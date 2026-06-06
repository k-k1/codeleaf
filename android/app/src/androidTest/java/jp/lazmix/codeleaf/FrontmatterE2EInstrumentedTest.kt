package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
        app.cleanRepos()
        srcRepo = app.createSrcRepo("fm-src", mapOf("README.md" to "---\ntitle: Hello\nauthor: kk\n---\n# Body\n"))
        app.addFixtureRepo("fm-fixture", srcRepo)
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
