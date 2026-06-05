package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.data.db.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    repos: List<Repo>,
    status: UiStatus,
    onAddClick: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (Repo) -> Unit,
    onOpenGraph: (Repo) -> Unit,
    onSync: (Repo) -> Unit,
    onMessageShown: () -> Unit,
    /** 存在するグループ名(昇順)。空ならグループ機能の導線は出さない。 */
    groups: List<String> = emptyList(),
    /** 選択中グループ(空=すべて)。`repos` は既にこの値で絞り込み済みで渡る。 */
    selectedGroup: String = "",
    onSelectGroup: (String) -> Unit = {},
    selectedRepoId: Long? = null,
    onCollapse: (() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(status.message) {
        status.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // グループがあるときだけタイトルを「CodeLeaf / <group> ▾」のドロップダウンにする。
                    var groupMenu by remember { mutableStateOf(false) }
                    val hasGroups = groups.isNotEmpty()
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (hasGroups) Modifier.clickable { groupMenu = true } else Modifier,
                        ) {
                            Text(
                                if (selectedGroup.isEmpty()) "CodeLeaf" else "CodeLeaf / $selectedGroup",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasGroups) Text(" ▾")
                        }
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            DropdownMenuItem(
                                text = { Text((if (selectedGroup.isEmpty()) "● " else "○ ") + "すべて") },
                                onClick = { groupMenu = false; onSelectGroup("") },
                            )
                            groups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text((if (selectedGroup == g) "● " else "○ ") + g) },
                                    onClick = { groupMenu = false; onSelectGroup(g) },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    // 3ペインのレールのときだけ「畳む」アイコンを出す。
                    if (onCollapse != null) {
                        IconButton(onClick = onCollapse) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "リポ一覧を畳む")
                        }
                    }
                },
                actions = {
                    // リポが1つも無いときは編集の代わりに＋。1つ以上あれば編集(＋は編集画面の中)。
                    if (repos.isEmpty()) {
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Default.Add, contentDescription = "リポジトリを追加")
                        }
                    } else {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Create, contentDescription = "リポジトリを編集")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (status.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (repos.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("リポジトリが未登録です", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "右上の + から GitHub / Bitbucket のリポジトリを追加してください",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(
                            repo,
                            selected = repo.id == selectedRepoId,
                            onOpen = { onOpen(repo) },
                            onOpenGraph = { onOpenGraph(repo) },
                            onSync = { onSync(repo) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoCard(repo: Repo, selected: Boolean, onOpen: () -> Unit, onOpenGraph: () -> Unit, onSync: () -> Unit) {
    val accent = repo.colorTag.accent()
    val colors = if (selected) {
        // 選択パネルはリポ色で淡くハイライト(色なしは中立グレー)。primarycontainer(紫)固定は避ける。
        val tint = (accent ?: MaterialTheme.colorScheme.outline).copy(alpha = 0.22f)
        CardDefaults.cardColors(containerColor = tint)
    } else {
        CardDefaults.cardColors()
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左端のアクセント色バー(色なしは透明)
            Box(
                Modifier.width(6.dp).height(64.dp)
                    .background(accent ?: Color.Transparent),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${repo.host.name.lowercase()} · ${repo.branch}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("同期: ${formatSync(repo.lastSyncedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onOpenGraph) {
                Icon(painterResource(R.drawable.ic_graph), contentDescription = "コミットグラフ")
            }
            IconButton(onClick = onSync) { Icon(Icons.Default.Refresh, contentDescription = "同期") }
}
    }
}

private fun formatSync(epochMillis: Long?): String {
    if (epochMillis == null) return "未同期"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMillis))
}
