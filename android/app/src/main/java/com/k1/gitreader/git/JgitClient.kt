package com.k1.gitreader.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
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
        if (token.isNullOrBlank()) null
        else UsernamePasswordCredentialsProvider(username ?: "", token)

    /** clone して全ブランチを取得。既に存在する場合は何もしない。 */
    fun clone(url: String, dir: File, cp: CredentialsProvider?) {
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(dir)
            .setCredentialsProvider(cp)
            .call()
            .use { git ->
                fetchAll(git, cp)
            }
    }

    /**
     * 指定ブランチを最新化し、ローカル変更を完全破棄する。
     * fetch -> checkout -> reset --hard origin/<branch> -> clean -fdx
     */
    fun sync(dir: File, branch: String, cp: CredentialsProvider?) {
        Git.open(dir).use { git ->
            fetchAll(git, cp)
            checkoutForced(git, branch)
            git.reset().setMode(ResetType.HARD).setRef("origin/$branch").call()
            git.clean().setCleanDirectories(true).setForce(true).setIgnore(false).call()
        }
    }

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
