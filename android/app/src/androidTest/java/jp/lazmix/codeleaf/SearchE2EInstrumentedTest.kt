package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
        app.cleanRepos()
        srcRepo = app.createSrcRepo(
            "search-src",
            mapOf(
                "README.md" to "# Title\n\nneedle alpha\n",
                "docs/guide.md" to "needle beta\n",
                "other.txt" to "nothing relevant here\n",
            ),
        )
        app.addFixtureRepo("search-fixture", srcRepo)
    }

    private fun openSearch() {
        compose.onNodeWithText("search-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("検索").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("全文検索", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // クエリ欄(ラベルは通常/正規表現で変わる)とパス絞り込み欄を取り違えないよう label で特定する。
    private fun typeQuery(text: String) {
        compose.onNode(
            hasSetTextAction() and (hasText("全文検索", substring = true) or hasText("正規表現で検索", substring = true)),
        ).performTextInput(text)
    }

    private fun typePathFilter(text: String) {
        compose.onNode(hasSetTextAction() and hasText("絞り込み", substring = true)).performTextInput(text)
    }

    @Test
    fun literalSearch_findsMatches_andJumpsToLine() {
        openSearch()
        typeQuery("needle")
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
        typeQuery("be.a")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 docs/guide.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("📄 docs/guide.md").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("📄 README.md").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun multiWordAnd_matchesLinesWithAllTerms() {
        openSearch()
        // "needle beta" は guide(needle beta) のみ。README(needle alpha) は beta が無く除外
        typeQuery("needle beta")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 docs/guide.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("📄 docs/guide.md").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("📄 README.md").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun pathFilter_restrictsToMatchingFiles() {
        openSearch()
        typeQuery("needle")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 README.md").fetchSemanticsNodes().isNotEmpty()
        }
        // "guide" で絞り込み → docs/guide.md のみ、README は除外
        typePathFilter("guide")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("📄 README.md").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("📄 docs/guide.md").assertIsDisplayed()
    }
}
