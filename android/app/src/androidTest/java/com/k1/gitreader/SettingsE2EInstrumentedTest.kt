package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k1.gitreader.data.FontScale
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode
import org.eclipse.jgit.api.Git
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

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            // 既定値に戻し、既存リポを一掃してから検証用リポを 1 件用意する。
            app.container.settingsStore.setDefaultTheme(ThemeMode.SYSTEM)
            app.container.settingsStore.setFontScale(FontScale.MEDIUM)
            repo.observeRepos().first().forEach { repo.delete(it) }

            srcRepo = File(app.cacheDir, "settings-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText("# Title\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "settings-fixture",
                    url = srcRepo.absolutePath,
                    host = GitHost.GITHUB,
                    username = "",
                    token = "x",
                    branch = null,
                    themeMode = ThemeMode.SYSTEM,
                ),
            )
        }
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
