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
 * ブラウザ一覧を下までスクロール → ファイルを開く → 戻る、でスクロール位置が保持されることを検証する。
 * (1ペインでは戻りでブラウザがアンマウントされるため、SaveableStateHolder で保持する必要がある。)
 */
@RunWith(AndroidJUnit4::class)
class BrowserScrollKeepE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // 一覧がスクロールするよう多数ファイル(f00..f29)。f29 は中身に目印。
        val files = (0..29).associate { i -> "f%02d.txt".format(i) to if (i == 29) "deep_marker\n" else "x\n" }
        val src = app.createSrcRepo("scroll-src", files)
        app.addFixtureRepo("scroll-repo", src)
        compose.onNodeWithText("scroll-repo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("f00.txt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun browserScroll_isKeptAfterOpeningFileAndBack() {
        // 一覧を f29 まで送る。
        compose.onNodeWithTag("browserFileList").performScrollToNode(hasText("f29.txt"))
        compose.onNodeWithText("f29.txt").assertIsDisplayed()

        // f29 を開く → ビューア。
        compose.onNodeWithText("f29.txt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("deep_marker").fetchSemanticsNodes().isNotEmpty() }

        // 戻る → ブラウザ。スクロールが保持され f29.txt がまだ表示されている(先頭に戻っていない)。
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("f29.txt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("f29.txt").assertIsDisplayed()
    }
}
