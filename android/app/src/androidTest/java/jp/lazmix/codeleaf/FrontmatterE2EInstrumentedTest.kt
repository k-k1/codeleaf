package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.NewRepo
import jp.lazmix.codeleaf.data.db.GitHost
import jp.lazmix.codeleaf.data.db.ThemeMode
import org.eclipse.jgit.api.Git
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * フロントマター付き Markdown を開くと、メタ情報がカード(Compose)として表示されることを検証する。
 * Markdown 本文は TextView で不可視だが、FrontmatterView は Compose なので照合できる。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class FrontmatterE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "fm-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText("---\ntitle: Hello\nauthor: kk\n---\n# Body\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "fm-fixture",
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
    fun frontmatter_isShownAsMetadataCard() {
        compose.onNodeWithText("fm-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("README.md").performClick()

        // フロントマターのキー/値が Compose ノードとして表示される
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("title").assertIsDisplayed()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithText("author").assertIsDisplayed()
        compose.onNodeWithText("kk").assertIsDisplayed()
    }
}
