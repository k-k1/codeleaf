package com.k1.gitreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.FileEntry
import com.k1.gitreader.data.db.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    repo: Repo,
    path: String,
    loadDir: suspend (String) -> List<FileEntry>,
    loadBranches: suspend () -> List<com.k1.gitreader.git.BranchInfo>,
    onSync: suspend () -> Unit,
    onSearch: () -> Unit,
    onGraph: () -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSwitchBranch: (String) -> Unit,
    onBack: () -> Unit,
) {
    var entries by remember(repo.id, path) { mutableStateOf<List<FileEntry>?>(null) }
    var error by remember(repo.id, path) { mutableStateOf<String?>(null) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(repo.id, path) {
        error = null
        entries = runCatching { loadDir(path) }
            .getOrElse { error = it.message; emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${repo.branch} ▾",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { showBranchSheet = true },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "検索")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("コミットグラフ") },
                            onClick = { menuExpanded = false; onGraph() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ひとつ上へ")
                }
                Text(
                    text = "/" + path,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    val result = runCatching { onSync() }
                    // 同期後はツリーが変わりうるので再読込
                    entries = runCatching { loadDir(path) }.getOrElse { error = it.message; emptyList() }
                    refreshing = false
                    snackbar.showSnackbar(
                        result.exceptionOrNull()?.let { "同期失敗: ${it.message}" } ?: "同期完了",
                    )
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                entries!!.isEmpty() -> Text("（空のディレクトリ）", Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries!!, key = { it.relPath }) { e ->
                        EntryRow(e, onClick = {
                            if (e.isDir) onOpenDir(e.relPath) else onOpenFile(e.relPath)
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showBranchSheet) {
        BranchSheet(
            currentBranch = repo.branch,
            loadBranches = loadBranches,
            onDismiss = { showBranchSheet = false },
            onSelect = { name ->
                showBranchSheet = false
                onSwitchBranch(name)
            },
        )
    }
}

@Composable
private fun EntryRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (entry.isDir) "📁" else "📄")
        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
