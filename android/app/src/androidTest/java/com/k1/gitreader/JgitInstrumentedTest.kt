package com.k1.gitreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k1.gitreader.git.JgitClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Android ランタイム(ART)上で JgitClient が動作することを検証する計装テスト。
 * 公開リポ octocat/Hello-World に対し clone/fetch/branch一覧/reset --hard+clean/log/diff を実行。
 *
 * 実行: エミュ or 実機を接続して `./gradlew connectedDebugAndroidTest`
 * （ネットワーク必須）。
 */
@RunWith(AndroidJUnit4::class)
class JgitInstrumentedTest {

    private val url = "https://github.com/octocat/Hello-World.git"

    @Test
    fun clone_fetch_reset_log_diff_onDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "itest-hello-world")
        dir.deleteRecursively()

        val jgit = JgitClient()

        // clone（公開リポ・認証なし）
        jgit.clone(url, dir, null)
        assertTrue("clone後 .git が存在", File(dir, ".git").exists())

        // ブランチ一覧（直近順・複数）
        val branches = jgit.listBranches(dir)
        assertTrue("ブランチが取得できる", branches.isNotEmpty())
        // 直近順（降順）であること
        val times = branches.map { it.committedAt }
        assertEquals("committedAt 降順", times.sortedDescending(), times)

        val branch = jgit.currentBranch(dir)

        // ローカル変更を作って破棄されることを確認
        val readme = File(dir, "README")
        assertTrue("README が存在", readme.exists())
        val original = readme.readText()
        readme.appendText("\nLOCAL EDIT THAT MUST BE DISCARDED\n")
        val untracked = File(dir, "ITEST_UNTRACKED.tmp").apply { writeText("junk") }

        jgit.sync(dir, branch, null)

        assertEquals("追跡ファイルが復元", original, readme.readText())
        assertTrue("未追跡ファイルが削除", !untracked.exists())

        // 履歴 + diff
        val log = jgit.log(dir, "README", 10)
        assertTrue("README の履歴がある", log.isNotEmpty())
        val diff = jgit.diff(dir, "README", log.first().sha)
        assertTrue("diff が取得できる", diff.isNotBlank())

        dir.deleteRecursively()
    }
}
