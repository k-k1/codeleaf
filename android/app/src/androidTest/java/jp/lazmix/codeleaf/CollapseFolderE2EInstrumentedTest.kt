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

/**
 * 単一子フォルダ連鎖の畳み込みを検証する。src/main/java/App.java は src/main/java が
 * それぞれ唯一の子なので、ルートで「src/main/java」1エントリに畳まれ、タップで最深へ直行する。
 */
@RunWith(AndroidJUnit4::class)
class CollapseFolderE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.createSrcRepo("cl-src", mapOf("src/main/java/App.java" to "class App {}\n"))
        app.addFixtureRepo("cl-fixture", src)
        compose.onNodeWithText("cl-fixture").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("src/main/java").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun singleChildChain_isCollapsed_andOpensDeepest() {
        // ルートでは中間階層を辿らず「src/main/java」1エントリ。
        compose.onNodeWithText("src/main/java").assertIsDisplayed()
        // タップ → 最深(src/main/java)へ直行し App.java が見える。
        compose.onNodeWithText("src/main/java").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("App.java").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("App.java").assertIsDisplayed()
        // パンくすには各階層が個別に出る(java が現在地=太字、main/src は祖先リンク)。
        compose.onNodeWithText("main").assertIsDisplayed()
    }
}
