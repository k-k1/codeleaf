package com.k1.gitreader

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Android ランタイム(ART)上で JGit が DESIGN.md の git 処理フロー通りに動くかを確認する
 * スモークテスト。pure-JVM PoC (poc/jgit-jvm/JgitPoc.java) の Kotlin 版。
 *
 * 公開リポ(octocat/Hello-World)を対象に:
 *   clone -> fetch -> ブランチ直近順 -> ローカル変更 -> reset --hard + clean -fdx -> 検証
 *
 * IO スレッドから呼ぶこと（呼び出し側で Dispatchers.IO）。
 */
object JgitSmokeTest {

    private const val URL = "https://github.com/octocat/Hello-World.git"
    private val FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun run(filesDir: File): String {
        val sb = StringBuilder()
        fun log(s: String) { sb.append(s).append('\n') }

        val work = File(filesDir, "repos/Hello-World")
        log("URL    : $URL")
        log("workdir: ${work.absolutePath}")

        val git = if (File(work, ".git").exists()) {
            log("\n--- open (既存clone再利用) ---")
            Git.open(work)
        } else {
            log("\n--- clone ---")
            Git.cloneRepository().setURI(URL).setDirectory(work).call()
        }

        git.use {
            val repo = git.repository

            log("\n--- fetch (+refs/heads/*) ---")
            git.fetch()
                .setRemote("origin")
                .setRefSpecs(RefSpec("+refs/heads/*:refs/remotes/origin/*"))
                .setRemoveDeletedRefs(true)
                .call()

            log("\n--- ブランチ一覧 (直近コミット順) ---")
            val branches = listBranchesByRecency(repo)
            for (b in branches) {
                log("   %-22s %s  %.7s".format(b.name, FMT.format(b.whenAt), b.sha))
            }

            val branch = branches.firstOrNull { it.name == repo.branch }?.name
                ?: branches.firstOrNull()?.name
                ?: repo.branch
            log("対象ブランチ: $branch")

            log("\n--- checkout $branch ---")
            val localExists = repo.findRef("refs/heads/$branch") != null
            git.checkout()
                .setName(branch)
                .setForced(true)
                .setCreateBranch(!localExists)
                .apply { if (!localExists) setStartPoint("origin/$branch") }
                .call()

            log("\n--- ローカル変更を作成 (破棄テスト用) ---")
            val trackedRel = firstTrackedFile(repo)
            val trackedAbs = File(work, trackedRel)
            val original = trackedAbs.readBytes()
            trackedAbs.appendText("\n!!! LOCAL EDIT THAT MUST BE DISCARDED !!!\n")
            val untracked = File(work, "POC_UNTRACKED.tmp")
            untracked.writeText("junk")
            log("   改変(追跡)  : $trackedRel")
            log("   作成(未追跡): POC_UNTRACKED.tmp")
            log("   status.isClean = ${git.status().call().isClean} (false 期待)")

            log("\n--- 破棄: reset --hard origin/$branch + clean -fdx ---")
            git.reset().setMode(ResetType.HARD).setRef("origin/$branch").call()
            git.clean().setCleanDirectories(true).setForce(true).setIgnore(false).call()

            log("\n--- 検証 ---")
            val trackedRestored = original.contentEquals(trackedAbs.readBytes())
            val untrackedRemoved = !untracked.exists()
            val clean = git.status().call().isClean
            log("   追跡ファイル復元   : ${ok(trackedRestored)}")
            log("   未追跡ファイル削除 : ${ok(untrackedRemoved)}")
            log("   status.isClean     : ${ok(clean)}")

            RevWalk(repo).use { rw ->
                val head = rw.parseCommit(repo.resolve("HEAD"))
                log("   HEAD = %.7s  %s".format(head.name, head.shortMessage))
            }

            val pass = trackedRestored && untrackedRemoved && clean
            log("\n==============================")
            log(if (pass) "RESULT: PASS (ARTでJGit動作OK)" else "RESULT: FAIL")
            log("==============================")
        }
        return sb.toString()
    }

    private data class BranchInfo(val name: String, val sha: String, val whenAt: Instant)

    private fun listBranchesByRecency(repo: Repository): List<BranchInfo> {
        val out = ArrayList<BranchInfo>()
        RevWalk(repo).use { rw ->
            for (ref in repo.refDatabase.getRefsByPrefix("refs/remotes/origin/")) {
                val name = ref.name.removePrefix("refs/remotes/origin/")
                if (name == "HEAD") continue
                val c = rw.parseCommit(ref.objectId)
                out.add(BranchInfo(name, c.name, c.committerIdent.whenAsInstant))
            }
        }
        return out.sortedByDescending { it.whenAt }
    }

    private fun firstTrackedFile(repo: Repository): String {
        RevWalk(repo).use { rw ->
            val head = rw.parseCommit(repo.resolve("HEAD"))
            TreeWalk(repo).use { tw ->
                tw.addTree(head.tree)
                tw.isRecursive = true
                if (tw.next()) return tw.pathString
            }
        }
        error("追跡ファイルが見つかりません")
    }

    private fun ok(b: Boolean) = if (b) "OK" else "NG"
}
