package com.k1.gitreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.render.CodeHighlight
import com.k1.gitreader.render.CodeView
import com.k1.gitreader.render.FrontmatterEntry
import com.k1.gitreader.render.Heading
import com.k1.gitreader.render.MarkdownRenderer
import com.k1.gitreader.render.MarkdownView
import com.k1.gitreader.render.MdBlock
import com.k1.gitreader.render.MdSection
import com.k1.gitreader.render.MermaidWebView
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
    targetLine: Int? = null,
    onHistory: () -> Unit,
    onNavigateToFile: (String) -> Unit,
    onBack: () -> Unit,
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

    // 目次スクロール用: 整形ビューのスクロール状態と各セクションの Y 位置(px)。
    val scope = rememberCoroutineScope()
    val scrollState = remember(filePath) { ScrollState(0) }
    val sectionTops = remember(filePath) { mutableStateMapOf<Int, Int>() }
    var showToc by remember(filePath) { mutableStateOf(false) }

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
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("履歴") },
                            onClick = { menuExpanded = false; onHistory() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                // 左: 目次
                if (isMarkdown && !raw && tocEntries.isNotEmpty()) {
                    TextButton(
                        onClick = { showToc = true },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text("☰ 目次") }
                }
                Spacer(Modifier.weight(1f))
                // 右: 折り返し(コード/Raw時) + 整形/Raw トグル
                if (!isMarkdown || raw) {
                    TextButton(
                        onClick = { wrap = !wrap },
                        modifier = Modifier.padding(end = 4.dp),
                    ) { Text(if (wrap) "折り返しON" else "折り返しOFF") }
                }
                if (isMarkdown) {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 12.dp)) {
                        SegmentedButton(
                            selected = !raw,
                            onClick = { raw = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("整形") }
                        SegmentedButton(
                            selected = raw,
                            onClick = { raw = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Raw") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val body = text
            when {
                error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                body == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                isMarkdown && !raw -> {
                    val (frontmatter, sections) = mdModel!!
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
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
                                MarkdownRenderer.splitBlocks(section.markdown).forEach { block ->
                                    when (block) {
                                        is MdBlock.Text -> MarkdownView(
                                            markdown = block.markdown,
                                            baseDir = baseDir,
                                            workDir = workDir,
                                            textColor = textColor,
                                            dark = dark,
                                            fontScale = fontScale,
                                            onNavigateToFile = onNavigateToFile,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        is MdBlock.Mermaid -> MermaidWebView(
                                            code = block.code,
                                            dark = dark,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
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
