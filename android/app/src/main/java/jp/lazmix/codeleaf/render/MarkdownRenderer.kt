package jp.lazmix.codeleaf.render

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vdurmont.emoji.EmojiParser
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
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
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.parser.Parser
import java.io.File

/** Markdown を構成するブロック（Mermaid/テーブルは専用描画する）。 */
sealed interface MdBlock {
    data class Text(val markdown: String) : MdBlock
    data class Mermaid(val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

/** YAML フロントマターの 1 項目（表示用に key と整形済み value を保持）。 */
data class FrontmatterEntry(val key: String, val value: String)

/** Markdown 見出し（ATX 形式）。level=1..6。 */
data class Heading(val level: Int, val text: String)

/** 目次スクロール用に、先頭見出しでドキュメントを区切ったセクション。heading=null は前文。 */
data class MdSection(val heading: Heading?, val markdown: String)

/**
 * Markwon の配色をリポ毎テーマに連動させるための色(ARGB int)。
 * コードフェンスのトークン色は Prism4j がハイライトするが、ブロックの背景・余白・既定文字色は
 * Markwon 既定(薄グレー・余白なし)だと「のっぺり」見えるため、ここでテーマ連動値を与える。
 */
data class MarkdownColors(
    val link: Int,
    val inlineCodeBg: Int,
    val inlineCodeText: Int,
    val blockQuoteBar: Int,
    val divider: Int,
    val codeBlockBg: Int = 0,
    val codeBlockText: Int = 0,
)

/**
 * Markwon ベースの Markdown レンダラ。
 * GFM(テーブル/打消し/タスクリスト)・HTML・画像表示・絵文字(:smile:)に対応。
 * 自動リンクは CommonMark autolink 拡張(scheme 付き URL/www./メールのみ)を使い、
 * Linkify による誤リンク(日付/"*.md"/日本語巻き込み)を避ける。
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
            .usePlugin(SyntaxHighlightPlugin.create(CodeHighlight.prism4j, CodeHighlight.theme(dark)))
            // 自動リンクは Android Linkify(ALL)ではなく CommonMark の autolink 拡張を使う。
            // Linkify は日付っぽい数字を電話番号、"CLAUDE.md"(.md は実在 TLD)を Web URL とみなし
            // 日本語まで巻き込んで誤リンク化した。autolink 拡張は scheme 付き URL / www. / メールだけを
            // 対象にするため誤検出しない(相対 .md 等は明示 [text](url) のときだけ RepoLinkResolver で遷移)。
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureParser(builder: Parser.Builder) {
                    builder.extensions(listOf(AutolinkExtension.create()))
                }
            })
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
                            val density = context.resources.displayMetrics.density
                            builder
                                .linkColor(colors.link)
                                .codeBackgroundColor(colors.inlineCodeBg)
                                .codeTextColor(colors.inlineCodeText)
                                // コードフェンス(<pre>)の背景・既定文字色・余白をテーマ連動で。
                                .codeBlockBackgroundColor(colors.codeBlockBg)
                                .codeBlockTextColor(colors.codeBlockText)
                                .codeBlockMargin((12 * density).toInt())
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

    /**
     * Mermaid フェンスでドキュメントをブロック列に分割する。
     * extractTables=true のとき、GFM テーブルも MdBlock.Table として切り出す。
     */
    fun splitBlocks(markdown: String, extractTables: Boolean = false): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        var last = 0
        for (m in mermaidFence.findAll(markdown)) {
            val pre = markdown.substring(last, m.range.first)
            if (pre.isNotBlank()) blocks.addAll(textOrTables(pre, extractTables))
            blocks.add(MdBlock.Mermaid(m.groupValues[1]))
            last = m.range.last + 1
        }
        val tail = markdown.substring(last)
        if (tail.isNotBlank()) blocks.addAll(textOrTables(tail, extractTables))
        if (blocks.isEmpty()) blocks.add(MdBlock.Text(markdown))
        return blocks
    }

    private fun textOrTables(md: String, extractTables: Boolean): List<MdBlock> =
        if (extractTables) splitTables(md) else listOf(MdBlock.Text(md))

    private val tableDelimiter =
        Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$""")

    /** GFM テーブル(ヘッダ行 + 区切り行 + 本文行)を MdBlock.Table として切り出す。フェンス内は無視。 */
    private fun splitTables(md: String): List<MdBlock> {
        val lines = md.split("\n")
        val out = ArrayList<MdBlock>()
        val buf = StringBuilder()
        var inFence = false
        fun flush() { if (buf.isNotBlank()) out.add(MdBlock.Text(buf.toString())); buf.setLength(0) }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence; buf.append(line).append('\n'); i++; continue
            }
            val isTableStart = !inFence && line.contains('|') && line.isNotBlank() &&
                i + 1 < lines.size && tableDelimiter.matches(lines[i + 1])
            if (isTableStart) {
                flush()
                val header = parseTableCells(line)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && !lines[i].trimStart().startsWith("```") &&
                    lines[i].contains('|') && lines[i].isNotBlank()
                ) {
                    rows.add(parseTableCells(lines[i])); i++
                }
                out.add(MdBlock.Table(header, rows))
            } else {
                buf.append(line).append('\n'); i++
            }
        }
        flush()
        return out.ifEmpty { listOf(MdBlock.Text(md)) }
    }

    private fun parseTableCells(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split("|").map { it.trim() }
    }

    /**
     * 先頭の YAML フロントマター(`---` で囲まれたブロック)を抽出する。
     * 見つかれば (項目リスト, フロントマターを除いた本文) を、無ければ (null, 元のまま) を返す。
     * 完全な YAML ではなく、ドキュメントで多い top-level の `key: value` と
     * その下のブロックリスト(`- item`)を簡易にパースする。
     */
    fun extractFrontmatter(markdown: String): Pair<List<FrontmatterEntry>?, String> {
        val text = markdown.removePrefix("\uFEFF")
        val lines = text.split("\n")
        if (lines.isEmpty() || lines[0].trim() != "---") return null to markdown

        var closing = -1
        for (i in 1 until lines.size) {
            val t = lines[i].trim()
            if (t == "---" || t == "...") { closing = i; break }
        }
        if (closing < 0) return null to markdown // 閉じが無ければフロントマター扱いしない

        val entries = ArrayList<FrontmatterEntry>()
        for (i in 1 until closing) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            val indented = raw[0].isWhitespace() || raw.trimStart().startsWith("- ")
            val colon = raw.indexOf(':')
            if (!indented && colon > 0) {
                val key = raw.substring(0, colon).trim()
                val value = unquote(raw.substring(colon + 1).trim())
                entries.add(FrontmatterEntry(key, value))
            } else if (entries.isNotEmpty()) {
                // リスト項目・継続行は直前のキーに連結する
                val add = unquote(raw.trim().removePrefix("- ").trim())
                if (add.isNotEmpty()) {
                    val last = entries.removeAt(entries.lastIndex)
                    val merged = if (last.value.isBlank()) add else "${last.value}, $add"
                    entries.add(last.copy(value = merged))
                }
            }
        }

        val body = lines.subList(closing + 1, lines.size).joinToString("\n").trimStart('\n')
        return (if (entries.isEmpty()) null else entries) to body
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && (s.first() == '"' && s.last() == '"' || s.first() == '\'' && s.last() == '\'')) {
            s.substring(1, s.length - 1)
        } else {
            s
        }

    private val atxHeading = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")

    /**
     * ATX 見出し(`#`〜`######`)の行で本文をセクションに分割する(目次スクロール用)。
     * フェンスドコードブロック内の `#` は見出し扱いしない。先頭見出しより前は heading=null。
     */
    fun splitIntoSections(markdown: String): List<MdSection> {
        val sections = ArrayList<MdSection>()
        val cur = StringBuilder()
        var curHeading: Heading? = null
        var inFence = false

        fun flush() {
            if (cur.isNotEmpty() || curHeading != null) {
                sections.add(MdSection(curHeading, cur.toString()))
            }
        }

        for (line in markdown.split("\n")) {
            if (line.trimStart().startsWith("```")) inFence = !inFence
            val h = if (!inFence) atxHeading.matchEntire(line)?.let {
                Heading(it.groupValues[1].length, it.groupValues[2].trim())
            } else {
                null
            }
            if (h != null) {
                flush()
                cur.setLength(0)
                curHeading = h
            }
            cur.append(line).append('\n')
        }
        flush()
        if (sections.isEmpty()) sections.add(MdSection(null, markdown))
        return sections
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

/** 相対リンクの解決先(リポ ルート相対パス・区切りは '/')。 */
sealed interface RepoTarget {
    val path: String
    /** リポ内の既存ファイル(ビューアで開く)。 */
    data class FileTarget(override val path: String) : RepoTarget
    /** リポ内の既存ディレクトリ(ファイルブラウザで開く)。ルートは path="" 。 */
    data class DirTarget(override val path: String) : RepoTarget
}

/**
 * Markdown 内のリンクタップを処理する LinkResolver。
 * 相対リンクがリポジトリ内のファイル/ディレクトリを指す場合はアプリ内遷移
 * (onFile/onDir に repo ルート相対パスを渡す)、それ以外(http(s)/mailto 等)は既定動作に委譲する。
 *
 * @param baseDir 表示中ファイルのあるディレクトリ(相対解決の基点)
 * @param workDir リポジトリのルート(=遷移パスの基点)
 */
class RepoLinkResolver(
    private val baseDir: File,
    private val workDir: File,
    private val onFile: (String) -> Unit,
    private val onDir: (String) -> Unit,
    private val onExternal: (String) -> Unit,
) : LinkResolver {

    override fun resolve(view: View, link: String) {
        // 同一ドキュメント内アンカーは未対応(何もしない)
        if (link.startsWith("#")) return
        if (!MarkdownRenderer.isRelative(link)) {
            // http(s)/mailto/絶対パス等は外部リンク扱い(開き方は呼び出し側=設定で決定)
            onExternal(link)
            return
        }
        // リポ外/存在しない相対リンクは黙って無視(外部 intent でクラッシュさせない)
        when (val t = resolveRepoTarget(baseDir, workDir, link)) {
            is RepoTarget.FileTarget -> onFile(t.path)
            is RepoTarget.DirTarget -> onDir(t.path)
            null -> Unit
        }
    }

    companion object {
        /**
         * 相対リンクをリポ内のファイル/ディレクトリへ解決する。
         * 解決できない(スキーム付き/リポ外/存在しない)場合は null。
         * リンク先は `%XX`/`<...>` でエンコードされ得るためパーセントデコードしてから実体を判定する。
         */
        fun resolveRepoTarget(baseDir: File, workDir: File, link: String): RepoTarget? {
            if (!MarkdownRenderer.isRelative(link)) return null
            val clean = decodePercent(link.substringBefore('#').substringBefore('?'))
            if (clean.isEmpty()) return null
            val target = File(baseDir, clean).normalize()
            val rel = target.relativeToOrNull(workDir.normalize()) ?: return null
            if (rel.path.startsWith("..")) return null
            val relPath = rel.path.replace('\\', '/')
            return when {
                target.isFile -> RepoTarget.FileTarget(relPath)
                // ディレクトリは末尾 '/' を落とす(ルートは "" のまま=ブラウザのトップ)。
                target.isDirectory -> RepoTarget.DirTarget(relPath.trimEnd('/'))
                else -> null
            }
        }

        /**
         * 相対リンクをリポ内ファイルへ解決し、repo ルート相対パス(区切りは '/')を返す。
         * ファイル以外(ディレクトリ/スキーム付き/リポ外/存在しない)は null。
         */
        fun resolveRepoRelativePath(baseDir: File, workDir: File, link: String): String? =
            (resolveRepoTarget(baseDir, workDir, link) as? RepoTarget.FileTarget)?.path

        /**
         * URL のパーセントエンコードのみ復号する(`%20` 等)。
         * URLDecoder は `+` を空白に変えてしまう(form 用セマンティクス・パスでは誤り)ため
         * リテラル `+` を一旦退避してから復号する。不正な `%` 列は復号せず原文を返す。
         */
        private fun decodePercent(s: String): String {
            if (!s.contains('%')) return s
            return try {
                java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                s
            }
        }
    }
}

/**
 * Compose から Markdown テキストブロックを表示する。baseDir は対象 .md があるディレクトリ。
 * textColor は現在テーマの onSurface 色（AndroidView の TextView は Compose テーマを継承しないため明示指定）。
 * dark はコードフェンスのハイライト配色(ダーク/ライト)を選ぶ。
 * workDir はリポジトリのルート。相対リンクがリポ内ファイル/ディレクトリを指す場合は
 * onNavigateToFile / onNavigateToDir(repo ルート相対パス)でアプリ内遷移する。
 */
@Composable
fun MarkdownView(
    markdown: String,
    baseDir: File,
    workDir: File,
    textColor: Int,
    dark: Boolean,
    fontScale: Float,
    onNavigateToFile: (String) -> Unit,
    onNavigateToDir: (String) -> Unit,
    onExternalLink: (String) -> Unit,
    /** このブロックを長押ししたとき呼ぶ(整形 Markdown でのメモ追加)。null なら無効。 */
    onLongPress: (() -> Unit)? = null,
    /** テキスト選択を有効にするか(選択モード)。ON のとき長押しメモは無効・リンクは維持。 */
    selectable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // コールバックの最新参照を保持(Markwon は再生成せず、resolver から間接参照する)
    val latestNavigate by rememberUpdatedState(onNavigateToFile)
    val latestNavigateDir by rememberUpdatedState(onNavigateToDir)
    val latestExternal by rememberUpdatedState(onExternalLink)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val scheme = MaterialTheme.colorScheme
    val colors = MarkdownColors(
        link = scheme.primary.toArgb(),
        inlineCodeBg = scheme.surfaceVariant.toArgb(),
        inlineCodeText = scheme.onSurfaceVariant.toArgb(),
        blockQuoteBar = scheme.outline.toArgb(),
        divider = scheme.outlineVariant.toArgb(),
        codeBlockBg = scheme.surfaceVariant.toArgb(),
        codeBlockText = scheme.onSurface.toArgb(),
    )
    val markwon = remember(context, dark, baseDir.path, workDir.path, colors) {
        val resolver = RepoLinkResolver(
            baseDir = baseDir,
            workDir = workDir,
            onFile = { latestNavigate(it) },
            onDir = { latestNavigateDir(it) },
            onExternal = { latestExternal(it) },
        )
        MarkdownRenderer.create(context, dark, resolver, colors)
    }
    val rendered = remember(markdown, baseDir.path) {
        MarkdownRenderer.preprocess(markdown, baseDir)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx) },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.textSize = MARKDOWN_BASE_SP * fontScale
            markwon.setMarkdown(tv, rendered)
            // 選択モード: 本文を選択可能に。MovementMethod は選択用に置換されるので、
            // リンクも生かす SelectableLinkMovementMethod を使う。長押しは選択に使うのでメモは無効。
            // 非選択モード: 従来どおりリンク(LinkMovementMethod)＋長押しメモ。
            tv.setTextIsSelectable(selectable)
            tv.movementMethod =
                if (selectable) SelectableLinkMovementMethod.getInstance() else LinkMovementMethod.getInstance()
            tv.setOnLongClickListener(
                if (selectable) {
                    null
                } else {
                    View.OnLongClickListener { latestLongPress?.invoke(); latestLongPress != null }
                },
            )
        },
    )
}

/**
 * 選択と相対/外部リンクのタップを両立する MovementMethod。
 * ArrowKeyMovementMethod(選択)を基底に、タップ(選択が無い ACTION_UP)が ClickableSpan 上なら onClick する。
 * ドラッグ選択の終了(選択あり)ではリンクを発火しない。
 */
private class SelectableLinkMovementMethod : ArrowKeyMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP &&
            Selection.getSelectionStart(buffer) == Selection.getSelectionEnd(buffer)
        ) {
            val layout = widget.layout
            if (layout != null) {
                val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                val off = layout.getOffsetForHorizontal(layout.getLineForVertical(y), x.toFloat())
                val links = buffer.getSpans(off, off, ClickableSpan::class.java)
                if (links.isNotEmpty()) {
                    links[0].onClick(widget)
                    return true
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private val instance = SelectableLinkMovementMethod()
        fun getInstance() = instance
    }
}

/** Markdown / コード本文の基準フォントサイズ(sp)。fontScale を掛けて適用する。 */
private const val MARKDOWN_BASE_SP = 16f
private const val CODE_BASE_SP = 14f

/** Prism4j の Spanned(ForegroundColorSpan)を Compose の AnnotatedString に変換する。 */
private fun spannedToAnnotatedString(cs: CharSequence): AnnotatedString {
    if (cs !is Spanned) return AnnotatedString(cs.toString())
    return buildAnnotatedString {
        append(cs.toString())
        for (sp in cs.getSpans(0, cs.length, ForegroundColorSpan::class.java)) {
            addStyle(SpanStyle(color = Color(sp.foregroundColor)), cs.getSpanStart(sp), cs.getSpanEnd(sp))
        }
    }
}

/**
 * ソースコード/プレーンテキストを行単位の LazyColumn で表示する。
 * 各行を Prism4j でハイライト(language=null なら無装飾)し、巨大ファイルは無装飾。
 * highlightLine(0始まり)を渡すとその行へスクロールし背景強調する(検索の行ジャンプ用)。
 * wrap=false なら各行を折り返さず、リスト全体を横スクロールできる。
 */
@Composable
fun CodeView(
    code: String,
    language: String?,
    dark: Boolean,
    fontScale: Float,
    highlightLine: Int? = null,
    wrap: Boolean = true,
    showLineNumbers: Boolean = false,
    /** 行(0始まり)を長押ししたとき呼ぶ。メモ追加の行選択に使う。 */
    onLineLongPress: ((Int) -> Unit)? = null,
    /** 選択中の行範囲(0始まり)。背景強調する。 */
    selectedLines: IntRange? = null,
    modifier: Modifier = Modifier,
) {
    val theme = remember(dark) { CodeHighlight.theme(dark) }
    val lines = remember(code, language, dark) {
        val raw = code.split("\n")
        if (code.length > 200_000) raw.map { AnnotatedString(it) }
        else raw.map { spannedToAnnotatedString(CodeHighlight.highlight(language, it, dark)) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(highlightLine, lines.size) {
        if (highlightLine != null && highlightLine in lines.indices) {
            listState.scrollToItem(highlightLine)
        }
    }
    val baseColor = Color(theme.textColor())
    val gutterColor = baseColor.copy(alpha = 0.5f)
    val highlightBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
    val fontSize = (CODE_BASE_SP * fontScale).sp
    // 行番号ガター幅は最大桁数から算出。
    val gutterWidth = ((lines.size.toString().length) * 9 + 12).dp
    val hScroll = rememberScrollState()
    val listModifier = modifier
        .background(Color(theme.background()))
        .let { if (wrap) it else it.horizontalScroll(hScroll) }
    LazyColumn(state = listState, modifier = listModifier.testTag("codeContent")) {
        itemsIndexed(lines) { idx, line ->
            val emphasized = idx == highlightLine || selectedLines?.contains(idx) == true
            val rowBg = if (emphasized) Modifier.background(highlightBg) else Modifier
            // 長押しでその行をメモ追加の起点にする(タップは無反応・縦スクロールは阻害しない)。
            val rowPress = if (onLineLongPress != null) {
                Modifier.pointerInput(idx) { detectTapGestures(onLongPress = { onLineLongPress(idx) }) }
            } else {
                Modifier
            }
            if (showLineNumbers) {
                Row(
                    Modifier
                        .then(if (wrap) Modifier.fillMaxWidth() else Modifier)
                        .then(rowBg)
                        .then(rowPress)
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${idx + 1}",
                        color = gutterColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(gutterWidth).padding(end = 8.dp),
                    )
                    Text(
                        text = line,
                        color = baseColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize,
                        softWrap = wrap,
                        maxLines = if (wrap) Int.MAX_VALUE else 1,
                        modifier = if (wrap) Modifier.weight(1f) else Modifier,
                    )
                }
            } else {
                Text(
                    text = line,
                    color = baseColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    softWrap = wrap,
                    maxLines = if (wrap) Int.MAX_VALUE else 1,
                    modifier = Modifier
                        .then(if (wrap) Modifier.fillMaxWidth() else Modifier)
                        .then(rowBg)
                        .then(rowPress)
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                )
            }
        }
    }
}
