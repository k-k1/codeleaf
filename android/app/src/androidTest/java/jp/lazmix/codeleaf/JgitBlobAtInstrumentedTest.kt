package jp.lazmix.codeleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.lazmix.codeleaf.git.JgitClient
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * JgitClient.readBytesAt（指定コミット時点の blob 読み出し）の計装テスト。
 * 端末上にローカル git リポを作り（ネットワーク不要）、各コミット時点の内容と、
 * そのコミットで削除されたファイルが親(sha^)にフォールバックすることを検証する。
 */
@RunWith(AndroidJUnit4::class)
class JgitBlobAtInstrumentedTest {

    @Test
    fun readBytesAt_returnsBlobAtCommit_andFallsBackToParentForDeleted() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "itest-blobat").apply { deleteRecursively(); mkdirs() }
        val jgit = JgitClient()

        Git.init().setInitialBranch("main").setDirectory(dir).call().use { git ->
            // c1: a.txt=v1, b.txt=bye
            File(dir, "a.txt").writeText("v1\n")
            File(dir, "b.txt").writeText("bye\n")
            git.add().addFilepattern(".").call()
            val c1 = git.commit().setMessage("c1").setAuthor("t", "t@e").setCommitter("t", "t@e").call()

            // c2: a.txt を v2 に変更・b.txt を削除
            File(dir, "a.txt").writeText("v2\n")
            git.add().addFilepattern("a.txt").call()
            git.rm().addFilepattern("b.txt").call()
            val c2 = git.commit().setMessage("c2").setAuthor("t", "t@e").setCommitter("t", "t@e").call()

            assertEquals("v2\n", jgit.readBytesAt(dir, "a.txt", c2.name)?.toString(Charsets.UTF_8))
            assertEquals("v1\n", jgit.readBytesAt(dir, "a.txt", c1.name)?.toString(Charsets.UTF_8))
            // b.txt は c2 のツリーに無い → 親 c1 の内容を返す
            assertEquals("bye\n", jgit.readBytesAt(dir, "b.txt", c2.name)?.toString(Charsets.UTF_8))
            // どのコミットにも無いパスは null
            assertNull(jgit.readBytesAt(dir, "nope.txt", c2.name))
        }

        dir.deleteRecursively()
    }
}
