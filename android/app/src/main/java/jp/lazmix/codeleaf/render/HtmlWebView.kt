package jp.lazmix.codeleaf.render

import android.graphics.Color
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * HTML ファイルをプレビュー描画する WebView。Markdown の整形表示に相当する「見た目」表示で、
 * ⋮ から Raw(ソース)表示へ切り替えられる。
 *
 * 読み取り専用リーダのため **JavaScript は無効**にする(リポ内スクリプトの実行と、file スキーム
 * からの XHR によるローカルファイル読み出し/外部送信を防ぐ)。相対リソース(画像/CSS)は [baseDir]
 * を baseURL に据えて解決するが、JS 無効のため取得経路は静的読み込みに限られる。
 * 高さは自己測定せず、ビューア領域いっぱい(WebView 内スクロール)で表示する。
 */
@Composable
fun HtmlWebView(html: String, baseDir: File, dark: Boolean, modifier: Modifier = Modifier) {
    val doc = styledHtml(html, dark)
    // baseURL は当該ファイルのディレクトリ(末尾 / が必要)。相対 img/link を解決する。
    val base = "file://${baseDir.absolutePath}/"
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                @Suppress("SetJavaScriptEnabled")
                settings.javaScriptEnabled = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { wv ->
            wv.loadDataWithBaseURL(base, doc, "text/html", "utf-8", null)
        },
    )
}

/**
 * 描画用の HTML を組み立てる。既定の背景/文字色・画像の最大幅などの下地スタイルを `<head>` 先頭に
 * 差し込み(ドキュメント自身の CSS が後勝ちで上書きできる)、`<head>` が無い断片は全体を包む。
 */
internal fun styledHtml(raw: String, dark: Boolean): String {
    val fg = if (dark) "#e6e6e6" else "#1a1a1a"
    val bg = if (dark) "#121212" else "#ffffff"
    val style = """<style>html{color:$fg;background:$bg;-webkit-text-size-adjust:100%;}""" +
        """body{margin:12px;overflow-wrap:break-word;}img,video,table{max-width:100%;height:auto;}</style>"""
    val headIdx = raw.indexOf("<head>", ignoreCase = true)
    return if (headIdx >= 0) {
        val at = headIdx + "<head>".length
        raw.substring(0, at) + style + raw.substring(at)
    } else {
        "<!doctype html><html><head>" +
            """<meta name="viewport" content="width=device-width, initial-scale=1">""" +
            style + "</head><body>" + raw + "</body></html>"
    }
}
