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

/**
 * 上部パンくずでフォルダ階層を飛ばして辿れることを検証する(GitHub 風)。
 * a/b/c まで降りてから、パンくずの「a」をタップして一気に a へ戻る。
 */
@RunWith(AndroidJUnit4::class)
class BreadcrumbE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.createSrcRepo("bc-src", mapOf("a/b/c/deep.txt" to "x\n"))
        app.addFixtureRepo("bc-fixture", src)
        compose.onNodeWithText("bc-fixture").performClick()
        waitFor("a")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun breadcrumb_jumpsAcrossLevels() {
        // a → b → c まで降りる(各フォルダはリストに1つだけ現れる)。
        compose.onNodeWithText("a").performClick(); waitFor("b")
        compose.onNodeWithText("b").performClick(); waitFor("c")
        compose.onNodeWithText("c").performClick(); waitFor("deep.txt")

        // パンくずの「a」をタップ → 一気に a 階層へ(b フォルダが見え、deep.txt は消える)。
        compose.onNodeWithText("a").performClick()
        waitFor("b")
        compose.onNodeWithText("b").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("deep.txt").fetchSemanticsNodes().isEmpty() }
    }
}
