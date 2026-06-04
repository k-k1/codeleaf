package com.k1.gitreader.render

import android.content.Context
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.image.network.NetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.File

/**
 * Markwon ベースの Markdown レンダラ。GFM(テーブル/打消し/タスクリスト)・HTML・
 * リンク自動化・画像表示に対応。リポジトリ内の相対画像は file:// 絶対パスへ解決する。
 *
 * Mermaid / 絵文字 / シンタックスハイライト / リポ毎テーマ連動は後続スライスで追加予定。
 */
object MarkdownRenderer {

    fun create(context: Context): Markwon =
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(
                ImagesPlugin.create { plugin ->
                    plugin.addSchemeHandler(FileSchemeHandler.create())
                    plugin.addSchemeHandler(NetworkSchemeHandler.create())
                },
            )
            .build()

    private val imageRegex = Regex("""!\[([^\]]*)]\(\s*([^)\s]+)([^)]*)\)""")

    /** Markdown 内の相対画像 src を baseDir 基準の file:// 絶対 URI に書き換える。 */
    fun resolveImagePaths(markdown: String, baseDir: File): String =
        imageRegex.replace(markdown) { m ->
            val alt = m.groupValues[1]
            val dest = m.groupValues[2]
            val tail = m.groupValues[3]
            if (isRelative(dest)) {
                val abs = File(baseDir, dest).normalize().absolutePath
                "![$alt](file://$abs$tail)"
            } else {
                m.value
            }
        }

    private fun isRelative(dest: String): Boolean {
        if (dest.startsWith("#") || dest.startsWith("/")) return false
        if (dest.startsWith("data:")) return false
        // scheme:// を持つものは絶対
        return !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(dest)
    }
}

/** Compose から Markdown を表示する。baseDir は対象 .md があるディレクトリ。 */
@Composable
fun MarkdownView(markdown: String, baseDir: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) { MarkdownRenderer.create(context) }
    val rendered = remember(markdown, baseDir.path) {
        MarkdownRenderer.resolveImagePaths(markdown, baseDir)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx).apply { setTextIsSelectable(true) } },
        update = { tv -> markwon.setMarkdown(tv, rendered) },
    )
}
