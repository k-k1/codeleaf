package com.k1.gitreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.render.CodeHighlight
import com.k1.gitreader.render.CodeView
import com.k1.gitreader.render.FrontmatterEntry
import com.k1.gitreader.render.MarkdownRenderer
import com.k1.gitreader.render.MarkdownView
import com.k1.gitreader.render.MdBlock
import com.k1.gitreader.render.MermaidWebView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    repo: Repo,
    filePath: String,
    workDir: File,
    loadText: suspend () -> String,
    fontScale: Float,
    onHistory: () -> Unit,
    onNavigateToFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember(filePath) { mutableStateOf<String?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }
    var raw by remember(filePath) { mutableStateOf(false) }
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
            if (isMarkdown) {
                BottomAppBar {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 12.dp)) {
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
                isMarkdown && !raw -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    val (frontmatter, content) = remember(body) {
                        MarkdownRenderer.extractFrontmatter(body)
                    }
                    if (frontmatter != null) {
                        FrontmatterView(frontmatter, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    MarkdownRenderer.splitBlocks(content).forEach { block ->
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
                // Raw 表示(Markdown のソース)は装飾せずそのまま見せる
                isMarkdown && raw -> Text(
                    text = body,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                )
                // 非 Markdown ファイルはコードとして拡張子からハイライト
                else -> {
                    val language = remember(filePath) { CodeHighlight.languageForFile(fileName) }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        CodeView(
                            code = body,
                            language = language,
                            dark = dark,
                            fontScale = fontScale,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
