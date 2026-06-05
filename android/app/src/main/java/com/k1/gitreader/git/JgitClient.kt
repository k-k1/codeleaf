package com.k1.gitreader.git

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
data class CommitInfo(
    val sha: String,
    val shortMessage: String,
    val author: String,
    val committedAt: Instant,
)

/** コミットグラフ1ノード。parents は親コミットの sha（マージは複数）、refs は指しているブランチ/タグ名。 */
data class GraphCommit(
    val sha: String,
    val parents: List<String>,
    val shortMessage: String,
    val fullMessage: String,
    val author: String,
    val committedAt: Instant,
    val refs: List<String>,
)

/**
 * JGit を薄くラップした git クライアント。全メソッドはブロッキング I/O のため、
 * 呼び出し側 (RepoRepository) で Dispatchers.IO に載せること。
 *
 * 読み取り専用リーダーのための最小機能のみ:
 *   clone / sync(fetch+reset --hard+clean) / listBranches。
 * add/commit/push は提供しない。
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

            // sha -> その位置を指す ref 短縮名
            val refNames = HashMap<String, MutableList<String>>()
            for (ref in allRefs) {
                val id = repo.refDatabase.peel(ref).peeledObjectId ?: ref.objectId ?: continue
                refNames.getOrPut(id.name) { ArrayList() }.add(Repository.shortenRefName(ref.name))
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
                            refs = refNames[c.name].orEmpty().sorted(),
                        ),
                    )
                }
                return out
            }
        }
    }

    /**
     * submodule を init + update（取得）。public submodule のみ想定の最小対応（1階層）。
     * 読み取り専用リーダーのため、submodule の取得失敗は致命にせず無視する
     * （親リポは利用可能なまま、当該 submodule ディレクトリが空になるだけ）。
     */
    private fun updateSubmodules(git: Git, cp: CredentialsProvider?) {
        runCatching {
            val inited = git.submoduleInit().call()
            if (inited.isNotEmpty()) {
                git.submoduleUpdate().setCredentialsProvider(cp).call()
            }
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
