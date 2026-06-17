package jp.lazmix.codeleaf

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 整形 Markdown のメモ追加 E2E。整形本文は AndroidView(TextView) で Compose セマンティクスから
 * 見えないため、ビュー階層からブロックの TextView を探して performLongClick() し、その結果
 * Compose の追加シート(「メモを追加」)が開くこと=長押し配線を検証する。
 */
@RunWith(AndroidJUnit4::class)
class MarkdownMemoE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.createSrcRepo(
            "md-src",
            mapOf("README.md" to "# CodeLeaf\n\nformatted paragraph marker_para\n\n## 使い方\n\nbody\n"),
        )
        app.addFixtureRepo("md-fixture", src)
        runBlocking {
            val repo = app.container.repoRepository.observeRepos().first().first { it.name == "md-fixture" }
            app.container.memoRepository.createMemo(repo.id, "レビュー")
        }
        compose.onNodeWithText("md-fixture").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("README.md").performClick()
        // 整形ビューが開けば下部バーに「☰ 目次」が出る(## 見出しあり)。
        compose.waitUntil(10_000) { compose.onAllNodesWithText("☰ 目次").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun longPressMarkdownBlock_opensAddSheet_andSaves() {
        // 段落の TextView を探して長押し(配線を直接発火)。
        compose.activityRule.scenario.onActivity { activity ->
            val tv = findTextView(activity.window.decorView, "marker_para")
                ?: throw AssertionError("markdown TextView with the paragraph not found")
            tv.performLongClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("メモを追加").assertIsDisplayed()

        // 既定の既存メモ帳「レビュー」へ保存できる。
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isEmpty() }

        // ⋮ → メモ → レビューが 1 件。
        compose.onNodeWithContentDescription("メニュー").performClick()
        compose.onNodeWithText("メモ").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("レビュー").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 件").assertIsDisplayed()
    }

    /** decorView 以下から、指定文字列を含む最初の TextView を返す。 */
    private fun findTextView(root: View, contains: String): TextView? {
        if (root is TextView && root.text?.contains(contains) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTextView(root.getChildAt(i), contains)?.let { return it }
            }
        }
        return null
    }
}
