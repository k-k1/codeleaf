package jp.lazmix.codeleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.lazmix.codeleaf.git.JgitClient
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

/**
 * JgitClient.lastCommits を端末上のローカルリポ（NW 不要・決定論）で検証する計装テスト。
 * 著者/日時を固定したコミットを並べ、ファイル=完全一致・フォルダ=配下最新・打ち切りを確認する。
 */
@RunWith(AndroidJUnit4::class)
class JgitLastCommitsInstrumentedTest {

    private fun who(name: String, epochSec: Long) =
        PersonIdent(name, "$name@example.com", Date(epochSec * 1000), java.util.TimeZone.getTimeZone("UTC"))

    private fun write(dir: File, rel: String, body: String) {
        val f = File(dir, rel)
        f.parentFile?.mkdirs()
        f.writeText(body)
    }

    @Test
    fun lastCommits_resolvesFilesFoldersAndCap() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "itest-lastcommits")
        dir.deleteRecursively()
        dir.mkdirs()

        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            // c1(Alice, t=1000): LICENSE, a/keep.txt
            write(dir, "LICENSE", "MIT")
            write(dir, "a/keep.txt", "v1")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor(who("Alice", 1000)).setCommitter(who("Alice", 1000)).setMessage("c1").call()

            // c2(Bob, t=2000): b/x.txt
            write(dir, "b/x.txt", "x")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor(who("Bob", 2000)).setCommitter(who("Bob", 2000)).setMessage("c2").call()

            // c3(Carol, t=3000): a/keep.txt を更新
            write(dir, "a/keep.txt", "v2")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor(who("Carol", 3000)).setCommitter(who("Carol", 3000)).setMessage("c3").call()
        }

        val jgit = JgitClient()

        // ルート直下: LICENSE=ファイル完全一致, a/b=フォルダ配下最新。
        val root = jgit.lastCommits(dir, "", listOf("LICENSE", "a", "b"), maxCommits = 100)
        assertEquals("Alice", root["LICENSE"]?.author)
        assertEquals(1000L, root["LICENSE"]?.at?.epochSecond)
        assertEquals("Carol", root["a"]?.author) // a 配下の最新は c3
        assertEquals(3000L, root["a"]?.at?.epochSecond)
        assertEquals("Bob", root["b"]?.author)
        assertEquals(2000L, root["b"]?.at?.epochSecond)

        // relDir 限定(サブフォルダ a の中)でファイル完全一致。
        val inA = jgit.lastCommits(dir, "a", listOf("a/keep.txt"), maxCommits = 100)
        assertEquals("Carol", inA["a/keep.txt"]?.author)

        // 打ち切り: 最新1コミット(c3)だけ見ると a のみ解決し、LICENSE/b は未解決(マップに無い)。
        val capped = jgit.lastCommits(dir, "", listOf("LICENSE", "a", "b"), maxCommits = 1)
        assertEquals("Carol", capped["a"]?.author)
        assertNull(capped["LICENSE"])
        assertNull(capped["b"])
        assertTrue(capped.containsKey("a"))
        assertFalse(capped.containsKey("LICENSE"))

        dir.deleteRecursively()
    }
}
