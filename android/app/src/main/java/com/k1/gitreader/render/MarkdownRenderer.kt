package com.k1.gitreader.render

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vdurmont.emoji.EmojiParser
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
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
 * Markwon の配色をリポ毎テーマに連動させるための色(ARGB int)。
 * コードフェンスの背景は Prism4j 側テーマが受け持つため、ここではインラインコード・
 * リンク・引用バー・区切り線など Prism 管轄外の要素を扱う。
 */
data class MarkdownColors(
    val link: Int,
    val inlineCodeBg: Int,
    val inlineCodeText: Int,
    val blockQuoteBar: Int,
    val divider: Int,
)

/**
 * Markwon ベースの Markdown レンダラ。
 * GFM(テーブル/打消し/タスクリスト)・HTML・リンク自動化・画像表示・絵文字(:smile:)に対応。
 * リポジトリ内の相対画像は file:// 絶対パスへ解決する。
 * ```mermaid ブロックは splitBlocks で切り出し、MermaidWebView 側で描画する。
 * コードフェンス(```lang)は Prism4j(SyntaxHighlightPlugin)でハイライトする。
 */
object MarkdownRenderer {

    /**
     * dark = true のときダーク配色テーマでコードフェンスをハイライトする。
     * linkResolver を渡すと相対リンクのアプリ内遷移など独自のリンク処理に差し替える。
     * colors を渡すとリンク色・インラインコード・引用・区切り線をテーマ連動させる。
     */
    fun create(
        context: Context,
        dark: Boolean,
        linkResolver: LinkResolver? = null,
        colors: MarkdownColors? = null,
    ): Markwon =
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
            .apply {
                if (linkResolver != null) {
                    usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver(linkResolver)
                        }
                    })
                }
                if (colors != null) {
                    usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            builder
                                .linkColor(colors.link)
                                .codeBackgroundColor(colors.inlineCodeBg)
                                .codeTextColor(colors.inlineCodeText)
                                .blockQuoteColor(colors.blockQuoteBar)
                                .thematicBreakColor(colors.divider)
                                .headingBreakColor(colors.divider)
                        }
                    })
                }
            }
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

    /** スキーム/絶対パス/アンカーでない(=リポ内ファイルを指しうる)相対参照か。 */
    internal fun isRelative(dest: String): Boolean {
        if (dest.startsWith("#") || dest.startsWith("/")) return false
        if (dest.startsWith("data:")) return false
        return !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(dest)
    }
}

/**
 * Markdown 内のリンクタップを処理する LinkResolver。
 * 相対リンクがリポジトリ内のファイルを指す場合はアプリ内遷移(onFile に repo ルート相対パスを渡す)、
 * それ以外(http(s)/mailto 等)は既定動作(ブラウザ等で開く)に委譲する。
 *
 * @param baseDir 表示中ファイルのあるディレクトリ(相対解決の基点)
 * @param workDir リポジトリのルート(=遷移パスの基点)
 */
class RepoLinkResolver(
    private val baseDir: File,
    private val workDir: File,
    private val onFile: (String) -> Unit,
) : LinkResolver {

    private val fallback = LinkResolverDef()

    override fun resolve(view: View, link: String) {
        // 同一ドキュメント内アンカーは未対応(何もしない)
        if (link.startsWith("#")) return
        if (!MarkdownRenderer.isRelative(link)) {
            // http(s)/mailto/絶対パス等は既定動作に委譲
            fallback.resolve(view, link)
            return
        }
        val path = resolveRepoRelativePath(baseDir, workDir, link)
        // リポ外/存在しない相対リンクは黙って無視(外部 intent でクラッシュさせない)
        if (path != null) onFile(path)
    }

    companion object {
        /**
         * 相対リンクをリポ内ファイルへ解決し、repo ルート相対パス(区切りは '/')を返す。
         * 解決できない(スキーム付き/リポ外/存在しない/ディレクトリ)場合は null。
         */
        fun resolveRepoRelativePath(baseDir: File, workDir: File, link: String): String? {
            if (!MarkdownRenderer.isRelative(link)) return null
            val clean = link.substringBefore('#').substringBefore('?')
            if (clean.isEmpty()) return null
            val target = File(baseDir, clean).normalize()
            val rel = target.relativeToOrNull(workDir.normalize()) ?: return null
            if (rel.path.startsWith("..")) return null
            if (!target.isFile) return null
            return rel.path.replace('\\', '/')
        }
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
 * workDir はリポジトリのルート。相対リンクがリポ内ファイルを指す場合は
 * onNavigateToFile(repo ルート相対パス)でアプリ内遷移する。
 */
@Composable
fun MarkdownView(
    markdown: String,
    baseDir: File,
    workDir: File,
    textColor: Int,
    dark: Boolean,
    onNavigateToFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // コールバックの最新参照を保持(Markwon は再生成せず、resolver から間接参照する)
    val latestNavigate by rememberUpdatedState(onNavigateToFile)
    val scheme = MaterialTheme.colorScheme
    val colors = MarkdownColors(
        link = scheme.primary.toArgb(),
        inlineCodeBg = scheme.surfaceVariant.toArgb(),
        inlineCodeText = scheme.onSurfaceVariant.toArgb(),
        blockQuoteBar = scheme.outline.toArgb(),
        divider = scheme.outlineVariant.toArgb(),
    )
    val markwon = remember(context, dark, baseDir.path, workDir.path, colors) {
        val resolver = RepoLinkResolver(baseDir, workDir) { latestNavigate(it) }
        MarkdownRenderer.create(context, dark, resolver, colors)
    }
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
