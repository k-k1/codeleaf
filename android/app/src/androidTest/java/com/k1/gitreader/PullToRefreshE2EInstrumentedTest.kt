package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode
import org.eclipse.jgit.api.Git
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * ファイルブラウザの pull-to-refresh で閲覧中リポが同期されることを実 UI で検証する。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class PullToRefreshE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "ptr-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText("# Title\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "ptr-fixture",
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
    fun pullToRefresh_syncsCurrentRepo() {
        // リポを開く → ブラウザに README.md
        compose.onNodeWithText("ptr-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }

        // ファイル一覧を下に引く → 同期 → 「同期完了」スナックバー
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("同期完了").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("同期完了").assertIsDisplayed()
    }
}
