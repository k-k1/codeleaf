package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * 全文検索の E2E: 部分一致＋行ジャンプ、正規表現検索。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class SearchE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "search-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText("# Title\n\nneedle alpha\n")
                File(srcRepo, "docs").mkdirs()
                File(srcRepo, "docs/guide.md").writeText("needle beta\n")
                File(srcRepo, "other.txt").writeText("nothing relevant here\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "search-fixture",
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

    private fun openSearch() {
        compose.onNodeWithText("search-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("検索").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("ファイル内を全文検索").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun literalSearch_findsMatches_andJumpsToLine() {
        openSearch()
        compose.onNode(hasSetTextAction()).performTextInput("needle")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 docs/guide.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("📄 README.md").assertIsDisplayed()
        compose.onNodeWithText("📄 docs/guide.md").assertIsDisplayed()

        // ヒット行をタップ → Raw 表示で該当行へジャンプ(行テキストが表示される)
        compose.onNodeWithText("L1: needle beta").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("needle beta").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("needle beta").assertIsDisplayed()
    }

    @Test
    fun regexSearch_matchesPatternOnly() {
        openSearch()
        // 正規表現トグル(.*) を有効化して "be.a" を検索 → guide(beta) のみ一致、README(alpha)は不一致
        compose.onNodeWithText(".*").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("be.a")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 docs/guide.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("📄 docs/guide.md").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("📄 README.md").fetchSemanticsNodes().isEmpty()
        }
    }
}
