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
 * 目次の E2E: 見出し付き Markdown を開き、☰目次 で見出し一覧(ボトムシート)が出て、
 * 見出しタップでシートが閉じることを検証する。シートの見出しは Compose なので照合可能。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class TocE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        app.cleanRepos()
        srcRepo = app.createSrcRepo(
            "toc-src",
            mapOf("README.md" to "# Alpha\n\ntext a\n\n## Beta\n\ntext b\n\n## Gamma\n\ntext c\n"),
        )
        app.addFixtureRepo("toc-fixture", srcRepo)
    }

    @Test
    fun toc_listsHeadings_andDismissesOnTap() {
        compose.onNodeWithText("toc-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("README.md").performClick()

        // 目次ボタン → ボトムシートに見出しが並ぶ
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("☰ 目次").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("☰ 目次").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithText("Gamma").assertIsDisplayed()

        // 見出しタップ → シートが閉じる(タイトル "目次" が消える)
        compose.onNodeWithText("Beta").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("目次").fetchSemanticsNodes().isEmpty()
        }
    }
}
