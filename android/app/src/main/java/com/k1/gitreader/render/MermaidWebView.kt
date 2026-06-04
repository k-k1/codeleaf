package com.k1.gitreader.render

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** 実高さ測定前に使う暫定高さ。 */
private const val FALLBACK_HEIGHT_DP = 360

/**
 * ```mermaid ブロックを、assets 同梱の mermaid.min.js でオフライン描画する WebView。
 * 描画完了後に JS が body の実高さ(CSS px ≒ dp)を測って通知し、高さを内容に合わせる。
 * テーマ(dark/default)はリポ毎テーマに連動。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(code: String, dark: Boolean, modifier: Modifier = Modifier) {
    // WebView は再生成されないため state はキー無しで保持し、再描画時に新しい高さで上書きする。
    var measuredDp by remember { mutableIntStateOf(0) }
    val html = remember(code, dark) { buildMermaidHtml(code, dark) }
    val heightDp = if (measuredDp > 0) measuredDp else FALLBACK_HEIGHT_DP
    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp.dp),
        factory = { ctx ->
            val webView = WebView(ctx)
            webView.settings.javaScriptEnabled = true
            webView.setBackgroundColor(Color.TRANSPARENT)
            webView.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onHeight(px: Int) {
                        // JS スレッドから呼ばれるため UI スレッドへ post して state 更新。
                        webView.post { if (px in 1..20_000 && px != measuredDp) measuredDp = px }
                    }
                },
                "AndroidHeight",
            )
            webView
        },
        update = { wv ->
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        },
    )
}

/** Mermaid 描画用 HTML を組み立てる。描画完了後 AndroidHeight.onHeight に実高さ(CSS px)を通知する。 */
internal fun buildMermaidHtml(code: String, dark: Boolean): String {
    val theme = if (dark) "dark" else "default"
    val fg = if (dark) "#e6e6e6" else "#1a1a1a"
    // mermaid は要素の textContent を読むため、HTML 実体参照に変換しても復号されて正しく解釈される。
    val safe = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="mermaid.min.js"></script>
        <style>html,body{margin:0;padding:0;background:transparent;color:$fg;}
        .mermaid{background:transparent;}</style>
        </head><body>
        <pre class="mermaid">$safe</pre>
        <script>
        mermaid.initialize({startOnLoad:false, theme:'$theme', securityLevel:'loose'});
        (async function(){
          try { await mermaid.run({querySelector:'.mermaid'}); } catch(e){}
          // レイアウト確定後に実高さ(CSS px)を測って Android へ通知する。
          requestAnimationFrame(function(){
            var h = Math.ceil(document.body.getBoundingClientRect().height);
            if (window.AndroidHeight && h > 0) AndroidHeight.onHeight(h);
          });
        })();
        </script>
        </body></html>
    """.trimIndent()
}
