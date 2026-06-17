package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.data.MemoFormat
import jp.lazmix.codeleaf.data.db.Memo
import jp.lazmix.codeleaf.data.db.MemoEntry
import jp.lazmix.codeleaf.data.db.MemoWithCount
import kotlinx.coroutines.launch

/**
 * リポジトリのメモ帳一覧。各メモ帳はタイトル＋エントリ数で、タップで詳細へ。
 * 各メモ帳の ⋮ から まとめて共有/コピー・名前変更・削除。右上＋で新規作成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosScreen(
    repoName: String,
    memos: List<MemoWithCount>,
    onOpenMemo: (Memo) -> Unit,
    onCreateMemo: (title: String) -> Unit,
    onRenameMemo: (id: Long, title: String) -> Unit,
    onDeleteMemo: (id: Long) -> Unit,
    loadEntries: suspend (memoId: Long) -> List<MemoEntry>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Memo?>(null) }
    var deleteTarget by remember { mutableStateOf<Memo?>(null) }

    fun bundleThen(memo: Memo, action: (String) -> Unit) {
        scope.launch {
            val text = MemoFormat.memo(repoName, memo.title, loadEntries(memo.id))
            action(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("メモ", style = MaterialTheme.typography.titleMedium)
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
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新しいメモ帳")
                    }
                },
            )
        },
    ) { padding ->
        if (memos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "メモ帳がありません。右上の＋で作成、またはファイル表示中に行を長押しして追加します。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(memos, key = { it.memo.id }) { mwc ->
                    MemoRow(
                        mwc = mwc,
                        onOpen = { onOpenMemo(mwc.memo) },
                        onShare = { bundleThen(mwc.memo) { shareText(context, it) } },
                        onCopy = { bundleThen(mwc.memo) { clipboard.setText(AnnotatedString(it)) } },
                        onRename = { renameTarget = mwc.memo },
                        onDelete = { deleteTarget = mwc.memo },
                    )
                }
            }
        }
    }

    if (showCreate) {
        MemoTitleDialog(
            title = "新しいメモ帳",
            initial = "",
            confirmLabel = "作成",
            onConfirm = { showCreate = false; onCreateMemo(it) },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { memo ->
        MemoTitleDialog(
            title = "メモ帳の名前を変更",
            initial = memo.title,
            confirmLabel = "変更",
            onConfirm = { renameTarget = null; onRenameMemo(memo.id, it) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { memo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("メモ帳を削除") },
            text = { Text("「${memo.title}」とその全エントリを削除します。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDeleteMemo(memo.id) }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun MemoRow(
    mwc: MemoWithCount,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen() },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    mwc.memo.title.ifBlank { "(無題)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${mwc.entryCount} 件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("まとめて共有") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("まとめてコピー") }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text("名前を変更") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("削除") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** メモ帳タイトルの作成/改名ダイアログ。空タイトルは確定不可。 */
@Composable
fun MemoTitleDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("タイトル") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
