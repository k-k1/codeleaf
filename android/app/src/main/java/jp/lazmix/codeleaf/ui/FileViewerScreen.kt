package jp.lazmix.codeleaf.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.lazmix.codeleaf.data.LinkOpenMode
import jp.lazmix.codeleaf.data.TableMode
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.render.CodeHighlight
import jp.lazmix.codeleaf.render.CodeView
import jp.lazmix.codeleaf.render.FrontmatterEntry
import jp.lazmix.codeleaf.render.Heading
import jp.lazmix.codeleaf.render.MarkdownRenderer
import jp.lazmix.codeleaf.render.MarkdownView
import jp.lazmix.codeleaf.render.MdBlock
import jp.lazmix.codeleaf.render.MdSection
import jp.lazmix.codeleaf.render.MermaidWebView
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    repo: Repo,
    filePath: String,
    workDir: File,
    loadText: suspend () -> String,
    fontScale: Float,
    defaultWrap: Boolean = true,
    linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    showLineNumbers: Boolean = false,
    tableMode: TableMode = TableMode.INLINE,
    stickyHeadings: Boolean = true,
    targetLine: Int? = null,
    onHistory: () -> Unit,
    onNavigateToFile: (String) -> Unit,
    onBack: () -> Unit,
    /** 戻る矢印を表示するか。2/3ペインでは一覧が常に見えるため非表示にする。 */
    showBack: Boolean = true,
) {
    var text by remember(filePath) { mutableStateOf<String?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }
    // 検索の行ジャンプで開いた場合は、行が分かる Raw 表示で開始する。
    var raw by remember(filePath) { mutableStateOf(targetLine != null) }
    var wrap by remember(filePath) { mutableStateOf(defaultWrap) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(repo.id, filePath) {
        error = null
        text = runCatching { loadText() }.getOrElse { error = it.message; null }
    }

    val isMarkdown = filePath.endsWith(".md", true) || filePath.endsWith(".markdown", true)
    val fileName = filePath.substringAfterLast('/')
    val parent = filePath.substringBeforeLast('/', "")
    val baseDir = if (parent.isEmpty()) workDir else File(workDir, parent)

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // 外部リンク(http/https)の開き方は設定に従う(アプリ内 Custom Tabs / 外部ブラウザ)。
    val context = LocalContext.current
    val openExternal: (String) -> Unit = remember(linkOpenMode) {
        { url ->
            runCatching {
                val uri = Uri.parse(url)
                if (linkOpenMode == LinkOpenMode.IN_APP) {
                    CustomTabsIntent.Builder().build().launchUrl(context, uri)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    // 目次スクロール用: 整形ビューのスクロール状態と各セクションの Y 位置(px)。
    val scope = rememberCoroutineScope()
    val scrollState = remember(filePath) { ScrollState(0) }
    val sectionTops = remember(filePath) { mutableStateMapOf<Int, Int>() }
    var showToc by remember(filePath) { mutableStateOf(false) }
    // スクロールビューポート上端(root座標, px)。テーブルのヘッダ固定の基準。
    var viewportTopPx by remember(filePath) { mutableFloatStateOf(0f) }
    // スティッキー見出しオーバーレイの高さ(px)。テーブルヘッダはこの分だけ下に固定する。
    var stickyHeadingsHeightPx by remember(filePath) { mutableIntStateOf(0) }

    // フロントマター抽出 + 見出しセクション分割(整形 Markdown のときのみ)。
    val mdModel = remember(text, isMarkdown) {
        val b = text
        if (b != null && isMarkdown) {
            val (fm, content) = MarkdownRenderer.extractFrontmatter(b)
            fm to MarkdownRenderer.splitIntoSections(content)
        } else {
            null
        }
    }
    val tocEntries = remember(mdModel) {
        mdModel?.second?.mapIndexedNotNull { i, s -> s.heading?.let { TocEntry(i, it) } } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // どのフォルダのファイルか分かるよう、ファイル名の上にパスのパンくずを出す。
                        if (parent.isNotEmpty()) {
                            Text(
                                parent.replace("/", " / "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isMarkdown) {
                            DropdownMenuItem(
                                text = { Text(if (raw) "整形で表示" else "Raw で表示") },
                                onClick = { menuExpanded = false; raw = !raw },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("履歴") },
                            onClick = { menuExpanded = false; onHistory() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            SlimBottomBar {
                // 左: 目次
                if (isMarkdown && !raw && tocEntries.isNotEmpty()) {
                    TextButton(
                        onClick = { showToc = true },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text("☰ 目次") }
                }
                Spacer(Modifier.weight(1f))
                // 右: 折り返し(コード/Raw時のみ)。整形/Raw 切替は右上 ⋮ メニューへ移動。
                if (!isMarkdown || raw) {
                    TextButton(
                        onClick = { wrap = !wrap },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(if (wrap) "折り返しON" else "折り返しOFF") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val body = text
            when {
                error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                body == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                isMarkdown && !raw -> Box(Modifier.fillMaxSize()) {
                    val (frontmatter, sections) = mdModel!!
                    Column(
                        Modifier.fillMaxSize()
                            .onGloballyPositioned { viewportTopPx = it.positionInRoot().y }
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                    ) {
                        if (frontmatter != null) {
                            FrontmatterView(frontmatter, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                        }
                        sections.forEachIndexed { index, section ->
                            // 各セクションの Y を記録し、目次タップ時のスクロール先にする。
                            Column(
                                Modifier.fillMaxWidth().onGloballyPositioned {
                                    sectionTops[index] = it.positionInParent().y.roundToInt()
                                },
                            ) {
                                val blocks = MarkdownRenderer.splitBlocks(
                                    section.markdown,
                                    extractTables = tableMode == TableMode.SCROLLABLE,
                                )
                                blocks.forEach { block ->
                                    when (block) {
                                        is MdBlock.Text -> MarkdownView(
                                            markdown = block.markdown,
                                            baseDir = baseDir,
                                            workDir = workDir,
                                            textColor = textColor,
                                            dark = dark,
                                            fontScale = fontScale,
                                            onNavigateToFile = onNavigateToFile,
                                            onExternalLink = openExternal,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        is MdBlock.Mermaid -> MermaidWebView(
                                            code = block.code,
                                            dark = dark,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        is MdBlock.Table -> MarkdownTableView(
                                            header = block.header,
                                            rows = block.rows,
                                            fontScale = fontScale,
                                            // 見出しオーバーレイの下端にヘッダを固定する
                                            viewportTopPx = viewportTopPx +
                                                (if (stickyHeadings) stickyHeadingsHeightPx else 0),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    if (stickyHeadings) {
                        StickyHeadingsOverlay(
                            sections = sections,
                            sectionTops = sectionTops,
                            scrollY = scrollState.value,
                            fontScale = fontScale,
                            onJump = { targetY -> scope.launch { scrollState.animateScrollTo(targetY.coerceAtLeast(0)) } },
                            onHeight = { stickyHeadingsHeightPx = it },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
                // Raw 表示(Markdown のソース)は無装飾の行表示。行ジャンプ時はその行へ。
                isMarkdown && raw -> CodeView(
                    code = body,
                    language = null,
                    dark = dark,
                    fontScale = fontScale,
                    highlightLine = targetLine?.let { it - 1 },
                    wrap = wrap,
                    showLineNumbers = showLineNumbers,
                    modifier = Modifier.fillMaxSize(),
                )
                // 非 Markdown ファイルはコードとして拡張子からハイライト(行ジャンプ対応)
                else -> {
                    val language = remember(filePath) { CodeHighlight.languageForFile(fileName) }
                    CodeView(
                        code = body,
                        language = language,
                        dark = dark,
                        fontScale = fontScale,
                        highlightLine = targetLine?.let { it - 1 },
                        wrap = wrap,
                        showLineNumbers = showLineNumbers,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showToc) {
        // 現在のスクロール位置に対応する見出しを強調する。
        val activeIndex = activeTocIndex(
            tocEntries.map { it.sectionIndex },
            sectionTops,
            scrollState.value,
        )
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            ) {
                Text(
                    "目次",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                tocEntries.forEach { entry ->
                    val active = entry.sectionIndex == activeIndex
                    Text(
                        entry.heading.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        fontWeight = if (active) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val y = sectionTops[entry.sectionIndex] ?: 0
                                showToc = false
                                scope.launch { scrollState.animateScrollTo(y) }
                            }
                            .padding(
                                start = (16 + (entry.heading.level - 1) * 16).dp,
                                end = 16.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                    )
                }
            }
        }
    }
}

private data class TocEntry(val sectionIndex: Int, val heading: Heading)

/**
 * スティッキー見出しの祖先パス(h1>h2>h3...)を返す。
 * 「見出しがスティッキーバーの下端に達したら昇格」を、実測高さに依存せず1パスで自己完結計算する
 * (rowHeightPx は各見出し行の推定高さ px)。スタックに積むごとにバー高さを足してしきい値を更新するため、
 * 実測高さのフィードバックループ(=境界での点滅)が起きない。
 */
internal fun stickyHeadingStack(
    sections: List<MdSection>,
    tops: Map<Int, Int>,
    scrollY: Int,
    rowHeightPx: (Heading) -> Int,
): List<Pair<Int, Heading>> {
    val stack = ArrayList<Pair<Int, Heading>>()
    var barHeight = 0
    for (idx in sections.indices) {
        val h = sections[idx].heading ?: continue
        val top = tops[idx] ?: continue
        if (top >= scrollY + barHeight) break // 以降の見出しは top がより大きいので対象外
        while (stack.isNotEmpty() && stack.last().second.level >= h.level) {
            barHeight -= rowHeightPx(stack.last().second)
            stack.removeAt(stack.lastIndex)
        }
        stack.add(idx to h)
        barHeight += rowHeightPx(h)
    }
    return stack
}

private fun headingSp(level: Int, fontScale: Float): Float =
    (when (level) { 1 -> 20f; 2 -> 18f; 3 -> 16f; else -> 15f }) * fontScale

/** 見出しの祖先パスを画面上部に固定表示する(スティッキー見出し)。 */
@Composable
private fun StickyHeadingsOverlay(
    sections: List<MdSection>,
    sectionTops: Map<Int, Int>,
    scrollY: Int,
    fontScale: Float,
    onJump: (Int) -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 行高さは推定(font サイズ + 縦パディング)。実測に依存させないことで境界での点滅を防ぐ。
    val density = LocalDensity.current
    val rowHeightPx: (Heading) -> Int = { h ->
        with(density) { (headingSp(h.level, fontScale).sp.toPx() + 8.dp.toPx()).roundToInt() }
    }
    val stack = stickyHeadingStack(sections, sectionTops, scrollY, rowHeightPx)
    if (stack.isEmpty()) {
        onHeight(0)
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned { onHeight(it.size.height) },
    ) {
        stack.forEach { (idx, h) ->
            Text(
                text = h.text,
                fontSize = headingSp(h.level, fontScale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    // その見出しの直後へ飛ばし、見出し自身を固定バー最下段に表示する
                    .clickable { onJump((sectionTops[idx] ?: 0) + 1) }
                    .padding(start = (16 + (h.level - 1) * 8).dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
            )
        }
        HorizontalDivider()
    }
}

/**
 * 現在のスクロール位置(px)に対応する見出しのセクション index を返す。
 * top が scrollY 以下である最後の見出しを採用する(該当なし/空なら最初の見出し or -1)。
 * sectionIndices は文書順、tops は section index → Y(px)。
 */
internal fun activeTocIndex(sectionIndices: List<Int>, tops: Map<Int, Int>, scrollY: Int): Int {
    var active = sectionIndices.firstOrNull() ?: -1
    for (idx in sectionIndices) {
        val top = tops[idx] ?: continue
        if (top <= scrollY + 1) active = idx else break
    }
    return active
}

/**
 * GFM テーブルを横スクロール＋ヘッダ固定で表示する(設定 SCROLLABLE 時)。
 * 全行をそのまま表示し、テーブルを読んでいる間はヘッダ行をビューポート上端に貼り付ける
 * (graphicsLayer.translationY + zIndex による擬似スティッキー)。横は共有スクロールでカラム整列。
 * viewportTopPx はスクロール領域上端の root 座標(px)。
 */
@Composable
private fun MarkdownTableView(
    header: List<String>,
    rows: List<List<String>>,
    fontScale: Float,
    viewportTopPx: Float,
    modifier: Modifier = Modifier,
) {
    val colCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    // 各カラム幅を内容(最大文字数)から推定。短い列は狭く、長い列は広く(クランプ)。CJK等は概算。
    val colWidths = remember(header, rows, fontScale) {
        (0 until colCount).map { c ->
            val maxChars = (sequenceOf(header.getOrElse(c) { "" }) +
                rows.asSequence().map { it.getOrElse(c) { "" } }).maxOf { it.length }
            (maxChars * (10f * fontScale) + 20f).dp.coerceIn(48.dp, 260.dp)
        }
    }
    val hScroll = rememberScrollState()
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val fontSize = (14f * fontScale).sp

    var tableTopPx by remember { mutableFloatStateOf(0f) }
    var tableHeightPx by remember { mutableIntStateOf(0) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // テーブル上端がビューポート上端より上にある量。ヘッダはその分だけ下げて上端に留める。
    val stickyOffset = (viewportTopPx - tableTopPx)
        .coerceIn(0f, (tableHeightPx - headerHeightPx).coerceAtLeast(0).toFloat())

    @Composable
    fun cell(text: String, isHeader: Boolean, width: Dp) {
        Text(
            text = text,
            // セル毎にボーダーを引いて表のグリッドを見せる。fillMaxHeight で行内のセル高さを揃える
            // (改行で背の高いセルがあっても他セルの枠がその高さまで伸びる)。
            modifier = Modifier.width(width)
                .fillMaxHeight()
                .border(0.5.dp, borderColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = fontSize,
            fontWeight = if (isHeader) FontWeight.Bold else null,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }

    BoxWithConstraints(modifier) {
        // 内容由来の合計幅がビューポートより狭ければ全幅に広げる(均等比率)。広ければ横スクロール。
        val avail = maxWidth
        val total = colWidths.fold(0.dp) { a, b -> a + b }
        val widths = if (total > 0.dp && total < avail) {
            val scale = avail / total
            colWidths.map { it * scale }
        } else {
            colWidths
        }
        Column(
            Modifier
                .horizontalScroll(hScroll)
                .onGloballyPositioned {
                    tableTopPx = it.positionInRoot().y
                    tableHeightPx = it.size.height
                },
        ) {
            // ヘッダ: translationY で上端に追従、zIndex で本文より前面に描画。
            // height(IntrinsicSize.Min) で行内セルを同じ高さに揃える。
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .zIndex(1f)
                    .graphicsLayer { translationY = stickyOffset }
                    .onGloballyPositioned { headerHeightPx = it.size.height }
                    .background(headerBg),
            ) {
                for (c in 0 until colCount) cell(header.getOrElse(c) { "" }, true, widths[c])
            }
            rows.forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    for (c in 0 until colCount) cell(row.getOrElse(c) { "" }, false, widths[c])
                }
            }
        }
    }
}

/** YAML フロントマターをメタ情報カードとして表示する。 */
@Composable
private fun FrontmatterView(entries: List<FrontmatterEntry>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEach { e ->
                Row {
                    Text(
                        e.key,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(e.value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
