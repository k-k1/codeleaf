package com.k1.gitreader.data

import com.k1.gitreader.data.crypto.TokenStore
import com.k1.gitreader.data.db.AuthType
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.data.db.RepoColor
import com.k1.gitreader.data.db.RepoDao
import com.k1.gitreader.data.db.ThemeMode
import com.k1.gitreader.data.oauth.BitbucketApi
import com.k1.gitreader.data.oauth.OAuthAccount
import com.k1.gitreader.data.oauth.RemoteRepo
import com.k1.gitreader.data.oauth.gitUsernameFor
import com.k1.gitreader.data.oauth.needsRefresh
import com.k1.gitreader.data.oauth.normalizeRepoUrl
import com.k1.gitreader.git.BranchInfo
import com.k1.gitreader.git.CommitInfo
import com.k1.gitreader.git.GraphCommit
import com.k1.gitreader.git.JgitClient
import org.eclipse.jgit.transport.CredentialsProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** ファイルブラウザ1エントリ。relPath はリポジトリルートからの相対パス（'/'区切り）。 */
data class FileEntry(
    val name: String,
    val relPath: String,
    val isDir: Boolean,
    /** git submodule のルートディレクトリ。 */
    val isSubmodule: Boolean = false,
    /** Git LFS のポインタファイル（実体は未取得・Viewer では開かない）。 */
    val isLfs: Boolean = false,
)

/** LFS ポインタファイルの先頭シグネチャ。 */
private const val LFS_POINTER_MAGIC = "version https://git-lfs.github.com/spec/v1"

/** 先頭テキストが Git LFS ポインタかを判定する純粋関数（テスト用）。 */
fun isLfsPointerHead(head: String): Boolean = head.startsWith(LFS_POINTER_MAGIC)

/** 全文検索のヒット1件。relPath はリポルートからの相対パス、line は1始まりの行番号。 */
data class SearchHit(
    val relPath: String,
    val line: Int,
    val text: String,
)

/** 検索対象テキストファイル1件（インメモリ・インクリメンタル検索用のコーパス）。 */
data class TextFile(
    val relPath: String,
    val content: String,
)

/** 検索結果。error が非 null のとき（不正な正規表現など）は hits は空。 */
data class SearchOutcome(
    val hits: List<SearchHit>,
    val error: String? = null,
)

/**
 * メモリ上のコーパスを検索する純粋関数。クエリは空白区切りの複数語を AND 条件で扱い、
 * 行が全語を含む(順不同)ときヒットする。regex=true なら各語を正規表現(大文字小文字無視)、
 * false なら各語を大文字小文字無視の部分一致で判定する。不正な正規表現は error を返す。
 * pathFilter が非空なら relPath にそれを含むファイルだけを対象にする(拡張子/ディレクトリ絞り込み)。
 */
fun searchCorpus(
    corpus: List<TextFile>,
    query: String,
    regex: Boolean,
    pathFilter: String = "",
    maxHits: Int = 500,
): SearchOutcome {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return SearchOutcome(emptyList())
    val regexes = if (regex) {
        terms.map {
            runCatching { Regex(it, RegexOption.IGNORE_CASE) }
                .getOrElse { return SearchOutcome(emptyList(), "正規表現が不正です") }
        }
    } else {
        null
    }
    val pf = pathFilter.trim()
    val hits = ArrayList<SearchHit>()
    outer@ for (file in corpus) {
        if (pf.isNotEmpty() && !file.relPath.contains(pf, ignoreCase = true)) continue
        var lineNo = 0
        for (line in file.content.lineSequence()) {
            lineNo++
            val matched = if (regexes != null) {
                regexes.all { it.containsMatchIn(line) }
            } else {
                terms.all { line.contains(it, ignoreCase = true) }
            }
            if (matched) {
                hits.add(SearchHit(file.relPath, lineNo, line.trim().take(200)))
                if (hits.size >= maxHits) break@outer
            }
        }
    }
    return SearchOutcome(hits)
}

/** 新規リポジトリ登録フォームの入力値。authType=OAUTH のとき oauth が非 null。 */
data class NewRepo(
    val name: String,
    val url: String,
    val host: GitHost,
    val username: String,
    val token: String,
    val branch: String?,
    val themeMode: ThemeMode,
    val authType: AuthType = AuthType.TOKEN,
    val oauth: OAuthAccount? = null,
)

/**
 * UI と git/db/暗号化トークンを仲介する Repository 層。
 * 全 I/O は ioDispatcher 上で実行する。
 */
class RepoRepository(
    private val dao: RepoDao,
    private val tokenStore: TokenStore,
    private val jgit: JgitClient,
    private val reposRoot: File,
    /** OAuth access token 失効時の更新。未設定(手動トークンのみ運用)なら null。 */
    private val refreshOAuth: (suspend (OAuthAccount) -> Result<OAuthAccount>)? = null,
    private val bitbucketApi: BitbucketApi = BitbucketApi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeRepos(): Flow<List<Repo>> = dao.observeAll()

    // リポ毎に書き込み系 git 操作(sync)を直列化し、作業ツリー/index の競合を防ぐ。
    private val syncLocks = ConcurrentHashMap<Long, Mutex>()
    private fun syncLock(id: Long): Mutex = syncLocks.getOrPut(id) { Mutex() }

    fun workDir(repo: Repo): File = File(reposRoot, repo.id.toString())

    /** 登録 → clone → 既定ブランチ確定。失敗時は行と暗号化トークンを巻き戻す。 */
    suspend fun addAndClone(input: NewRepo): Repo = withContext(ioDispatcher) {
        val id = dao.insert(
            Repo(
                name = input.name.ifBlank { input.url.substringAfterLast('/').removeSuffix(".git") },
                url = input.url,
                host = input.host,
                username = input.username,
                branch = input.branch.orEmpty(),
                themeMode = input.themeMode,
                authType = input.authType,
            ),
        )
        when (input.authType) {
            AuthType.TOKEN -> tokenStore.setToken(id, input.token)
            AuthType.OAUTH -> tokenStore.setOAuth(id, input.oauth!!.toJson())
        }
        val dir = File(reposRoot, id.toString())
        try {
            val cp = credentialsFor(dao.getById(id)!!)
            jgit.clone(input.url, dir, cp)
            val branch = input.branch?.takeIf { it.isNotBlank() } ?: jgit.currentBranch(dir)
            val saved = dao.getById(id)!!.copy(branch = branch, lastSyncedAt = nowMillis())
            dao.update(saved)
            saved
        } catch (t: Throwable) {
            // ロールバック（両名前空間のトークンを掃除）
            dao.getById(id)?.let { dao.delete(it) }
            tokenStore.removeToken(id)
            tokenStore.removeOAuth(id)
            dir.deleteRecursively()
            throw t
        }
    }

    /** 指定ブランチで最新化（ローカル変更は破棄）。同一リポの同期は直列化される。 */
    suspend fun sync(repo: Repo, branch: String = repo.branch): Repo = withContext(ioDispatcher) {
        syncLock(repo.id).withLock {
            // OAuth の refresh も同一リポ Mutex 内で行い、並行 sync との競合を防ぐ。
            val cp = credentialsFor(repo)
            jgit.sync(workDir(repo), branch, cp)
            val saved = repo.copy(branch = branch, lastSyncedAt = nowMillis())
            dao.update(saved)
            saved
        }
    }

    /** 認証種別に応じた CredentialsProvider を構築。OAUTH は期限切れなら refresh して新トークンを使う。 */
    private suspend fun credentialsFor(repo: Repo): CredentialsProvider? = when (repo.authType) {
        AuthType.TOKEN -> jgit.credentials(repo.username, tokenStore.getToken(repo.id))
        AuthType.OAUTH -> {
            val json = tokenStore.getOAuth(repo.id) ?: error("OAuth 資格情報がありません")
            val account = ensureFresh(repo.id, OAuthAccount.fromJson(json))
            jgit.credentials(gitUsernameFor(repo.host, AuthType.OAUTH, repo.username), account.accessToken)
        }
    }

    /** access token が期限切れ間近なら refresh して保存し直す。refresh 失効は例外送出(再ログイン誘導)。 */
    private suspend fun ensureFresh(repoId: Long, account: OAuthAccount): OAuthAccount {
        if (!needsRefresh(account.expiresAtEpochMs, nowMillis())) return account
        val refresher = refreshOAuth ?: return account
        val refreshed = refresher(account).getOrThrow()
        tokenStore.setOAuth(repoId, refreshed.toJson())
        return refreshed
    }

    /** OAuth でアクセス可能な Bitbucket リポのうち、未登録(=clone 済みでない)ものを返す。 */
    suspend fun listClonableBitbucketRepos(account: OAuthAccount): List<RemoteRepo> =
        withContext(ioDispatcher) {
            val remote = bitbucketApi.listRepositories(account.accessToken).getOrThrow()
            val cloned = observeRepos().first().map { normalizeRepoUrl(it.url) }.toSet()
            remote.filter { normalizeRepoUrl(it.cloneUrl) !in cloned }
        }

    suspend fun listBranches(repo: Repo): List<BranchInfo> = withContext(ioDispatcher) {
        jgit.listBranches(workDir(repo))
    }

    /** 作業ツリー内の relPath 配下を列挙（.git 除外・フォルダ優先→名前順）。
     *  submodule のルートと LFS ポインタを判別フラグ付きで返す。 */
    suspend fun listDir(repo: Repo, relPath: String): List<FileEntry> = withContext(ioDispatcher) {
        val root = workDir(repo)
        val dir = if (relPath.isEmpty()) root else File(root, relPath)
        // .gitmodules があるリポだけ index を読んで submodule パス集合を得る。
        val subPaths = if (File(root, ".gitmodules").exists()) jgit.submodulePaths(root) else emptySet()
        val children = dir.listFiles().orEmpty().filterNot { it.name == ".git" }
        children
            .map { f ->
                val rel = joinRel(relPath, f.name)
                FileEntry(
                    name = f.name,
                    relPath = rel,
                    isDir = f.isDirectory,
                    isSubmodule = f.isDirectory && rel in subPaths,
                    isLfs = !f.isDirectory && isLfsPointer(f),
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** 小さなファイルの先頭を読み、Git LFS ポインタかを判定する。 */
    private fun isLfsPointer(f: File): Boolean {
        val len = f.length()
        if (len < 50L || len > 1024L) return false // LFS ポインタは概ね 120〜200B の小さなテキスト
        val text = runCatching { f.readBytes().decodeToString() }.getOrNull() ?: return false
        return isLfsPointerHead(text)
    }

    /** テキストファイルを UTF-8 で読み込む。 */
    suspend fun readText(repo: Repo, relPath: String): String = withContext(ioDispatcher) {
        File(workDir(repo), relPath).readText()
    }

    /**
     * 作業ツリーのテキストファイル本文をメモリに読み込む（インクリメンタル検索用コーパス）。
     * .git・巨大ファイル(>1MB)・バイナリ(NULを含む)は除外し、合計サイズ上限で打ち切る。
     * relPath 昇順で返す。
     */
    suspend fun loadSearchCorpus(repo: Repo): List<TextFile> = withContext(ioDispatcher) {
        val root = workDir(repo)
        val files = root.walkTopDown().onEnter { it.name != ".git" }.filter { it.isFile }
        val corpus = ArrayList<TextFile>()
        var total = 0L
        for (f in files) {
            if (total >= MAX_CORPUS_BYTES) break
            if (f.length() > MAX_SEARCH_FILE_BYTES) continue
            val data = runCatching { f.readBytes() }.getOrNull() ?: continue
            if (data.any { it == 0.toByte() }) continue // バイナリ判定
            total += data.size
            val rel = f.relativeTo(root).path.replace('\\', '/')
            corpus.add(TextFile(rel, String(data, Charsets.UTF_8)))
        }
        corpus.sortedBy { it.relPath }
    }

    /** ファイルのコミット履歴。 */
    suspend fun fileHistory(repo: Repo, relPath: String, limit: Int = 100): List<CommitInfo> =
        withContext(ioDispatcher) { jgit.log(workDir(repo), relPath, limit) }

    /** 指定コミットでのファイル unified diff。 */
    suspend fun fileDiff(repo: Repo, relPath: String, sha: String): String =
        withContext(ioDispatcher) { jgit.diff(workDir(repo), relPath, sha) }

    /** リポジトリ全体のコミットグラフ(全 ref から DAG)。 */
    suspend fun commitGraph(repo: Repo): List<GraphCommit> =
        withContext(ioDispatcher) { jgit.commitGraph(workDir(repo)) }

    /** 指定コミット全体の unified diff(第1親との全ファイル差分)。 */
    suspend fun commitDiff(repo: Repo, sha: String): String =
        withContext(ioDispatcher) { jgit.commitDiff(workDir(repo), sha) }

    /** リポジトリの表示テーマを変更して保存する。更新後の Repo を返す。 */
    suspend fun setTheme(repo: Repo, mode: ThemeMode): Repo = withContext(ioDispatcher) {
        val updated = repo.copy(themeMode = mode)
        dao.update(updated)
        updated
    }

    /** カード色(プリセット)を変更して保存する。 */
    suspend fun setColor(repo: Repo, color: RepoColor) = withContext(ioDispatcher) {
        dao.update(repo.copy(colorTag = color))
    }

    /** 並べ替え結果を保存する(リストの並び順を sortOrder=index で書き込む)。 */
    suspend fun saveOrder(orderedRepos: List<Repo>) = withContext(ioDispatcher) {
        orderedRepos.forEachIndexed { index, repo ->
            if (repo.sortOrder != index) dao.update(repo.copy(sortOrder = index))
        }
    }

    private fun joinRel(parent: String, child: String): String =
        if (parent.isEmpty()) child else "$parent/$child"

    suspend fun delete(repo: Repo) = withContext(ioDispatcher) {
        dao.delete(repo)
        tokenStore.removeToken(repo.id)
        tokenStore.removeOAuth(repo.id)
        workDir(repo).deleteRecursively()
    }

    /** 登録済みリポジトリ・暗号化トークン・作業ツリーをすべて削除する(キャッシュ全削除)。 */
    suspend fun deleteAll() = withContext(ioDispatcher) {
        observeRepos().first().forEach { delete(it) }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private companion object {
        const val MAX_SEARCH_FILE_BYTES = 1_000_000L
        const val MAX_CORPUS_BYTES = 8_000_000L
    }
}
