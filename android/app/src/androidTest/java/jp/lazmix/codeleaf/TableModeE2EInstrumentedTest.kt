package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.TableMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
        app.container.settingsStore.setTableMode(TableMode.SCROLLABLE)
        app.cleanRepos()
        srcRepo = app.createSrcRepo(
            "table-src",
            mapOf("README.md" to "# Doc\n\n| 項目 | 値 |\n| --- | --- |\n| 言語 | Kotlin |\n| UI | Compose |\n"),
        )
        app.addFixtureRepo("table-fixture", srcRepo)
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
