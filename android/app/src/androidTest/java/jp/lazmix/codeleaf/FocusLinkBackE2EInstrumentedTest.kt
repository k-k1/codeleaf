package jp.lazmix.codeleaf

import android.content.pm.ActivityInfo
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
 * 集中モード中に Markdown リンクで別ファイルへ遷移した後の戻る挙動を検証する。
 * 集中モードは多ペイン(>=600dp)でのみ有効なので landscape に固定して実行する。
 * 期待: リンクで潜った先で戻る → 集中は維持したまま前のファイルへ。先頭ファイルでもう一度戻る → 集中解除。
 */
@RunWith(AndroidJUnit4::class)
class FocusLinkBackE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // index.md にサブファイルへの相対リンク。両方に整形本文の目印を置く。
        val src = app.createSrcRepo(
            "focuslink-src",
            mapOf(
                "index.md" to "# Index\n\n[LINKGO](sub.md)\n\nindex_marker_para\n",
                "sub.md" to "# Sub\n\nsub_marker_para\n",
            ),
        )
        app.addFixtureRepo("focuslink-repo", src)
        // 集中モードを使えるよう landscape(多ペイン)に固定。MainActivity は configChanges で再生成しない。
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
    }

    private fun waitDesc(desc: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** decorView 以下から、指定文字列を含む最初の TextView を返す。 */
    private fun findTextView(root: View, contains: String): TextView? {
        if (root is TextView && root.text?.contains(contains) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findTextView(root.getChildAt(i), contains)?.let { return it }
        }
        return null
    }

    private fun waitForBody(marker: String) {
        compose.waitUntil(10_000) {
            var found = false
            compose.activityRule.scenario.onActivity { found = findTextView(it.window.decorView, marker) != null }
            found
        }
    }

    private fun bodyGone(marker: String): Boolean {
        var present = false
        compose.activityRule.scenario.onActivity { present = findTextView(it.window.decorView, marker) != null }
        return !present
    }

    @Test
    fun back_inFocus_afterLink_returnsToPrevFile_keepingFocus() {
        compose.onNodeWithText("focuslink-repo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("index.md").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("index.md").performClick()
        waitForBody("index_marker_para")

        // 集中モードへ。
        compose.onNodeWithContentDescription("集中モード(全幅)").performClick()
        waitDesc("一覧とレールを表示")

        // 整形本文中の相対リンク LINKGO をクリック(リンクの ClickableSpan を発火)。
        compose.activityRule.scenario.onActivity { activity ->
            val tv = findTextView(activity.window.decorView, "LINKGO")
                ?: throw AssertionError("link TextView not found")
            val sp = tv.text as Spanned
            val spans = sp.getSpans(0, sp.length, ClickableSpan::class.java)
            (spans.firstOrNull() ?: throw AssertionError("ClickableSpan not found")).onClick(tv)
        }
        waitForBody("sub_marker_para") // sub.md に遷移

        // 戻る: 集中は維持したまま前のファイル(index.md)へ。
        pressBack()
        waitForBody("index_marker_para")
        waitDesc("一覧とレールを表示") // まだ集中モード
        compose.waitUntil(10_000) { bodyGone("sub_marker_para") }

        // もう一度戻る: 先頭ファイルなので今度は集中解除(ファイルは開いたまま)。
        pressBack()
        waitDesc("集中モード(全幅)")
        waitForBody("index_marker_para")
    }
}
