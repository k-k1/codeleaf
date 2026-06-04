package com.k1.gitreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.render.MarkdownView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    repo: Repo,
    filePath: String,
    workDir: File,
    loadText: suspend () -> String,
    onBack: () -> Unit,
) {
    var text by remember(filePath) { mutableStateOf<String?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }
    var raw by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(repo.id, filePath) {
        error = null
        text = runCatching { loadText() }.getOrElse { error = it.message; null }
    }

    val isMarkdown = filePath.endsWith(".md", true) || filePath.endsWith(".markdown", true)
    val fileName = filePath.substringAfterLast('/')
    val parent = filePath.substringBeforeLast('/', "")
    val baseDir = if (parent.isEmpty()) workDir else File(workDir, parent)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
                isMarkdown && !raw -> MarkdownView(
                    markdown = body,
                    baseDir = baseDir,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                )
                else -> Text(
                    text = body,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                )
            }
        }
    }
}
