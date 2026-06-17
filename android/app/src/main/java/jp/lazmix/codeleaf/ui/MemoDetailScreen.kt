package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.data.MemoFormat
import jp.lazmix.codeleaf.data.db.MemoEntry

/**
 * 1つのメモ帳の中身。エントリ(ファイル:行 + 引用 + コメント)を一覧し、
 * 各エントリの ⋮ で共有/コピー/削除、ヘッダタップで該当ファイルの行へジャンプ。
 * 上部 ⋮ で メモ帳まるごと共有/コピー・名前変更・削除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailScreen(
    repoName: String,
    memoTitle: String,
    entries: List<MemoEntry>,
    onOpenEntry: (MemoEntry) -> Unit,
    onDeleteEntry: (id: Long) -> Unit,
    onRenameMemo: (title: String) -> Unit,
    onDeleteMemo: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var topMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteMemo by remember { mutableStateOf(false) }

    fun bundle(): String = MemoFormat.memo(repoName, memoTitle, entries)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            memoTitle.ifBlank { "(無題)" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            repoName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Box {
                        IconButton(onClick = { topMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("まとめて共有") },
                                onClick = { topMenu = false; shareText(context, bundle()) },
                            )
                            DropdownMenuItem(
                                text = { Text("まとめてコピー") },
                                onClick = { topMenu = false; clipboard.setText(AnnotatedString(bundle())) },
                            )
                            DropdownMenuItem(
                                text = { Text("名前を変更") },
                                onClick = { topMenu = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("メモ帳を削除") },
                                onClick = { topMenu = false; showDeleteMemo = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "エントリがありません。ファイル表示中に行を長押しして追加します。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.id }) { e ->
                    EntryCard(
                        entry = e,
                        onOpen = { onOpenEntry(e) },
                        onShare = { shareText(context, MemoFormat.entry(repoName, e)) },
                        onCopy = { clipboard.setText(AnnotatedString(MemoFormat.entry(repoName, e))) },
                        onDelete = { onDeleteEntry(e.id) },
                    )
                }
            }
        }
    }

    if (showRename) {
        MemoTitleDialog(
            title = "メモ帳の名前を変更",
            initial = memoTitle,
            confirmLabel = "変更",
            onConfirm = { showRename = false; onRenameMemo(it) },
            onDismiss = { showRename = false },
        )
    }
    if (showDeleteMemo) {
        AlertDialog(
            onDismissRequest = { showDeleteMemo = false },
            title = { Text("メモ帳を削除") },
            text = { Text("「${memoTitle}」とその全エントリを削除します。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { showDeleteMemo = false; onDeleteMemo() }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteMemo = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun EntryCard(
    entry: MemoEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ヘッダ(ファイル:行)タップで該当行へジャンプ。
                Text(
                    "${entry.filePath} ${MemoFormat.lineRange(entry.lineStart, entry.lineEnd)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { onOpen() }.padding(vertical = 6.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("共有") }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("コピー") }, onClick = { menu = false; onCopy() })
                        DropdownMenuItem(text = { Text("削除") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
            if (entry.quote.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.quote,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (entry.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("エントリを削除") },
            text = { Text("このエントリを削除します。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }
}
