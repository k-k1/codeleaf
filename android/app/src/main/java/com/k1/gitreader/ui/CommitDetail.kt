package com.k1.gitreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.git.GraphCommit

/**
 * コミット詳細(ヘッダ: refs/フルメッセージ/著者・時刻・sha ＋ コミット全体の diff)。
 * 2ペインの右ペイン(`CommitDetailContent`)と compact の全画面(`CommitDetailScreen`)で共有する。
 */
@Composable
fun CommitDetailContent(commit: GraphCommit, loadDiff: suspend () -> String) {
    var diff by remember(commit.sha) { mutableStateOf<String?>(null) }
    var error by remember(commit.sha) { mutableStateOf<String?>(null) }
    LaunchedEffect(commit.sha) {
        error = null
        diff = runCatching { loadDiff() }.getOrElse { error = it.message; "" }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (commit.refs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    commit.refs.forEach { RefChip(it) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(commit.fullMessage.trim(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${commit.author} · ${relativeTimeMillis(commit.committedAt.toEpochMilli())} · ${commit.sha.take(7)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        val d = diff
        when {
            error != null -> Text("diff取得失敗: $error", Modifier.padding(16.dp))
            d == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            d.isBlank() -> Text("差分なし", Modifier.padding(16.dp))
            else -> DiffText(d, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(commit: GraphCommit, loadDiff: suspend () -> String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("commit ${commit.sha.take(7)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CommitDetailContent(commit, loadDiff)
        }
    }
}
