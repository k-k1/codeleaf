package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k1.gitreader.data.FontScale
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 設定の「コードの折り返し(既定)」OFF が、コード表示の初期状態に反映されることを検証する。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class WrapDefaultE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            app.container.settingsStore.setWrapByDefault(true) // 既定値から開始
            app.container.settingsStore.setFontScale(FontScale.MEDIUM)
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "wrapdef-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "Sample.kt").writeText("val x = 1\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "wrapdef-fixture",
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
    fun wrapDefaultOff_appliesToCodeView() {
        // 設定でデフォルト折り返しを OFF にする
        compose.onNodeWithContentDescription("設定").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("コードの折り返し（既定）").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(isToggleable()).performClick()
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
