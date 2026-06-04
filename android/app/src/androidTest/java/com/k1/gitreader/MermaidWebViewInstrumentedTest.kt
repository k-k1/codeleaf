package com.k1.gitreader

import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k1.gitreader.render.buildMermaidHtml
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android ランタイム(ART)上で、assets 同梱の mermaid.min.js が図を描画し、
 * 高さ測定 JS が JavascriptInterface 経由で正の実高さ(CSS px)を通知することを検証する。
 * これにより「Mermaid 動的高さ」の描画→測定→ブリッジ通知の経路を end-to-end で確認する。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`（ネットワーク不要・mermaid はオフライン同梱）。
 */
@RunWith(AndroidJUnit4::class)
class MermaidWebViewInstrumentedTest {

    @Test
    fun mermaidRenders_andReportsPositiveHeight_onDevice() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val latch = CountDownLatch(1)
        val reported = AtomicInteger(0)

        instr.runOnMainSync {
            val wv = WebView(ctx)
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
            // ウィンドウ未アタッチでも内部レイアウトが走るよう、明示的に幅を与える。
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            wv.measure(widthSpec, heightSpec)
            wv.layout(0, 0, 1080, 2000)

            val html = buildMermaidHtml("graph TD; A-->B; B-->C;", dark = false)
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        }

        assertTrue("高さ通知が一定時間内に来る(=mermaid 描画完了)", latch.await(20, TimeUnit.SECONDS))
        assertTrue("通知された高さが正(=図が描画されている): ${reported.get()}", reported.get() > 0)
    }
}
