package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 大きいテキストのサイズガード E2E。6MB のテキストを開くと自動表示せず確認画面が出て、
 * 「表示する」で先頭のみ読み込み(上限超過)＋注意バーが出ることを検証する。
 */
@RunWith(AndroidJUnit4::class)
class LargeFileGuardE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.gitRepo("big-src") { git, dir ->
            val sb = StringBuilder()
            sb.append("BIGFILESTART\n")
            val line = "x".repeat(99) + "\n" // 100B
            repeat(62_000) { sb.append(line) } // ~6.2MB(上限 5MB 超 → truncated)
            File(dir, "big.txt").writeText(sb.toString())
            git.commitAll("init")
        }
        app.addFixtureRepo("bigrepo", src)
        compose.onNodeWithText("bigrepo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("big.txt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun largeText_guardThenTruncatedLoad() {
        compose.onNodeWithText("big.txt").performClick()
        // 確認画面(自動表示しない)。
        compose.waitUntil(10_000) { compose.onAllNodesWithText("大きいファイル").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("表示する").performClick()
        // 先頭のみ表示の注意バー＋先頭マーカー。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("先頭のみ表示中", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("BIGFILESTART", substring = true).assertIsDisplayed()
    }
}
