package jp.lazmix.codeleaf.git

import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * コミットグラフ長押し「ブランチ切替」の元データ検証(JGit は JVM 上でも動く・NW 非依存)。
 * - ref 表示名の純粋関数がブランチ/タグ/ローカルを正しく区別するか。
 * - clone 後の commitGraph で各コミットの branches(リモートブランチのみ・タグ除外)が埋まるか。
 */
class CommitGraphBranchesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun refDisplayNames_distinguishBranchesTagsAndLocal() {
        // チップ表示用: リモートブランチ(origin 剥がし)とタグは出す、ローカルと origin/HEAD は出さない。
        assertEquals("main", graphRefDisplayName("refs/remotes/origin/main"))
        assertEquals("v1.0", graphRefDisplayName("refs/tags/v1.0"))
        assertNull(graphRefDisplayName("refs/heads/main"))
        assertNull(graphRefDisplayName("refs/remotes/origin/HEAD"))

        // 切替メニュー用: リモートブランチだけ。タグ・ローカル・HEAD は対象外。
        assertEquals("main", remoteBranchDisplayName("refs/remotes/origin/main"))
        assertEquals("feature", remoteBranchDisplayName("refs/remotes/origin/feature"))
        assertNull(remoteBranchDisplayName("refs/tags/v1.0"))
        assertNull(remoteBranchDisplayName("refs/heads/main"))
        assertNull(remoteBranchDisplayName("refs/remotes/origin/HEAD"))
    }

    @Test
    fun commitGraph_populatesBranchesPerCommit_excludingTags() {
        val src = tmp.newFolder("src")
        Git.init().setDirectory(src).setInitialBranch("main").call().use { git ->
            File(src, "a.txt").writeText("a\n")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor("t", "t@t").setMessage("A").call()
            // commitA を指すタグ v1.0 と release ブランチ(= main と release が同一コミットを指す)。
            git.tag().setName("v1.0").call()
            git.branchCreate().setName("release").call()
            // feature に commitB。
            git.checkout().setName("feature").setCreateBranch(true).call()
            File(src, "b.txt").writeText("b\n")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor("t", "t@t").setMessage("B").call()
            git.checkout().setName("main").call()
        }
        val clone = tmp.newFolder("clone")
        JgitClient().clone(src.absolutePath, clone, null)

        val graph = JgitClient().commitGraph(clone)
        val a = graph.first { it.shortMessage == "A" }
        val b = graph.first { it.shortMessage == "B" }

        // commitA は main と release の2ブランチが指す → 切替メニューに2項目並ぶ(ソート済み)。
        assertEquals(listOf("main", "release"), a.branches)
        // タグ v1.0 は branches に含めない(切替対象でない)が、refs(チップ)には含む。
        assertFalse("v1.0" in a.branches)
        assertTrue("v1.0" in a.refs)
        // commitB は feature のみ。
        assertEquals(listOf("feature"), b.branches)
    }
}
