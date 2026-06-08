package jp.lazmix.codeleaf.git

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
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
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/** リモートブランチ1件分の情報（直近順表示・選択用）。 */
data class BranchInfo(
    val name: String,
    val sha: String,
    val committedAt: Instant,
)

/** コミット1件分の情報（履歴表示用）。 */
@Parcelize
@TypeParceler<Instant, InstantParceler>
data class CommitInfo(
    val sha: String,
    val shortMessage: String,
    val author: String,
    val committedAt: Instant,
) : Parcelable

/** Instant を epochMilli Long で Parcel に書き出す(プロセス死復元のため)。 */
object InstantParceler : Parceler<Instant> {
    override fun create(parcel: Parcel): Instant = Instant.ofEpochMilli(parcel.readLong())
    override fun Instant.write(parcel: Parcel, flags: Int) = parcel.writeLong(toEpochMilli())
}

/** コミットグラフ1ノード。parents は親コミットの sha（マージは複数）、refs は指しているブランチ/タグ名。 */
@Parcelize
@TypeParceler<Instant, InstantParceler>
data class GraphCommit(
    val sha: String,
    val parents: List<String>,
    val shortMessage: String,
    val fullMessage: String,
    val author: String,
    val committedAt: Instant,
    val refs: List<String>,
    /**
     * 現在チェックアウト中ブランチ(HEAD)から到達可能か。true=ローカル作業ツリーに反映済み。
     * false=他ブランチ専用/未取り込みで、UI ではグレー表示する。
     */
    val inCurrentBranch: Boolean = true,
) : Parcelable

/**
 * コミットグラフ chip 用の ref 表示名。読み取りミラーなので冗長さを避ける純粋関数:
 * - ローカルブランチ(refs/heads 配下) は出さない(null) … リモートと重複するため
 * - リモートブランチ(refs/remotes 配下) は remote 名を剥がす(origin/main → main)。origin/HEAD は除外
 * - タグ(refs/tags 配下) はそのまま
 * - それ以外(refs/stash 等) は出さない
 */
internal fun graphRefDisplayName(fullName: String): String? = when {
    fullName.startsWith("refs/heads/") -> null
    fullName.startsWith("refs/remotes/") ->
        fullName.removePrefix("refs/remotes/").substringAfter('/').takeIf { it != "HEAD" }
    fullName.startsWith("refs/tags/") -> fullName.removePrefix("refs/tags/")
    else -> null
}

/**
 * JGit を薄くラップした git クライアント。全メソッドはブロッキング I/O のため、
 * 呼び出し側 (RepoRepository) で Dispatchers.IO に載せること。
 *
 * 読み取り専用リーダーのための最小機能のみ:
 *   clone / sync(fetch+reset --hard+clean) / listBranches。
 * add/commit/push は提供しない。
 */
private const val SUBMODULE_TAG = "JgitSubmodule"
private const val SUBMODULE_RETRIES = 3

/**
 * SSH 形式の git URL を HTTPS に変換する（変換不要ならそのまま返す）。submodule 用。
 * 認証はトークン(HTTPS)で行うため、SSH のままだと JGit が取得できず submodule が空になる。
 * 対応: scp 形式 `user@host:owner/repo(.git)` と `ssh://[user@]host[:port]/owner/repo(.git)`。
 */
internal fun sshToHttps(url: String): String {
    val u = url.trim()
    // scp 形式: user@host:path（"http(s)://..." は '/' を含むため [^@/] に阻まれ誤マッチしない）。
    Regex("""^[^@/\s]+@([^:/\s]+):(.+)$""").matchEntire(u)?.let {
        return "https://${it.groupValues[1]}/${it.groupValues[2].removePrefix("/")}"
    }
    // ssh://[user@]host[:port]/path
    Regex("""^ssh://(?:[^@/\s]+@)?([^:/\s]+)(?::\d+)?/(.+)$""").matchEntire(u)?.let {
        return "https://${it.groupValues[1]}/${it.groupValues[2]}"
    }
    return u
}

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

    /** 指定コミットにおける filePath の unified diff（第1親との差分）。 */
    fun diff(dir: File, filePath: String, sha: String): String {
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
                        df.pathFilter = PathFilter.create(filePath)
                        df.format(df.scan(oldIter, newTree))
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    /** 指定コミット全体の unified diff（第1親との差分・全ファイル）。 */
    fun commitDiff(dir: File, sha: String): String {
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
                        df.format(df.scan(oldIter, newTree)) // pathFilter 無し = 全ファイル
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

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
            for (ref in allRefs) {
                val display = graphRefDisplayName(ref.name) ?: continue
                val id = repo.refDatabase.peel(ref).peeledObjectId ?: ref.objectId ?: continue
                refNames.getOrPut(id.name) { ArrayList() }.add(display)
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
