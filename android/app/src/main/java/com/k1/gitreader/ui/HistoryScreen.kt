package com.k1.gitreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.git.CommitInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    filePath: String,
    loadHistory: suspend () -> List<CommitInfo>,
    onOpenDiff: (String) -> Unit,
    onBack: () -> Unit,
) {
    var commits by remember(filePath) { mutableStateOf<List<CommitInfo>?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        error = null
        commits = runCatching { loadHistory() }.getOrElse { error = it.message; emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("履歴: ${filePath.substringAfterLast('/')}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val list = commits
            when {
                error != null -> Text("履歴取得失敗: $error", Modifier.padding(16.dp))
                list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                list.isEmpty() -> Text("履歴がありません", Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.sha }) { c ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onOpenDiff(c.sha) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(c.shortMessage, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${c.sha.take(7)} · ${c.author} · ${relativeTimeMillis(c.committedAt.toEpochMilli())}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
