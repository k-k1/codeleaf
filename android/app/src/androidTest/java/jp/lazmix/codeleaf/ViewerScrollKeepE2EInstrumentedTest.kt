package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ビューア(コード)のスクロール位置が、ファイルを閉じて開き直しても保持されることを検証する。
 * 1ペインでは戻りでビューアがアンマウントされるため、SaveableStateHolder で保持する必要がある。
 * (Markdown リンク遷移→戻るも同じ holder 機構で保持される。)
 */
@RunWith(AndroidJUnit4::class)
class ViewerScrollKeepE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // 60行のコード。末尾に目印。スクロールしないと見えない。
        val body = (1..58).joinToString("\n") { "line_$it" } + "\nBOTTOM_LINE_MARKER\n"
        val src = app.createSrcRepo("vscroll-src", mapOf("code.txt" to body))
        app.addFixtureRepo("vscroll-repo", src)
        compose.onNodeWithText("vscroll-repo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("code.txt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun viewerScroll_isKeptAfterCloseAndReopen() {
        // code.txt を開いて末尾までスクロール。
        compose.onNodeWithText("code.txt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("line_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("codeContent").performScrollToNode(hasText("BOTTOM_LINE_MARKER"))
        compose.onNodeWithText("BOTTOM_LINE_MARKER").assertIsDisplayed()

        // 戻る(閉じる) → ブラウザ。
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("code.txt").fetchSemanticsNodes().isNotEmpty() }

        // 開き直す → スクロール位置が復元され、末尾の目印がそのまま見えている(先頭に戻っていない)。
        compose.onNodeWithText("code.txt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("BOTTOM_LINE_MARKER").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("BOTTOM_LINE_MARKER").assertIsDisplayed()
    }
}
