package jp.lazmix.codeleaf

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 集中モード(レール/一覧を隠して全幅表示)の入/出を検証する。1ペイン(portrait)前提。
 * 特に「集中モード中の戻るはファイルを閉じず集中を解除する」(Task 1)を確認する。
 */
@RunWith(AndroidJUnit4::class)
class FocusModeE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.createSrcRepo("focus-src", mapOf("note.txt" to "focus_marker\n"))
        app.addFixtureRepo("focus-fixture", src)
        compose.onNodeWithText("focus-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("note.txt").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("note.txt").performClick()
        waitForLine("focus_marker")
    }

    private fun waitForLine(line: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDesc(desc: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun setOrientation(orientation: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        compose.waitForIdle()
    }

    @Test
    fun back_exitsFocusMode_keepingFileOpen() {
        // 集中モードは多ペイン幅(>=600dp)が前提。SOV43 portrait=411dp は1ペインで
        // 集中ハンドルが出ないため、横向きに回して検証する。
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        // 通常表示: 集中モードへ入るハンドルが見える。
        waitForDesc("集中モード(全幅)")

        // 集中モードへ: ハンドルが「解除」表示に変わる。本文は引き続き表示。
        compose.onNodeWithContentDescription("集中モード(全幅)").performClick()
        waitForDesc("一覧とレールを表示")
        compose.onNodeWithText("focus_marker").assertIsDisplayed()

        // 戻る: 集中だけ解除され、ファイルは開いたまま。
        pressBack()
        waitForDesc("集中モード(全幅)")
        compose.onNodeWithText("focus_marker").assertIsDisplayed()

        // もう一度戻る: 今度はファイルを閉じて一覧へ。
        pressBack()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("focus_marker").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("note.txt").assertIsDisplayed()
    }

    /**
     * 集中モード中に上部のファイル名/パス表示をタップすると、集中だけ解除されファイルは残る
     * (○< ハンドルと同じ)。集中モードは多ペイン幅(>=600dp)が前提なので landscape に回して検証する。
     */
    @Test
    fun tappingTitle_inFocusMode_exitsFocusKeepingFileOpen() {
        // 横向きで多ペイン幅にする(SOV43 portrait=411dp は1ペインで集中ハンドルが出ない)。
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        waitForDesc("集中モード(全幅)")

        // 集中モードへ: ハンドルが「解除」表示に変わる。
        compose.onNodeWithContentDescription("集中モード(全幅)").performClick()
        waitForDesc("一覧とレールを表示")
        compose.onNodeWithText("focus_marker").assertIsDisplayed()

        // タイトルタップで集中解除。ハンドルが「集中モード」へ戻り、ファイルは開いたまま。
        compose.onNodeWithTag("viewerTitle").performClick()
        waitForDesc("集中モード(全幅)")
        compose.onNodeWithText("focus_marker").assertIsDisplayed()
    }
}
