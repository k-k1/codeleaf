package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.crypto.TokenStore
import jp.lazmix.codeleaf.data.db.AuthType
import jp.lazmix.codeleaf.data.db.CloneState
import jp.lazmix.codeleaf.data.db.GitHost
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.RepoColor
import jp.lazmix.codeleaf.data.db.RepoDao
import jp.lazmix.codeleaf.data.db.ThemeMode
import jp.lazmix.codeleaf.data.oauth.BitbucketApi
import jp.lazmix.codeleaf.data.oauth.GitHubApi
import jp.lazmix.codeleaf.data.oauth.OAuthAccount
import jp.lazmix.codeleaf.data.oauth.RemoteRepo
import jp.lazmix.codeleaf.data.oauth.gitUsernameFor
import jp.lazmix.codeleaf.data.oauth.needsRefresh
import jp.lazmix.codeleaf.data.oauth.normalizeRepoUrl
import jp.lazmix.codeleaf.git.BranchInfo
import jp.lazmix.codeleaf.git.CommitInfo
import android.graphics.BitmapFactory
import jp.lazmix.codeleaf.git.GraphCommit
import jp.lazmix.codeleaf.git.JgitClient
import org.eclipse.jgit.transport.CredentialsProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/** ファイルブラウザ1エントリ。relPath はリポジトリルートからの相対パス（'/'区切り）。 */
data class FileEntry(
    val name: String,
    val relPath: String,
    val isDir: Boolean,
    /** git submodule のルートディレクトリ。 */
    val isSubmodule: Boolean = false,
    /** submodule だが中身が未取得（取得失敗等で空）。再同期で取得を促す表示に使う。 */
    val submoduleUnfetched: Boolean = false,
    /** Git LFS のポインタファイル（実体は未取得・Viewer では開かない）。 */
    val isLfs: Boolean = false,
    /** 一覧表示名。単一子フォルダ連鎖を畳むと "src/main/java" のような連結になる（既定は name）。 */
    val displayName: String = name,
)

/** LFS ポインタファイルの先頭シグネチャ。 */
private const val LFS_POINTER_MAGIC = "version https://git-lfs.github.com/spec/v1"

/** 先頭テキストが Git LFS ポインタかを判定する純粋関数（テスト用）。 */
fun isLfsPointerHead(head: String): Boolean = head.startsWith(LFS_POINTER_MAGIC)

/** buf が満ちるか EOF まで読み、実際に読めたバイト数を返す。 */
private fun InputStream.readFully(buf: ByteArray): Int {
    var off = 0
    while (off < buf.size) {
        val r = read(buf, off, buf.size - off)
        if (r < 0) break
        off += r
    }
    return off
}

/** 先頭の UTF-8 BOM(U+FEFF)を除去する。 */
private fun String.stripBom(): String =
    if (isNotEmpty() && this[0] == '﻿') substring(1) else this

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
    private val githubApi: GitHubApi = GitHubApi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeRepos(): Flow<List<Repo>> = dao.observeAll()

    // リポ毎に書き込み系 git 操作(sync)を直列化し、作業ツリー/index の競合を防ぐ。
    private val syncLocks = ConcurrentHashMap<Long, Mutex>()
    private fun syncLock(id: Long): Mutex = syncLocks.getOrPut(id) { Mutex() }

    fun workDir(repo: Repo): File = File(reposRoot, repo.id.toString())

    /** 作業ツリーに relPath の実体(ファイル/フォルダ)が存在するか(お気に入りの生存判定)。 */
    suspend fun exists(repo: Repo, relPath: String): Boolean = withContext(ioDispatcher) {
        relPath.isNotEmpty() && File(workDir(repo), relPath).exists()
    }

    /**
     * 行＋暗号化トークンだけ先に登録し（cloneState=CLONING・clone はまだ）即 [Repo] を返す。
     * 一覧はこの時点で「clone 中」パネルとして当該リポを表示できる。実 clone は [cloneRegistered]。
     */
    suspend fun register(input: NewRepo): Repo = withContext(ioDispatcher) {
        val id = dao.insert(
            Repo(
                name = input.name.ifBlank { input.url.substringAfterLast('/').removeSuffix(".git") },
                url = input.url,
                host = input.host,
                username = input.username,
                branch = input.branch.orEmpty(),
                themeMode = input.themeMode,
                authType = input.authType,
                cloneState = CloneState.CLONING,
            ),
        )
        when (input.authType) {
            AuthType.TOKEN -> tokenStore.setToken(id, input.token)
            AuthType.OAUTH -> tokenStore.setOAuth(id, input.oauth!!.toJson())
        }
        dao.getById(id)!!
    }

    /**
     * [register] 済みリポを clone → 既定ブランチ確定 → READY。失敗時は作業ツリーだけ掃除して
     * 行・トークンは残し FAILED にする（再試行で同じ行を使い回せる）。再試行も同関数。
     */
    suspend fun cloneRegistered(repo: Repo): Repo = withContext(ioDispatcher) {
        val dir = workDir(repo)
        try {
            dir.deleteRecursively() // 再試行時の残骸を掃除してから clone
            val cp = credentialsFor(repo)
            jgit.clone(repo.url, dir, cp)
            val branch = repo.branch.takeIf { it.isNotBlank() } ?: jgit.currentBranch(dir)
            val saved = dao.getById(repo.id)!!
                .copy(branch = branch, lastSyncedAt = nowMillis(), cloneState = CloneState.READY)
            dao.update(saved)
            saved
        } catch (t: Throwable) {
            dir.deleteRecursively()
            dao.getById(repo.id)?.let { dao.update(it.copy(cloneState = CloneState.FAILED)) }
            throw t
        }
    }

    /** 登録 → clone（[register]＋[cloneRegistered] の合成）。 */
    suspend fun addAndClone(input: NewRepo): Repo = cloneRegistered(register(input))

    /** プロセス死で中断した clone(CLONING 残留)を FAILED へ倒す（起動時に呼ぶ）。 */
    suspend fun failInterruptedClones() = withContext(ioDispatcher) { dao.failInterruptedClones() }

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

    /** 成功したログインを provider 単位で記憶する（次回のリポ追加で再ログインを省く）。 */
    suspend fun rememberOAuthSession(account: OAuthAccount) = withContext(ioDispatcher) {
        tokenStore.setOAuthSession(account.provider, account.toJson())
    }

    /**
     * 記憶済みログインを返す。失効間近なら refresh して保存し直す。
     * refresh 失効（再ログインが要る）や未記憶なら null（UI はログインボタンを出す）。
     */
    suspend fun rememberedOAuthSession(provider: String): OAuthAccount? = withContext(ioDispatcher) {
        val json = tokenStore.getOAuthSession(provider) ?: return@withContext null
        val account = OAuthAccount.fromJson(json)
        if (!needsRefresh(account.expiresAtEpochMs, nowMillis())) return@withContext account
        val refresher = refreshOAuth ?: return@withContext account
        val refreshed = refresher(account).getOrNull() ?: return@withContext null
        tokenStore.setOAuthSession(provider, refreshed.toJson())
        refreshed
    }

    /** OAuth でアクセス可能な Bitbucket リポのうち、未登録(=clone 済みでない)ものを返す。 */
    suspend fun listClonableBitbucketRepos(account: OAuthAccount): List<RemoteRepo> =
        withContext(ioDispatcher) {
            val remote = bitbucketApi.listRepositories(account.accessToken).getOrThrow()
            unregistered(remote)
        }

    /** OAuth でアクセス可能な GitHub リポのうち、未登録(=clone 済みでない)ものを返す。 */
    suspend fun listClonableGitHubRepos(account: OAuthAccount): List<RemoteRepo> =
        withContext(ioDispatcher) {
            val remote = githubApi.listRepositories(account.accessToken).getOrThrow()
            unregistered(remote)
        }

    /** リモートリポ一覧から、URL 正規化で既存登録と重複するものを除く。 */
    private suspend fun unregistered(remote: List<RemoteRepo>): List<RemoteRepo> {
        val cloned = observeRepos().first().map { normalizeRepoUrl(it.url) }.toSet()
        return remote.filter { normalizeRepoUrl(it.cloneUrl) !in cloned }
    }

    suspend fun listBranches(repo: Repo): List<BranchInfo> = withContext(ioDispatcher) {
        jgit.listBranches(workDir(repo))
    }

    /** 作業ツリー内の relPath 配下を列挙（.git 除外・フォルダ優先→名前順）。
     *  submodule のルートと LFS ポインタを判別フラグ付きで返す。 */
    suspend fun listDir(repo: Repo, relPath: String, collapse: Boolean = true): List<FileEntry> =
        withContext(ioDispatcher) {
            val root = workDir(repo)
            val dir = if (relPath.isEmpty()) root else File(root, relPath)
            // .gitmodules があるリポだけ index を読んで submodule パス集合を得る。
            val subPaths = if (File(root, ".gitmodules").exists()) jgit.submodulePaths(root) else emptySet()
            val children = dir.listFiles().orEmpty().filterNot { it.name == ".git" }
            val sorted = children
                .map { f ->
                    val rel = joinRel(relPath, f.name)
                    val isSub = f.isDirectory && rel in subPaths
                    FileEntry(
                        name = f.name,
                        relPath = rel,
                        isDir = f.isDirectory,
                        isSubmodule = isSub,
                        // submodule なのに中身が空(.git のみ/空)＝未取得。取得失敗を一覧で気づけるようにする。
                        submoduleUnfetched = isSub && f.listFiles().orEmpty().none { it.name != ".git" },
                        isLfs = !f.isDirectory && isLfsPointer(f),
                    )
                }
                .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
            if (!collapse) return@withContext sorted
            // 単一子フォルダ連鎖(src/main/java 等)を1エントリに畳む。タップで最深へ直行。
            sorted.map { e ->
                if (e.isDir && !e.isSubmodule) {
                    val (display, target) = collapseDirChain(root, e.relPath, subPaths)
                    if (target != e.relPath) {
                        e.copy(name = target.substringAfterLast('/'), relPath = target, displayName = display)
                    } else {
                        e
                    }
                } else {
                    e
                }
            }
        }

    /**
     * フォルダ [startRel] が「中身が単一のサブフォルダだけ」である限り降り、連結表示名と最深パスを返す。
     * submodule は越えない。深さは安全のため上限を設ける。
     */
    private fun collapseDirChain(root: File, startRel: String, subPaths: Set<String>): Pair<String, String> {
        val names = ArrayList<String>()
        names.add(startRel.substringAfterLast('/'))
        var rel = startRel
        var guard = 0
        while (guard++ < 24) {
            val kids = File(root, rel).listFiles().orEmpty().filterNot { it.name == ".git" }
            if (kids.size != 1) break
            val only = kids[0]
            if (!only.isDirectory) break
            val childRel = joinRel(rel, only.name)
            if (childRel in subPaths) break // submodule の中へは畳まない
            names.add(only.name)
            rel = childRel
        }
        return names.joinToString("/") to rel
    }

    /** 小さなファイルの先頭を読み、Git LFS ポインタかを判定する。 */
    private fun isLfsPointer(f: File): Boolean {
        val len = f.length()
        if (len < 50L || len > 1024L) return false // LFS ポインタは概ね 120〜200B の小さなテキスト
        val text = runCatching { f.readBytes().decodeToString() }.getOrNull() ?: return false
        return isLfsPointerHead(text)
    }

    /**
     * テキストファイルを指定 [charset](既定 UTF-8)で読み込む。先頭 BOM は除去する。
     * [maxBytes] を超えるファイルは先頭だけ読み、[TextLoad.truncated] を立てる
     * (巨大ファイルでの OOM/フリーズ回避)。
     */
    suspend fun readText(
        repo: Repo,
        relPath: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = Long.MAX_VALUE,
    ): TextLoad = withContext(ioDispatcher) {
        val f = File(workDir(repo), relPath)
        val size = f.length()
        val cap = minOf(size, maxBytes, Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(0)
        val buf = ByteArray(cap)
        val n = f.inputStream().use { it.readFully(buf) }
        TextLoad(String(buf, 0, n, charset).stripBom(), truncated = size > n.toLong())
    }

    /**
     * ファイルの先頭を読んで種別(テキスト/画像/バイナリ)とサイズを判定する。
     * 全読みせず先頭 [FileClassifier.PROBE_BYTES] だけ読むので巨大バイナリでも軽い。
     * テキストはエンコード/改行コードを、ラスタ画像は寸法を併せて返す。
     */
    suspend fun probeFile(repo: Repo, relPath: String): FileInfo = withContext(ioDispatcher) {
        val f = File(workDir(repo), relPath)
        val size = f.length()
        val buf = ByteArray(FileClassifier.PROBE_BYTES)
        val n = f.inputStream().use { it.readFully(buf) }
        val head = buf.copyOf(n)
        val kind = FileClassifier.classify(relPath.substringAfterLast('/'), head)
        val textMeta = if (kind is FileKind.Text) FileClassifier.textMeta(head) else null
        var w: Int? = null
        var h: Int? = null
        if (kind is FileKind.Image && kind.format != "svg") {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opt)
            if (opt.outWidth > 0 && opt.outHeight > 0) {
                w = opt.outWidth
                h = opt.outHeight
            }
        }
        FileInfo(kind, size, head, textMeta, w, h)
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

    /**
     * 指定コミット(sha)時点の relPath の種別を判定する(履歴表示用)。
     * 作業ツリーに無いファイルを Viewer で見せるとき用。実ファイルが無いので
     * 画像/PDF はプレビューせず概要カード(Binary)に寄せる。blob が無ければ null。
     */
    suspend fun probeBlob(repo: Repo, relPath: String, sha: String): FileInfo? = withContext(ioDispatcher) {
        val bytes = jgit.readBytesAt(workDir(repo), relPath, sha) ?: return@withContext null
        val size = bytes.size.toLong()
        val head = if (bytes.size > FileClassifier.PROBE_BYTES) bytes.copyOf(FileClassifier.PROBE_BYTES) else bytes
        var kind = FileClassifier.classify(relPath.substringAfterLast('/'), head)
        if (kind is FileKind.Image) kind = FileKind.Binary(kind.format.uppercase())
        if (kind is FileKind.Pdf) kind = FileKind.Binary("PDF")
        val textMeta = if (kind is FileKind.Text) FileClassifier.textMeta(head) else null
        FileInfo(kind, size, head, textMeta)
    }

    /** 指定コミット(sha)時点の relPath のテキスト本文。blob が無ければ null。先頭 BOM は除去。 */
    suspend fun readBlobText(
        repo: Repo,
        relPath: String,
        sha: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = Long.MAX_VALUE,
    ): TextLoad? = withContext(ioDispatcher) {
        val bytes = jgit.readBytesAt(workDir(repo), relPath, sha) ?: return@withContext null
        val total = bytes.size.toLong()
        val cap = minOf(total, maxBytes, Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(0)
        TextLoad(String(bytes, 0, cap, charset).stripBom(), truncated = total > cap.toLong())
    }

    /** リポジトリの表示テーマを変更して保存する。更新後の Repo を返す。 */
    suspend fun setTheme(repo: Repo, mode: ThemeMode): Repo = withContext(ioDispatcher) {
        val updated = repo.copy(themeMode = mode)
        dao.update(updated)
        updated
    }

    /** カード色(プリセット)を変更して保存する。更新後の [Repo] を返す。 */
    suspend fun setColor(repo: Repo, color: RepoColor): Repo = withContext(ioDispatcher) {
        val updated = repo.copy(colorTag = color)
        dao.update(updated)
        updated
    }

    /** 所属グループ(Working Set)を変更して保存する。空=未分類。 */
    suspend fun setGroup(repo: Repo, group: String) = withContext(ioDispatcher) {
        dao.update(repo.copy(groupName = group.trim()))
    }

    /**
     * 編集画面のセクション D&D 結果を保存する。`ordered` は表示順で、各 Repo の groupName は
     * ドロップ先セクションに更新済み。所属(groupName)と並び順(sortOrder=index)をまとめて書き込む。
     */
    suspend fun saveGroupsAndOrder(ordered: List<Repo>) = withContext(ioDispatcher) {
        ordered.forEachIndexed { index, repo ->
            val cur = dao.getById(repo.id) ?: return@forEachIndexed
            if (cur.groupName != repo.groupName || cur.sortOrder != index) {
                dao.update(cur.copy(groupName = repo.groupName, sortOrder = index))
            }
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

    /** 登録済みリポジトリ・暗号化トークン・作業ツリー・記憶ログインをすべて削除する(キャッシュ全削除)。 */
    suspend fun deleteAll() = withContext(ioDispatcher) {
        observeRepos().first().forEach { delete(it) }
        listOf("GITHUB", "BITBUCKET").forEach { tokenStore.removeOAuthSession(it) }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private companion object {
        const val MAX_SEARCH_FILE_BYTES = 1_000_000L
        const val MAX_CORPUS_BYTES = 8_000_000L
    }
}
