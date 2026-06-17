package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.FontScale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 設定の「コードの折り返し(既定)」OFF が、コード表示の初期状態に反映されることを検証する。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class WrapDefaultE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        app.container.settingsStore.setWrapByDefault(true) // 既定値から開始
        app.container.settingsStore.setFontScale(FontScale.MEDIUM)
        app.cleanRepos()
        srcRepo = app.createSrcRepo("wrapdef-src", mapOf("Sample.kt" to "val x = 1\n"))
        app.addFixtureRepo("wrapdef-fixture", srcRepo)
    }

    @Test
    fun wrapDefaultOff_appliesToCodeView() {
        // 設定でデフォルト折り返しを OFF にする
        compose.onNodeWithContentDescription("設定").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("コードの折り返し（既定）").fetchSemanticsNodes().isNotEmpty()
        }
        // 「コードの折り返し（既定）」のスイッチを testTag で特定して押す(並び順に依存しない)。
        compose.onNodeWithTag("settingSwitch:コードの折り返し（既定）").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, app.container.settingsStore.settings.value.wrapByDefault)

        // 一覧へ戻り、コードファイルを開くと初期状態が「折り返しOFF」
        compose.onNodeWithContentDescription("戻る").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("wrapdef-fixture").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("wrapdef-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Sample.kt").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sample.kt").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("折り返しOFF").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("折り返しOFF").assertIsDisplayed()
    }
}
