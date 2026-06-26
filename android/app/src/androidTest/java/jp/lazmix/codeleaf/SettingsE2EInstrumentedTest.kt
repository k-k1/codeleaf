package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.FontScale
import jp.lazmix.codeleaf.data.db.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 設定画面の E2E: デフォルトテーマ/フォントサイズの永続化と、キャッシュ全削除を実 UI で検証する。
 * ネットワーク非依存(ローカル git リポを file パスで clone してリポを 1 件用意)。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class SettingsE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        // 既定値に戻し、既存リポを一掃してから検証用リポを 1 件用意する。
        resetAppLocaleToSystem() // ja 前提の文字列 assert が残留ロケール上書きで壊れないように
        app.container.settingsStore.setDefaultTheme(ThemeMode.SYSTEM)
        app.container.settingsStore.setFontScale(FontScale.MEDIUM)
        app.cleanRepos()
        srcRepo = app.createSrcRepo("settings-src", mapOf("README.md" to "# Title\n"))
        app.addFixtureRepo("settings-fixture", srcRepo)
    }

    @Test
    fun settings_persistThemeAndFont_andClearCache() {
        // List → ⚙ 設定
        compose.onNodeWithContentDescription("設定").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("デフォルトテーマ").fetchSemanticsNodes().isNotEmpty()
        }

        // テーマ=ダーク / フォント=大 を選択 → SettingsStore に永続化される
        compose.onNodeWithText("ダーク").performClick()
        compose.onNodeWithText("大").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, app.container.settingsStore.settings.value.defaultTheme)
        assertEquals(FontScale.LARGE, app.container.settingsStore.settings.value.fontScale)

        // キャッシュ全削除 → 確認ダイアログ → 削除(縦スクロールするので scrollTo してから)
        compose.onNodeWithText("キャッシュを全削除").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("削除").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("削除").performClick()

        // リポジトリが全削除されるまで待つ
        val repo = app.container.repoRepository
        compose.waitUntil(timeoutMillis = 10_000) {
            runBlocking { repo.observeRepos().first().isEmpty() }
        }
        assertTrue(runBlocking { repo.observeRepos().first().isEmpty() })

        // 設定画面のカウントが 0 件に更新される
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("登録中のリポジトリ: 0 件", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("登録中のリポジトリ: 0 件", substring = true).assertIsDisplayed()
    }
}
