package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.TableMode
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
 * テーブル表示形式=横スクロール のとき、GFM テーブルが専用 Compose テーブルとして描画され、
 * セルが Compose ノードとして照合できることを検証する(INLINE は TextView 内で不可視)。
 */
@RunWith(AndroidJUnit4::class)
class TableModeE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            app.container.settingsStore.setTableMode(TableMode.SCROLLABLE)
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "table-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText(
                    "# Doc\n\n| 項目 | 値 |\n| --- | --- |\n| 言語 | Kotlin |\n| UI | Compose |\n",
                )
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@e").setCommitter("t", "t@e").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "table-fixture",
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
    fun scrollableTable_rendersCellsAsComposeNodes() {
        compose.onNodeWithText("table-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("README.md").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Kotlin").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("項目").assertIsDisplayed() // ヘッダセル
        compose.onNodeWithText("言語").assertIsDisplayed()
        compose.onNodeWithText("Kotlin").assertIsDisplayed()
        compose.onNodeWithText("Compose").assertIsDisplayed()
    }
}
