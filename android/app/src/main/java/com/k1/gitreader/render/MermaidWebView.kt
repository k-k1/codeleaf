package com.k1.gitreader.render

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * ```mermaid ブロックを、assets 同梱の mermaid.min.js でオフライン描画する WebView。
 * 高さは v1 では固定。テーマ(dark/default)はリポ毎テーマに連動。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(code: String, dark: Boolean, modifier: Modifier = Modifier) {
    val html = remember(code, dark) { buildHtml(code, dark) }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(360.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { wv ->
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        },
    )
}

private fun buildHtml(code: String, dark: Boolean): String {
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
        <script>mermaid.initialize({startOnLoad:true, theme:'$theme', securityLevel:'loose'});</script>
        </body></html>
    """.trimIndent()
}
