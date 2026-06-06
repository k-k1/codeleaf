package jp.lazmix.codeleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.lazmix.codeleaf.git.JgitClient
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * commitGraph の inCurrentBranch(現在チェックアウト中ブランチから到達可能か)を検証する。
 * ローカル git リポを作るだけなのでネットワーク非依存。
 *
 * 構成: main = A→B、feature = A→B→C。HEAD を main(=B) にした状態で、
 * C(feature 専用)のみ inCurrentBranch=false、A/B は true になることを確認する。
 */
@RunWith(AndroidJUnit4::class)
class CommitGraphReachabilityInstrumentedTest {

    @Test
    fun offBranchCommit_isMarkedNotInCurrentBranch() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "graph-reach-src").apply { deleteRecursively(); mkdirs() }

        Git.init().setInitialBranch("main").setDirectory(dir).call().use { git ->
            fun commit(name: String, msg: String) {
                File(dir, name).writeText(msg)
                git.add().addFilepattern(".").call()
                git.commit().setMessage(msg)
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            commit("a.txt", "A")
            commit("b.txt", "B") // main = B
            git.checkout().setCreateBranch(true).setName("feature").call()
            commit("c.txt", "C") // feature = C(B の子)
            git.checkout().setName("main").call() // HEAD = main(=B)
        }

        val graph = JgitClient().commitGraph(dir)
        fun inBranch(msg: String) = graph.first { it.shortMessage == msg }.inCurrentBranch

        assertTrue("A は現ブランチ(main)から到達可能", inBranch("A"))
        assertTrue("B は現ブランチ(main)の先端", inBranch("B"))
        assertEquals("C は feature 専用なので現ブランチ非到達", false, inBranch("C"))
    }
}
