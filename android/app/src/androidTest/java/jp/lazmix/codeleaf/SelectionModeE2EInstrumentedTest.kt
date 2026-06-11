package jp.lazmix.codeleaf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 選択モード(⋮「テキストを選択」)の効果を検証する。ON の間はコード行長押しのメモ追加を無効化し、
 * OFF に戻すと従来どおりメモ追加シートが出ることを確認する（衝突回避の核）。
 */
@RunWith(AndroidJUnit4::class)
class SelectionModeE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        app.container.settingsStore.setSelectByDefault(false) // 既定 OFF で決定論化
        val src = app.createSrcRepo("sel-src", mapOf("code.kt" to "package x\nval sel_target = 7\nfun main() {}\n"))
        app.addFixtureRepo("sel-fixture", src)
        compose.onNodeWithText("sel-fixture").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("code.kt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("code.kt").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("val sel_target = 7").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        // 他テストへ影響しないよう既定値を戻す（設定はトグルで永続化されるため）。
        app.container.settingsStore.setSelectByDefault(false)
    }

    private fun toggleSelection(label: String) {
        compose.onNodeWithContentDescription("メニュー").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun selectionMode_togglesLineMemoLongPress() {
        // 選択モード ON → 行長押しでメモ追加シートは出ない。
        toggleSelection("テキストを選択")
        compose.onNodeWithText("val sel_target = 7").performTouchInput { longClick() }
        compose.waitForIdle()
        assert(compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isEmpty()) {
            "選択モード中は長押しメモが無効のはず"
        }

        // 選択モード OFF → 行長押しでメモ追加シートが出る（トグルが復帰する）。
        toggleSelection("選択を終了")
        compose.onNodeWithText("val sel_target = 7").performTouchInput { longClick() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isNotEmpty() }
    }
}
