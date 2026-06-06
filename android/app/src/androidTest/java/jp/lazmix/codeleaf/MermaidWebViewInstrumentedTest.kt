package jp.lazmix.codeleaf

import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.render.buildMermaidHtml
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android ランタイム(ART)上で、assets 同梱の mermaid.min.js が図を描画し、
 * 高さ測定 JS が JavascriptInterface 経由で正の実高さ(CSS px)を通知することを検証する。
 *
 * WebView は**実ウィンドウに attach** して描画させる(detach 状態の WebView はフルスイートの
 * 負荷下でレンダラがスロットルされ高さ通知が来ないことがあるため。MainActivity の content に載せる)。
 */
@RunWith(AndroidJUnit4::class)
class MermaidWebViewInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mermaidRenders_andReportsPositiveHeight_onDevice() {
        val latch = CountDownLatch(1)
        val reported = AtomicInteger(0)

        compose.activityRule.scenario.onActivity { activity ->
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onHeight(px: Int) {
                        reported.set(px)
                        latch.countDown()
                    }
                },
                "AndroidHeight",
            )
            // content に載せてウィンドウへ attach する(描画が確実に走る)。
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.addView(wv, ViewGroup.LayoutParams(1080, 2000))

            val html = buildMermaidHtml("graph TD; A-->B; B-->C;", dark = false)
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        }

        assertTrue("高さ通知が一定時間内に来る(=mermaid 描画完了)", latch.await(30, TimeUnit.SECONDS))
        assertTrue("通知された高さが正(=図が描画されている): ${reported.get()}", reported.get() > 0)
    }
}
