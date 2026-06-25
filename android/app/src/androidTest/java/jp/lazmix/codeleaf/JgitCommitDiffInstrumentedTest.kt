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
 * JgitClient.commitFileSummaries（コミットの変更ファイル一覧を安価スキャン）と、
 * filterPath による各ファイルの遅延整形・巨大 diff のサイズ上限切り詰めを検証する。
 * 端末上にローカル git リポを作る（ネットワーク不要）。
 */
@RunWith(AndroidJUnit4::class)
class JgitCommitDiffInstrumentedTest {

    @Test
    fun commitFileSummaries_listsAddModifyDelete_andPerFileDiffByFilterPath() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "itest-commitdiff").apply { deleteRecursively(); mkdirs() }
        val jgit = JgitClient()

        Git.init().setInitialBranch("main").setDirectory(dir).call().use { git ->
            File(dir, "a.txt").writeText("a1\n")
            File(dir, "b.txt").writeText("b1\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("c1").setAuthor("t", "t@e").setCommitter("t", "t@e").call()

            // c2: a.txt 変更 / b.txt 削除 / c.txt 追加
            File(dir, "a.txt").writeText("a2\n")
            File(dir, "c.txt").writeText("c1\n")
            git.add().addFilepattern("a.txt").call()
            git.add().addFilepattern("c.txt").call()
            git.rm().addFilepattern("b.txt").call()
            val c2 = git.commit().setMessage("c2").setAuthor("t", "t@e").setCommitter("t", "t@e").call()

            val summaries = jgit.commitFileSummaries(dir, c2.name)
            val byPath = summaries.associateBy { it.filterPath }
            assertEquals(setOf("a.txt", "b.txt", "c.txt"), byPath.keys)
            assertEquals("MODIFY", byPath["a.txt"]!!.changeType)
            assertEquals("DELETE", byPath["b.txt"]!!.changeType)
            assertEquals("ADD", byPath["c.txt"]!!.changeType)
            // 削除ファイルは開く対象=旧側パス（/dev/null にしない）。
            assertEquals("b.txt", byPath["b.txt"]!!.openPath)

            // filterPath で1ファイルだけ遅延整形できる（削除ファイルも旧側 path で取れる）。
            val aDiff = jgit.diff(dir, byPath["a.txt"]!!.filterPath, c2.name)
            assertTrue(aDiff.contains("-a1"))
            assertTrue(aDiff.contains("+a2"))
            assertTrue(aDiff.contains("a.txt"))
            // a.txt の diff に他ファイルは混ざらない。
            assertTrue(!aDiff.contains("c.txt"))

            val bDiff = jgit.diff(dir, byPath["b.txt"]!!.filterPath, c2.name)
            assertTrue(bDiff.contains("-b1"))
        }

        dir.deleteRecursively()
    }

    @Test
    fun perFileDiff_isTruncated_whenExceedingSizeCap() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "itest-bigdiff").apply { deleteRecursively(); mkdirs() }
        val jgit = JgitClient()

        Git.init().setInitialBranch("main").setDirectory(dir).call().use { git ->
            // 上限(512KiB)を確実に超える本文を持つファイルを追加（1行を長めにして総量を稼ぐ）。
            val big = buildString { repeat(40_000) { append("line ").append(it).append(" ").append("x".repeat(30)).append('\n') } }
            File(dir, "big.txt").writeText(big)
            git.add().addFilepattern(".").call()
            val c1 = git.commit().setMessage("big").setAuthor("t", "t@e").setCommitter("t", "t@e").call()

            val diff = jgit.diff(dir, "big.txt", c1.name)
            assertTrue("巨大 diff は切り詰め注記が付くはず", diff.contains("diff が大きいため以降を省略"))
        }

        dir.deleteRecursively()
    }
}
