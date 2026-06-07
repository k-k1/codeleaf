package jp.lazmix.codeleaf.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.lazmix.codeleaf.data.FileInfo
import jp.lazmix.codeleaf.data.FileKind
import jp.lazmix.codeleaf.data.LinkOpenMode
import jp.lazmix.codeleaf.data.TableMode
import jp.lazmix.codeleaf.data.humanSize
import jp.lazmix.codeleaf.data.db.MemoWithCount
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    repo: Repo,
    filePath: String,
    workDir: File,
    /** 推定エンコード(Charset 名・null は UTF-8)で本文を読む。 */
    loadText: suspend (charsetName: String?) -> String,
    /** ファイル種別(テキスト/画像/バイナリ)とサイズを先読みで判定する。 */
    probeFile: suspend () -> FileInfo,
    fontScale: Float,
    defaultWrap: Boolean = true,
    /** 折り返しトグルの変更を保存する(ファイル閲覧の折り返し設定として永続化)。 */
    onToggleWrap: (Boolean) -> Unit = {},
    linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    showLineNumbers: Boolean = false,
    tableMode: TableMode = TableMode.INLINE,
    stickyHeadings: Boolean = true,
    targetLine: Int? = null,
    onHistory: () -> Unit,
    onMemos: () -> Unit,
    onNavigateToFile: (String) -> Unit,
    onBack: () -> Unit,
    /** 戻る矢印を表示するか。2/3ペインでは一覧が常に見えるため非表示にする。 */
    showBack: Boolean = true,
    /** 同一フォルダ内のファイル(repo ルート相対パス, 表示順)。前/次ファイル送りに使う。 */
    loadSiblings: suspend () -> List<String> = { emptyList() },
    /** 前/次ファイルを開く(現在のビューアを置き換える)。 */
    onOpenSibling: (String) -> Unit = {},
    /** メモ追加先の候補(このリポのメモ帳)。 */
    memos: List<MemoWithCount> = emptyList(),
    /** 既存メモ帳にエントリ追加(行は1始まり・lineEnd 含む)。 */
    onAddMemoEntry: (memoId: Long, lineStart: Int, lineEnd: Int, quote: String, comment: String) -> Unit =
        { _, _, _, _, _ -> },
    /** 新規メモ帳を作って即エントリ追加。 */
    onCreateMemoWithEntry: (title: String, lineStart: Int, lineEnd: Int, quote: String, comment: String) -> Unit =
        { _, _, _, _, _ -> },
) {
    var text by remember(filePath) { mutableStateOf<String?>(null) }
    var info by remember(filePath) { mutableStateOf<FileInfo?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }
    // 検索の行ジャンプで開いた場合は、行が分かる Raw 表示で開始する。
    var raw by remember(filePath) { mutableStateOf(targetLine != null) }
    var wrap by remember(filePath) { mutableStateOf(defaultWrap) }
    var menuExpanded by remember { mutableStateOf(false) }

    // まず種別を判定し、テキストのときだけ本文を読み込む(画像/バイナリは全読みしない)。
    LaunchedEffect(repo.id, filePath) {
        error = null
        text = null
        info = null
        val probed = runCatching { probeFile() }.getOrElse { error = it.message; null }
        info = probed
        if (probed?.kind is FileKind.Text) {
            text = runCatching { loadText(probed.text?.charsetName) }.getOrElse { error = it.message; null }
        }
    }
    val isTextFile = info?.kind is FileKind.Text

    // メモ追加の行選択範囲(0始まり)。非 null の間は追加シートを出す。
    var addRange by remember(filePath) { mutableStateOf<IntRange?>(null) }

    // 同一フォルダの隣接ファイル(前/次送り用)。読み込めるまでは送りボタンを出さない。
    var siblings by remember(repo.id, filePath) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(repo.id, filePath) {
        siblings = runCatching { loadSiblings() }.getOrDefault(emptyList())
    }
    val siblingIndex = siblings.indexOf(filePath)
    val prevFile = siblings.getOrNull(siblingIndex - 1)
    val nextFile = siblings.getOrNull(siblingIndex + 1)

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
                    if (showBack) BackButton(onBack)
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isMarkdown && isTextFile) {
                            DropdownMenuItem(
                                text = { Text(if (raw) "整形で表示" else "Raw で表示") },
                                onClick = { menuExpanded = false; raw = !raw },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("履歴") },
                            onClick = { menuExpanded = false; onHistory() },
                        )
                        if (isTextFile) {
                            DropdownMenuItem(
                                text = { Text("メモ") },
                                onClick = { menuExpanded = false; onMemos() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            SlimBottomBar {
                // 左: 目次(整形 Markdown) / 折り返し(コード・Raw)。同じ左位置に揃える。
                // 画像/バイナリでは本文操作が無いので出さない。
                if (isTextFile && isMarkdown && !raw && tocEntries.isNotEmpty()) {
                    TextButton(
                        onClick = { showToc = true },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text("☰ 目次") }
                }
                if (isTextFile && (!isMarkdown || raw)) {
                    TextButton(
                        onClick = { wrap = !wrap; onToggleWrap(wrap) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text(if (wrap) "折り返しON" else "折り返しOFF") }
                }
                Spacer(Modifier.weight(1f))
                // 右: 同一フォルダ内の前/次ファイルを開く(端では無効化)。
                if (siblings.size > 1 && siblingIndex >= 0) {
                    IconButton(onClick = { prevFile?.let(onOpenSibling) }, enabled = prevFile != null) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "前のファイル")
                    }
                    IconButton(onClick = { nextFile?.let(onOpenSibling) }, enabled = nextFile != null) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "次のファイル")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val body = text
            val kind = info?.kind
            // 上部メタバー: テキストはエンコード/BOM/改行/サイズ、画像はフォーマット/寸法/サイズ。
            info?.let { fi -> fileMetaLine(fi)?.let { FileMetaBar(it) } }
            when {
                error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                kind == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                kind is FileKind.Image -> ImageViewer(
                    file = File(workDir, filePath),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                )
                kind is FileKind.Binary -> BinaryInfoView(
                    typeLabel = kind.typeLabel,
                    size = info!!.size,
                    head = info!!.head,
                    modifier = Modifier.fillMaxSize(),
                )
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
                                            // 長押しでこのブロックのソース行を起点にメモ追加。
                                            onLongPress = blockLineRange(body, block.markdown)
                                                ?.let { range -> { addRange = range } },
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
                    onLineLongPress = { idx -> addRange = idx..idx },
                    selectedLines = addRange,
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
                        onLineLongPress = { idx -> addRange = idx..idx },
                        selectedLines = addRange,
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

    // 行を長押ししたら、その行を起点にメモ追加シートを開く(コード/Raw 表示のみ)。
    val sheetRange = addRange
    if (sheetRange != null && text != null) {
        AddMemoSheet(
            fileName = fileName,
            lines = remember(text) { text!!.split("\n") },
            initialRange = sheetRange,
            memos = memos,
            onSave = { lineStart, lineEnd, quote, comment, memoId, newTitle ->
                if (memoId != null) {
                    onAddMemoEntry(memoId, lineStart, lineEnd, quote, comment)
                } else {
                    onCreateMemoWithEntry(newTitle, lineStart, lineEnd, quote, comment)
                }
                addRange = null
            },
            onDismiss = { addRange = null },
        )
    }
}

/**
 * 整形 Markdown のブロック(markdown 文字列)を全文から探し、0始まりのソース行範囲を返す。
 * 見つからない(空・重複等)場合は null。範囲はメモ追加シートで微調整できる。
 */
internal fun blockLineRange(fullText: String, blockMarkdown: String): IntRange? {
    val blk = blockMarkdown.trim('\n')
    if (blk.isEmpty()) return null
    val idx = fullText.indexOf(blk)
    if (idx < 0) return null
    val start = fullText.substring(0, idx).count { it == '\n' }
    return start..(start + blk.count { it == '\n' })
}

/**
 * 上部メタバーの1行を組み立てる(純粋関数)。テキストはエンコード/BOM/改行/サイズ、
 * 画像はフォーマット/寸法/サイズ。バイナリは概要カードに出すので null。
 */
internal fun fileMetaLine(info: FileInfo): String? = when (val k = info.kind) {
    is FileKind.Text -> buildList {
        info.text?.let { m ->
            add(m.encodingLabel)
            add(if (m.hasBom) "BOM" else "BOMなし")
            add(m.eol.label)
        }
        add(humanSize(info.size))
    }.joinToString(" ・ ")
    is FileKind.Image -> buildList {
        add(k.format.uppercase())
        if (info.imageWidth != null && info.imageHeight != null) {
            add("${info.imageWidth}×${info.imageHeight}")
        }
        add(humanSize(info.size))
    }.joinToString(" ・ ")
    is FileKind.Binary -> null
}

/** 本文上部に出す1行のメタ情報バー。 */
@Composable
private fun FileMetaBar(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * 画像ファイルを表示する。初期はビューア領域にフィット(ContentScale.Fit)し、ピンチで拡大/縮小、
 * 拡大中はドラッグでパン、ダブルタップで等倍↔2.5倍をトグルする。SVG も Coil の SvgDecoder で描画。
 */
@Composable
private fun ImageViewer(file: File, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(file.path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    // 拡大時のみパン可。等倍に戻ったら中央へ戻す。
                    offset = if (next > 1f) offset + pan else Offset.Zero
                    scale = next
                }
            }
            .pointerInput(file.path) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(file).build(),
            imageLoader = loader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * 画像以外のバイナリは本文を出さず、file(1) 風の種別・サイズ・先頭バイトの 16 進プレビューだけを示す。
 */
@Composable
private fun BinaryInfoView(typeLabel: String, size: Long, head: ByteArray, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(typeLabel, style = MaterialTheme.typography.titleLarge)
        Text(
            "${humanSize(size)} ・ テキストとして表示できません",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (head.isNotEmpty()) {
            Text(
                "先頭バイト",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    hexPreview(head, 96),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** 先頭 [max] バイトを 16 進ダンプ(16 バイト毎に改行)する。 */
private fun hexPreview(bytes: ByteArray, max: Int): String {
    val n = minOf(bytes.size, max)
    val sb = StringBuilder()
    for (i in 0 until n) {
        sb.append("%02X".format(bytes[i].toInt() and 0xFF))
        sb.append(if ((i + 1) % 16 == 0) "\n" else " ")
    }
    return sb.toString().trimEnd()
}

/**
 * メモ追加シート。長押しした行を起点に、開始/終了行の微調整・引用プレビュー・コメント入力・
 * 追加先メモ帳(既存 or 新規)の選択を行い、[onSave] で確定する。行は表示用に 1 始まり。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemoSheet(
    fileName: String,
    lines: List<String>,
    initialRange: IntRange,
    memos: List<MemoWithCount>,
    onSave: (lineStart: Int, lineEnd: Int, quote: String, comment: String, memoId: Long?, newTitle: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val total = lines.size.coerceAtLeast(1)
    var start1 by remember { mutableIntStateOf((initialRange.first + 1).coerceIn(1, total)) }
    var end1 by remember { mutableIntStateOf((initialRange.last + 1).coerceIn(start1, total)) }
    var comment by remember { mutableStateOf("") }
    var selectedMemoId by remember { mutableStateOf(memos.firstOrNull()?.memo?.id) }
    var newTitle by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }

    val s0 = (start1 - 1).coerceIn(0, total - 1)
    val e0 = (end1 - 1).coerceIn(s0, total - 1)
    val quote = remember(s0, e0, lines) { lines.subList(s0, e0 + 1).joinToString("\n") }
    val creatingNew = selectedMemoId == null
    val canSave = !creatingNew || newTitle.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("メモを追加", style = MaterialTheme.typography.titleMedium)
            Text(
                fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 行範囲の微調整(開始 ≤ 終了 ≤ 総行数)。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("行", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                LineStepper(
                    value = start1,
                    onDec = { start1 = (start1 - 1).coerceAtLeast(1) },
                    onInc = { start1 = (start1 + 1).coerceAtMost(end1) },
                )
                Text("〜", Modifier.padding(horizontal = 8.dp))
                LineStepper(
                    value = end1,
                    onDec = { end1 = (end1 - 1).coerceAtLeast(start1) },
                    onInc = { end1 = (end1 + 1).coerceAtMost(total) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "全 $total 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 引用プレビュー。
            Text(
                quote,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("コメント") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // 追加先メモ帳: 既存から選ぶ or 新規作成。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("メモ帳", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Box {
                    TextButton(onClick = { pickerOpen = true }) {
                        val label = if (creatingNew) {
                            "新しいメモ帳"
                        } else {
                            memos.firstOrNull { it.memo.id == selectedMemoId }?.memo?.title ?: "メモ帳"
                        }
                        Text(label)
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        memos.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.memo.title.ifBlank { "(無題)" }) },
                                onClick = { selectedMemoId = m.memo.id; pickerOpen = false },
                            )
                        }
                        if (memos.isNotEmpty()) HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("＋ 新しいメモ帳") },
                            onClick = { selectedMemoId = null; pickerOpen = false },
                        )
                    }
                }
            }
            if (creatingNew) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("新しいメモ帳のタイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = canSave,
                    onClick = { onSave(s0 + 1, e0 + 1, quote, comment, selectedMemoId, newTitle.trim()) },
                ) { Text("保存") }
            }
        }
    }
}

/** 行番号の増減ステッパ(−[値]＋)。 */
@Composable
private fun LineStepper(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDec, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text("−") }
        Text("$value", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onInc, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text("＋") }
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
