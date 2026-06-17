package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import jp.lazmix.codeleaf.git.GraphCommit

/**
 * コミット詳細(ヘッダ: refs/フルメッセージ/著者・時刻・sha ＋ コミット全体の diff)。
 * 2ペインの右ペイン(`CommitDetailContent`)と compact の全画面(`CommitDetailScreen`)で共有する。
 */
@Composable
fun CommitDetailContent(commit: GraphCommit, loadDiff: suspend () -> String, onOpenFile: (String) -> Unit = {}) {
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
            // タイトル(コミットの件名)= 1行目を少し大きく。
            val subject = commit.shortMessage.trim()
            Text(
                subject.ifBlank { "(メッセージなし)" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // メタ: 著者・時刻・ハッシュ。
            Text(
                "${commit.author} · ${relativeTimeMillis(commit.committedAt.toEpochMilli())} · ${shortSha(commit.sha)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 本文(件名以降)= 数行表示し、タップで全文展開。
            val body = remember(commit.sha) { commit.fullMessage.trim().removePrefix(subject).trim() }
            if (body.isNotBlank()) {
                var expanded by remember(commit.sha) { mutableStateOf(false) }
                var overflow by remember(commit.sha) { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = expanded || overflow) { expanded = !expanded },
                )
                if (expanded || overflow) {
                    Text(
                        if (expanded) "閉じる" else "続きを表示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(top = 2.dp),
                    )
                }
            }
        }
        HorizontalDivider()
        val d = diff
        when {
            error != null -> Text("diff取得失敗: $error", Modifier.padding(16.dp))
            d == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            d.isBlank() -> Text("差分なし", Modifier.padding(16.dp))
            else -> DiffView(d, Modifier.weight(1f).fillMaxWidth(), onOpenFile)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(
    commit: GraphCommit,
    loadDiff: suspend () -> String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit = {},
) {
    Scaffold(
        // 本文(DiffView)が自前の下部バーで navigationBars を padding するため二重計上を防ぐ(DiffScreen と同様)。
        contentWindowInsets = contentInsetsExcludingNavBar,
        topBar = {
            TopAppBar(
                title = { Text("commit ${shortSha(commit.sha)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CommitDetailContent(commit, loadDiff, onOpenFile)
        }
    }
}
