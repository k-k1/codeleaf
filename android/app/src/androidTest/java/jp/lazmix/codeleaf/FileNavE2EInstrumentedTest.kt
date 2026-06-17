package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 下部バー右端の「前/次ファイル」送りを検証する。同一フォルダのファイルを名前順に行き来し、
 * ビューアの本文が入れ替わることを確認する(ネットワーク非依存・ローカル git リポを file clone)。
 */
@RunWith(AndroidJUnit4::class)
class FileNavE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // 名前順 a < b < c。各ファイルはインデントなしの一意な行を持つ。
        val src = app.createSrcRepo(
            "nav-src",
            mapOf(
                "a.txt" to "alpha_marker\n",
                "b.txt" to "bravo_marker\n",
                "c.txt" to "charlie_marker\n",
            ),
        )
        app.addFixtureRepo("nav-fixture", src)
        compose.onNodeWithText("nav-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("a.txt").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForLine(line: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun nextAndPrev_walkSiblingsInOrder() {
        compose.onNodeWithText("a.txt").performClick()
        waitForLine("alpha_marker")

        // 次 → b
        compose.onNodeWithContentDescription("次のファイル").performClick()
        waitForLine("bravo_marker")

        // 次 → c
        compose.onNodeWithContentDescription("次のファイル").performClick()
        waitForLine("charlie_marker")

        // 前 → b
        compose.onNodeWithContentDescription("前のファイル").performClick()
        waitForLine("bravo_marker")
        compose.onNodeWithText("bravo_marker").assertIsDisplayed()
    }
}
