package com.k1.gitreader.render

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vdurmont.emoji.EmojiParser
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.image.network.NetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import java.io.File

/** Markdown を構成するブロック（Mermaid だけは WebView で別描画する）。 */
sealed interface MdBlock {
    data class Text(val markdown: String) : MdBlock
    data class Mermaid(val code: String) : MdBlock
}

/**
 * Markwon ベースの Markdown レンダラ。
 * GFM(テーブル/打消し/タスクリスト)・HTML・リンク自動化・画像表示・絵文字(:smile:)に対応。
 * リポジトリ内の相対画像は file:// 絶対パスへ解決する。
 * ```mermaid ブロックは splitBlocks で切り出し、MermaidWebView 側で描画する。
 * コードフェンス(```lang)は Prism4j(SyntaxHighlightPlugin)でハイライトする。
 */
object MarkdownRenderer {

    /** dark = true のときダーク配色テーマでコードフェンスをハイライトする。 */
    fun create(context: Context, dark: Boolean): Markwon =
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(CodeHighlight.prism4j, CodeHighlight.theme(dark)))
            .usePlugin(
                ImagesPlugin.create { plugin ->
                    plugin.addSchemeHandler(FileSchemeHandler.create())
                    plugin.addSchemeHandler(NetworkSchemeHandler.create())
                },
            )
            .build()

    private val mermaidFence =
        Regex("""(?ms)^[ \t]*```[ \t]*mermaid[ \t]*\r?\n(.*?)\r?\n[ \t]*```[ \t]*$""")

    private val imageRegex = Regex("""!\[([^\]]*)]\(\s*([^)\s]+)([^)]*)\)""")

    /** Mermaid フェンスでドキュメントをブロック列に分割する。 */
    fun splitBlocks(markdown: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        var last = 0
        for (m in mermaidFence.findAll(markdown)) {
            val pre = markdown.substring(last, m.range.first)
            if (pre.isNotBlank()) blocks.add(MdBlock.Text(pre))
            blocks.add(MdBlock.Mermaid(m.groupValues[1]))
            last = m.range.last + 1
        }
        val tail = markdown.substring(last)
        if (tail.isNotBlank()) blocks.add(MdBlock.Text(tail))
        if (blocks.isEmpty()) blocks.add(MdBlock.Text(markdown))
        return blocks
    }

    /** 絵文字 shortcode を unicode 化し、相対画像を file:// へ解決する。 */
    fun preprocess(markdown: String, baseDir: File): String =
        resolveImagePaths(EmojiParser.parseToUnicode(markdown), baseDir)

    private fun resolveImagePaths(markdown: String, baseDir: File): String =
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
        return !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(dest)
    }
}

/**
 * 非 Markdown ファイルのコードハイライト用ヘルパ。
 * Prism4j(GrammarLocatorDef は prism4j-bundler が生成)を共有し、Markdown フェンス用と
 * コードファイル全体表示用の双方で同じ文法・テーマを使う。
 */
object CodeHighlight {

    /** GrammarLocatorDef は kapt(prism4j-bundler)が PrismGrammarLocator から生成する。 */
    val prism4j: Prism4j by lazy { Prism4j(GrammarLocatorDef()) }

    fun theme(dark: Boolean): Prism4jTheme =
        if (dark) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create()

    /** language が null/未対応の場合は素のテキスト(span なし)が返る。 */
    fun highlight(language: String?, code: String, dark: Boolean): CharSequence =
        Prism4jSyntaxHighlight.create(prism4j, theme(dark)).highlight(language ?: "", code)

    /** ファイル名(拡張子)から Prism4j の言語 ID を推定する。未対応は null。 */
    fun languageForFile(name: String): String? =
        when (name.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "groovy", "gradle" -> "groovy"
            "scala", "sc" -> "scala"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
            "cs" -> "csharp"
            "js", "mjs", "cjs", "jsx" -> "javascript"
            "json" -> "json"
            "css" -> "css"
            "html", "htm", "xml", "xhtml", "svg" -> "markup"
            "py" -> "python"
            "go" -> "go"
            "sql" -> "sql"
            "yml", "yaml" -> "yaml"
            "swift" -> "swift"
            "dart" -> "dart"
            "md", "markdown" -> "markdown"
            else -> null
        }
}

/**
 * Compose から Markdown テキストブロックを表示する。baseDir は対象 .md があるディレクトリ。
 * textColor は現在テーマの onSurface 色（AndroidView の TextView は Compose テーマを継承しないため明示指定）。
 * dark はコードフェンスのハイライト配色(ダーク/ライト)を選ぶ。
 */
@Composable
fun MarkdownView(
    markdown: String,
    baseDir: File,
    textColor: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context, dark) { MarkdownRenderer.create(context, dark) }
    val rendered = remember(markdown, baseDir.path) {
        MarkdownRenderer.preprocess(markdown, baseDir)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx).apply { setTextIsSelectable(true) } },
        update = { tv ->
            tv.setTextColor(textColor)
            markwon.setMarkdown(tv, rendered)
        },
    )
}

/** 非 Markdown のソースコードを Prism4j でハイライト表示する。巨大ファイルは素のまま表示。 */
@Composable
fun CodeView(code: String, language: String?, dark: Boolean, modifier: Modifier = Modifier) {
    val theme = remember(dark) { CodeHighlight.theme(dark) }
    val rendered: CharSequence = remember(code, language, dark) {
        if (code.length > 200_000) code else CodeHighlight.highlight(language, code, dark)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
        },
        update = { tv ->
            tv.setBackgroundColor(theme.background())
            tv.setTextColor(theme.textColor())
            tv.text = rendered
        },
    )
}
