package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.git.CommitInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    filePath: String,
    loadHistory: suspend () -> List<CommitInfo>,
    onSelectCommit: (CommitInfo) -> Unit,
    onBack: () -> Unit,
    selectedSha: String? = null,
    /** 引っ張って更新(同期)する処理。null なら pull-to-refresh を出さない。 */
    onSync: (suspend () -> Unit)? = null,
) {
    var commits by remember(filePath) { mutableStateOf<List<CommitInfo>?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(filePath) {
        error = null
        commits = runCatching { loadHistory() }.getOrElse { error = it.message; emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("履歴: ${filePath.substringAfterLast('/')}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    if (onSync == null || refreshing) return@PullToRefreshBox
                    scope.launch {
                        refreshing = true
                        val result = runCatching { onSync() }
                        // 同期後はそのファイルの履歴が増減しうるので再読込。
                        commits = runCatching { loadHistory() }.getOrElse { error = it.message; emptyList() }
                        refreshing = false
                        snackbar.showSnackbar(
                            result.exceptionOrNull()?.let { "同期失敗: ${it.message}" } ?: "同期完了",
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val list = commits
                when {
                    error != null -> Text("履歴取得失敗: $error", Modifier.padding(16.dp))
                    list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    list.isEmpty() -> Text("履歴がありません", Modifier.padding(16.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.sha }) { c ->
                            val selected = c.sha == selectedSha
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onSelectCommit(c) }
                                    .then(
                                        if (selected) {
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(c.shortMessage, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${shortSha(c.sha)} · ${c.author} · ${relativeTimeMillis(c.committedAt.toEpochMilli())}",
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
}

/**
 * 2/3ペインの右に出すファイル履歴の差分(選択コミットでのそのファイルの diff)。
 * ヘッダ(件名・著者・時刻・sha)＋ DiffText。コミットグラフの CommitDetailContent と対の関係。
 */
@Composable
fun FileDiffPane(commit: CommitInfo, loadDiff: suspend () -> String) {
    var diff by remember(commit.sha) { mutableStateOf<String?>(null) }
    var error by remember(commit.sha) { mutableStateOf<String?>(null) }
    LaunchedEffect(commit.sha) {
        error = null
        diff = runCatching { loadDiff() }.getOrElse { error = it.message; "" }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                commit.shortMessage,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${commit.author} · ${relativeTimeMillis(commit.committedAt.toEpochMilli())} · ${shortSha(commit.sha)}",
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
