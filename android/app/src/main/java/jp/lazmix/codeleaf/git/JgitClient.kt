package jp.lazmix.codeleaf.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

private const val SUBMODULE_TAG = "JgitSubmodule"
private const val SUBMODULE_RETRIES = 3

/**
 * JGit を薄くラップした git クライアント。全メソッドはブロッキング I/O のため、
 * 呼び出し側 (RepoRepository) で Dispatchers.IO に載せること。
 *
 * 読み取り専用リーダーのための最小機能のみ:
 *   clone / sync(fetch+reset --hard+clean) / listBranches。
 * add/commit/push は提供しない。
 *
 * 戻り値の DTO(BranchInfo/CommitInfo/GraphCommit)と ref 名・URL 変換の純粋関数は GitModels.kt。
 */
class JgitClient {

    private val allBranchesRefSpec = RefSpec("+refs/heads/*:refs/remotes/origin/*")

    fun credentials(username: String?, token: String?): CredentialsProvider? =
        if (token.isNullOrBlank()) {
            null
        } else {
            // GitHub は空ユーザー名だと 401 になるため、未指定時は慣例の x-access-token を使う。
            val user = username?.takeIf { it.isNotBlank() } ?: "x-access-token"
            UsernamePasswordCredentialsProvider(user, token)
        }

    /** clone して全ブランチ + submodule を取得。 */
    fun clone(url: String, dir: File, cp: CredentialsProvider?) {
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(dir)
            .setCredentialsProvider(cp)
            .call()
            .use { git ->
                fetchAll(git, cp)
                updateSubmodules(git, cp)
            }
    }

    /**
     * 指定ブランチを最新化し、ローカル変更を完全破棄する。
     * fetch -> checkout -> reset --hard origin/<branch> -> clean -fdx -> submodule update
     */
    fun sync(dir: File, branch: String, cp: CredentialsProvider?) {
        Git.open(dir).use { git ->
            fetchAll(git, cp)
            checkoutForced(git, branch)
            git.reset().setMode(ResetType.HARD).setRef("origin/$branch").call()
            git.clean().setCleanDirectories(true).setForce(true).setIgnore(false).call()
            updateSubmodules(git, cp)
        }
    }

    /**
     * index 上の submodule の相対パス（'/'区切り）集合を返す。submodule 無し/エラー時は空。
     * ファイル一覧で submodule ディレクトリを判別するために使う。
     */
    fun submodulePaths(dir: File): Set<String> = runCatching {
        Git.open(dir).use { git ->
            val paths = HashSet<String>()
            SubmoduleWalk.forIndex(git.repository).use { walk ->
                while (walk.next()) paths.add(walk.path)
            }
            paths
        }
    }.getOrDefault(emptySet())

    /** リモートブランチを直近コミット順（降順）で返す。 */
    fun listBranches(dir: File): List<BranchInfo> {
        Git.open(dir).use { git ->
            val repo = git.repository
            val out = ArrayList<BranchInfo>()
            RevWalk(repo).use { rw ->
                for (ref in repo.refDatabase.getRefsByPrefix("refs/remotes/origin/")) {
                    val name = ref.name.removePrefix("refs/remotes/origin/")
                    if (name == "HEAD") continue
                    val c = rw.parseCommit(ref.objectId)
                    out.add(BranchInfo(name, c.name, c.committerIdent.whenAsInstant))
                }
            }
            return out.sortedByDescending { it.committedAt }
        }
    }

    /** clone 直後の既定ブランチ名（HEAD）。 */
    fun currentBranch(dir: File): String =
        Git.open(dir).use { it.repository.branch }

    /** HEAD が指すコミット sha。最終コミット情報キャッシュの無効化キーに使う（同期で変わる）。 */
    fun headSha(dir: File): String? =
        Git.open(dir).use { it.repository.resolve("HEAD")?.name }

    /** HEAD から見たコミット履歴。filePath 指定時はそのファイルに触れたコミットのみ。 */
    fun log(dir: File, filePath: String?, limit: Int): List<CommitInfo> {
        Git.open(dir).use { git ->
            val cmd = git.log().setMaxCount(limit)
            if (filePath != null) cmd.addPath(filePath)
            return cmd.call().map { c ->
                CommitInfo(c.name, c.shortMessage, c.authorIdent.name, c.authorIdent.whenAsInstant)
            }
        }
    }

    /**
     * [relDir]（リポルートからの相対・'/'区切り・ルートは ""）直下に並ぶ各 [paths]
     *（[FileEntry][jp.lazmix.codeleaf.data.FileEntry] の relPath。ファイルは完全パス・フォルダはプレフィックス）
     * について、それを最後に変更したコミット（著者・著者日時）を返す。一覧の「いつ・誰」表示用。
     *
     * HEAD から first-parent を新しい順にたどり、各コミットと第1親の relDir 配下差分を見て、
     * 未解決の対象に当たれば確定する。全対象が解決するか [maxCommits] 件 walk したら打ち切る
     *（古くしか触れられていない単独ファイルが履歴全走を招くのを防ぐ）。リネーム追従は行わない。
     * 解決できなかった対象はマップに含めない（呼び出し側で「—」表示）。
     */
    fun lastCommits(dir: File, relDir: String, paths: List<String>, maxCommits: Int): Map<String, EntryCommit> {
        if (paths.isEmpty()) return emptyMap()
        Git.open(dir).use { git ->
            val repo = git.repository
            val head = repo.resolve("HEAD") ?: return emptyMap()
            val unresolved = HashSet(paths)
            val result = HashMap<String, EntryCommit>()
            RevWalk(repo).use { rw ->
                rw.setFirstParent(true)
                rw.sort(RevSort.COMMIT_TIME_DESC)
                rw.markStart(rw.parseCommit(head))
                val reader = repo.newObjectReader()
                val parents = RevWalk(repo) // 親ツリー解決用(iterating walk を乱さない)
                val df = DiffFormatter(DisabledOutputStream.INSTANCE)
                df.setRepository(repo)
                df.isDetectRenames = false
                if (relDir.isNotEmpty()) df.pathFilter = PathFilter.create(relDir)
                try {
                    var count = 0
                    for (commit in rw) {
                        if (unresolved.isEmpty() || count >= maxCommits) break
                        count++
                        val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                        val oldTree = if (commit.parentCount > 0) {
                            val parent = parents.parseCommit(commit.getParent(0).id)
                            CanonicalTreeParser().apply { reset(reader, parent.tree) }
                        } else {
                            EmptyTreeIterator()
                        }
                        val diffs = df.scan(oldTree, newTree)
                        if (diffs.isEmpty()) continue
                        val changed = diffs.map { if (it.newPath != DiffEntry.DEV_NULL) it.newPath else it.oldPath }
                        val ec = EntryCommit(commit.authorIdent.name, commit.authorIdent.whenAsInstant)
                        val hit = unresolved.filter { t -> changed.any { matchesPathPrefix(it, t) } }
                        for (t in hit) {
                            result[t] = ec
                            unresolved.remove(t)
                        }
                    }
                } finally {
                    df.close()
                    parents.close()
                    reader.close()
                }
            }
            return result
        }
    }

    /**
     * 指定コミット(sha)の第1親との unified diff を返す共通処理。
     * [pathFilter] が非 null なら当該パスだけ、null なら全ファイルを対象にする。
     */
    private fun formatDiff(dir: File, sha: String, pathFilter: String?): String {
        Git.open(dir).use { git ->
            val repo = git.repository
            RevWalk(repo).use { rw ->
                val commit = rw.parseCommit(ObjectId.fromString(sha))
                repo.newObjectReader().use { reader ->
                    val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                    val oldIter = if (commit.parentCount > 0) {
                        val parent = rw.parseCommit(commit.getParent(0).id)
                        CanonicalTreeParser().apply { reset(reader, parent.tree) }
                    } else {
                        EmptyTreeIterator()
                    }
                    val out = ByteArrayOutputStream()
                    DiffFormatter(out).use { df ->
                        df.setRepository(repo)
                        if (pathFilter != null) df.pathFilter = PathFilter.create(pathFilter)
                        df.format(df.scan(oldIter, newTree))
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    /** 指定コミットにおける filePath の unified diff（第1親との差分）。 */
    fun diff(dir: File, filePath: String, sha: String): String = formatDiff(dir, sha, filePath)

    /**
     * 指定コミット(sha)時点の relPath の blob バイト列を返す。
     * sha のツリーに無ければ第1親(sha^)を試す（そのコミットで削除されたファイルは前版を表示するため）。
     * どちらにも無ければ null。
     */
    fun readBytesAt(dir: File, relPath: String, sha: String): ByteArray? {
        Git.open(dir).use { git ->
            val repo = git.repository
            RevWalk(repo).use { rw ->
                val commit = rw.parseCommit(ObjectId.fromString(sha))
                repo.newObjectReader().use { reader ->
                    val trees = buildList {
                        add(commit.tree)
                        if (commit.parentCount > 0) add(rw.parseCommit(commit.getParent(0).id).tree)
                    }
                    for (tree in trees) {
                        TreeWalk.forPath(reader, relPath, tree)?.use { tw ->
                            return reader.open(tw.getObjectId(0)).bytes
                        }
                    }
                }
            }
        }
        return null
    }

    /** 指定コミット全体の unified diff（第1親との差分・全ファイル）。 */
    fun commitDiff(dir: File, sha: String): String = formatDiff(dir, sha, pathFilter = null)

    /**
     * 全 ref(ローカル/リモートブランチ・タグ)を起点に DAG を辿り、コミットグラフを返す。
     * TOPO かつ committer date 降順。各コミットに紐づくブランチ/タグ名も付与する。
     */
    fun commitGraph(dir: File, limit: Int = 300): List<GraphCommit> {
        Git.open(dir).use { git ->
            val repo = git.repository
            val allRefs = repo.refDatabase.refs.filter { it.name != "HEAD" }

            // 現在チェックアウト中(HEAD)から到達可能なコミット = ローカル作業ツリーに反映済み。
            // それ以外(他ブランチ専用・未取り込み)は inCurrentBranch=false にして UI でグレー表示する。
            // 解決できない場合(detached 等)は空のままにし、後段で全コミットを反映済み扱いにする。
            val reachableFromHead = HashSet<String>()
            runCatching {
                repo.resolve("HEAD")?.let { head ->
                    RevWalk(repo).use { hw ->
                        hw.markStart(hw.parseCommit(head))
                        for (c in hw) reachableFromHead.add(c.name)
                    }
                }
            }

            // sha -> その位置を指す ref 表示名。読み取りミラーなのでローカル(refs/heads)は出さず、
            // リモートブランチは origin/ を剥がし、タグはそのまま(graphRefDisplayName)。
            val refNames = HashMap<String, MutableList<String>>()
            // sha -> その位置を指すリモートブランチ名(切替メニュー用。タグは除く)。
            val branchNames = HashMap<String, MutableList<String>>()
            for (ref in allRefs) {
                val id = repo.refDatabase.peel(ref).peeledObjectId ?: ref.objectId ?: continue
                graphRefDisplayName(ref.name)?.let { refNames.getOrPut(id.name) { ArrayList() }.add(it) }
                remoteBranchDisplayName(ref.name)?.let { branchNames.getOrPut(id.name) { ArrayList() }.add(it) }
            }

            RevWalk(repo).use { rw ->
                rw.sort(RevSort.TOPO, true)
                rw.sort(RevSort.COMMIT_TIME_DESC, true)
                for (ref in allRefs) {
                    val id = repo.refDatabase.peel(ref).peeledObjectId ?: ref.objectId ?: continue
                    val commit = runCatching { rw.parseCommit(id) }.getOrNull() ?: continue
                    rw.markStart(commit)
                }
                val out = ArrayList<GraphCommit>()
                for (c in rw) {
                    if (out.size >= limit) break
                    out.add(
                        GraphCommit(
                            sha = c.name,
                            parents = c.parents.map { it.name },
                            shortMessage = c.shortMessage,
                            fullMessage = c.fullMessage,
                            author = c.authorIdent.name,
                            committedAt = c.committerIdent.whenAsInstant,
                            refs = refNames[c.name].orEmpty().distinct().sorted(),
                            branches = branchNames[c.name].orEmpty().distinct().sorted(),
                            // HEAD を解決できなかった時(集合が空)は全て反映済み扱い(全グレー化を回避)。
                            inCurrentBranch = reachableFromHead.isEmpty() || c.name in reachableFromHead,
                        ),
                    )
                }
                return out
            }
        }
    }

    /**
     * submodule を init + update（取得）。public/private submodule の HTTPS 取得を想定（1階層）。
     * submoduleInit が .gitmodules の URL を .git/config へ展開する（相対URLは親originから解決）。
     * その後、JGit が扱えない SSH URL（git@host:.. / ssh://..）を HTTPS に書き換えてから update する。
     * 認証は親リポと同じ [cp]（token）を使う。読み取り専用リーダーのため取得失敗は致命にしない。
     */
    private fun updateSubmodules(git: Git, cp: CredentialsProvider?) {
        try {
            val inited = git.submoduleInit().call()
            val cfg = git.repository.config
            val names = cfg.getSubsections("submodule")
            // SSH URL を HTTPS へ書き換える（JGit は SSH 未設定で SSH submodule を取得できないため）。
            var changed = false
            for (name in names) {
                val url = cfg.getString("submodule", name, "url") ?: continue
                val https = sshToHttps(url)
                if (https != url) {
                    cfg.setString("submodule", name, "url", https)
                    changed = true
                    android.util.Log.w(SUBMODULE_TAG, "rewrite submodule '$name': $url -> $https")
                }
            }
            if (changed) cfg.save()
            android.util.Log.w(SUBMODULE_TAG, "init=${inited.size} submodules=$names")
            // submodule ごとに update する(1個の失敗が他を巻き込まないよう個別に)。
            // 一時的なネットワーク失敗に備え数回リトライ(バックオフ)。失敗は致命にせず残す。
            for (name in names) {
                val path = cfg.getString("submodule", name, "path") ?: name
                var ok = false
                var lastErr: Throwable? = null
                for (attempt in 1..SUBMODULE_RETRIES) {
                    try {
                        git.submoduleUpdate().addPath(path).setCredentialsProvider(cp).call()
                        ok = true
                        break
                    } catch (e: Exception) {
                        lastErr = e
                        android.util.Log.w(SUBMODULE_TAG, "update '$path' attempt $attempt/$SUBMODULE_RETRIES failed: ${e.message}")
                        if (attempt < SUBMODULE_RETRIES) runCatching { Thread.sleep(400L * attempt) }
                    }
                }
                if (ok) android.util.Log.w(SUBMODULE_TAG, "submodule '$path' updated")
                else android.util.Log.w(SUBMODULE_TAG, "submodule '$path' gave up after $SUBMODULE_RETRIES tries", lastErr)
            }
        } catch (e: Exception) {
            // init 等の段階失敗は親リポを使えるよう致命にせず、原因究明のためログには残す。
            android.util.Log.w(SUBMODULE_TAG, "submodule phase failed: ${e.message}", e)
        }
    }

    private fun fetchAll(git: Git, cp: CredentialsProvider?) {
        git.fetch()
            .setRemote("origin")
            .setRefSpecs(allBranchesRefSpec)
            .setRemoveDeletedRefs(true)
            .setCredentialsProvider(cp)
            .call()
    }

    private fun checkoutForced(git: Git, branch: String) {
        val repo: Repository = git.repository
        val localExists = repo.findRef("refs/heads/$branch") != null
        git.checkout()
            .setName(branch)
            .setForced(true)
            .setCreateBranch(!localExists)
            .apply { if (!localExists) setStartPoint("origin/$branch") }
            .call()
    }
}
