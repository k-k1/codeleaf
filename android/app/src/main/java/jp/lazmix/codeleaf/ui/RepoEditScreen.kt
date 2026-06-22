package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.lazmix.codeleaf.data.db.CloneState
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.RepoColor

private val ITEM_HEIGHT = 96.dp
private val HEADER_HEIGHT = 48.dp
private val ROW_SPACING = 8.dp

/** 編集リストの1行。Header=見出し(group=null は未分類)、Item=リポカード、DropZone=空セクションの落とし所。 */
private sealed interface EditSlot {
    data class Header(val group: String?) : EditSlot
    data class Item(val repo: Repo) : EditSlot
    data class DropZone(val group: String) : EditSlot
}

/** repos と groups から表示スロット列を作る。未分類を先頭に、続けて groups の順でセクション化する。
 *  空セクションには DropZone を1枚入れて、ドラッグの落とし所(=的)を大きくする。 */
private fun buildSlots(repos: List<Repo>, groups: List<String>): List<EditSlot> {
    val byGroup = repos.groupBy { it.groupName }
    val out = ArrayList<EditSlot>()
    fun section(headerGroup: String?, key: String) {
        out.add(EditSlot.Header(headerGroup))
        val items = (byGroup[key] ?: emptyList()).sortedBy { it.sortOrder }
        if (items.isEmpty()) out.add(EditSlot.DropZone(key))
        else items.forEach { out.add(EditSlot.Item(it)) }
    }
    section(null, "") // 未分類(先頭)
    for (g in groups) section(g, g)
    return out
}

/** スロット列を表示順の Repo 列に変換。各 Item には直前の見出しのグループを割り当てる(DropZone は無視)。 */
private fun slotsToRepos(slots: List<EditSlot>): List<Repo> {
    val out = ArrayList<Repo>()
    var current = ""
    for (s in slots) when (s) {
        is EditSlot.Header -> current = s.group ?: ""
        is EditSlot.Item -> out.add(s.repo.copy(groupName = current))
        is EditSlot.DropZone -> Unit
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoEditScreen(
    repos: List<Repo>,
    groups: List<String>,
    /** D&D 結果(表示順＋ドロップ先で更新済みの groupName)を保存する。 */
    onReorderAndGroup: (List<Repo>) -> Unit,
    onSetColor: (Repo, RepoColor) -> Unit,
    onDelete: (Repo) -> Unit,
    /** ローカル clone が壊れたときに作り直す（削除→再 clone）。 */
    onReclone: (Repo) -> Unit,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val slots = remember { mutableStateListOf<EditSlot>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf<Repo?>(null) }
    var confirmReclone by remember { mutableStateOf<Repo?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val itemPitch = with(density) { (ITEM_HEIGHT + ROW_SPACING).toPx() }
    val headerPitch = with(density) { (HEADER_HEIGHT + ROW_SPACING).toPx() }
    fun pitchOf(slot: EditSlot) = if (slot is EditSlot.Header) headerPitch else itemPitch // DropZone は Item と同じ高さ

    // ドラッグ中以外は最新状態へ同期(追加/削除/色/グループ変更の反映)。
    LaunchedEffect(repos, groups) {
        if (draggingId == null) {
            slots.clear()
            slots.addAll(buildSlots(repos, groups))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("リポジトリを編集") },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "リポジトリを追加")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            itemsIndexed(
                slots,
                key = { _, s ->
                    when (s) {
                        is EditSlot.Header -> "h:${s.group ?: "_none"}"
                        is EditSlot.Item -> "i:${s.repo.id}"
                        is EditSlot.DropZone -> "z:${s.group}"
                    }
                },
            ) { _, slot ->
                when (slot) {
                    is EditSlot.Header -> GroupHeader(
                        group = slot.group,
                        onRename = { slot.group?.let { renameTarget = it } },
                        onDelete = { slot.group?.let { onDeleteGroup(it) } },
                    )
                    is EditSlot.DropZone -> DropZoneRow()
                    is EditSlot.Item -> {
                        val repo = slot.repo
                        val dragging = repo.id == draggingId
                        RepoEditCard(
                            repo = repo,
                            dragging = dragging,
                            dragOffset = if (dragging) dragOffset else 0f,
                            onSetColor = { onSetColor(repo, it) },
                            onDelete = { confirmDelete = repo },
                            onReclone = { confirmReclone = repo },
                            dragModifier = Modifier.pointerInput(repo.id) {
                                detectDragGestures(
                                    onDragStart = { draggingId = repo.id; dragOffset = 0f },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        onReorderAndGroup(slotsToRepos(slots))
                                    },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        // 1ステップずつ隣のスロットと入れ替える(見出しも跨ぐ=所属変更)。
                                        // index 0 は未分類見出しで固定なので、Item は index>=1 に留める。
                                        while (true) {
                                            val from = slots.indexOfFirst {
                                                it is EditSlot.Item && it.repo.id == draggingId
                                            }
                                            if (from < 0) break
                                            if (from < slots.lastIndex && dragOffset > pitchOf(slots[from + 1]) / 2) {
                                                val p = pitchOf(slots[from + 1])
                                                slots.add(from + 1, slots.removeAt(from))
                                                dragOffset -= p
                                                continue
                                            }
                                            if (from >= 2 && dragOffset < -pitchOf(slots[from - 1]) / 2) {
                                                val p = pitchOf(slots[from - 1])
                                                slots.add(from - 1, slots.removeAt(from))
                                                dragOffset += p
                                                continue
                                            }
                                            break
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
            item(key = "_add_group") {
                TextButton(
                    onClick = { addDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  グループを追加")
                }
            }
        }
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("リポジトリを削除") },
            text = { Text("「${target.name}」を削除します。clone データと保存トークンも消えます。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(target) }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("キャンセル") }
            },
        )
    }

    confirmReclone?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmReclone = null },
            title = { Text("再Clone") },
            text = { Text("「${target.name}」のローカル clone を削除して取得し直します。設定とトークンは保持されます。") },
            confirmButton = {
                TextButton(onClick = { confirmReclone = null; onReclone(target) }) { Text("再Clone") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReclone = null }) { Text("キャンセル") }
            },
        )
    }

    if (addDialog) {
        GroupNameDialog(
            title = "新規グループ",
            initial = "",
            onConfirm = { onAddGroup(it); addDialog = false },
            onDismiss = { addDialog = false },
        )
    }
    renameTarget?.let { old ->
        GroupNameDialog(
            title = "グループ名を変更",
            initial = old,
            onConfirm = { onRenameGroup(old, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/** グループ見出し。未分類(group=null)は固定ラベルのみ。通常グループは改名(タップ)＋削除(ゴミ箱)。 */
@Composable
private fun GroupHeader(group: String?, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (group != null) Modifier.clickable(onClick = onRename) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group == null) {
            Text(
                "未分類（「すべて」のみに表示）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                group,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "グループを削除", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 空セクションの落とし所。リポ1枚ぶんの高さの破線エリアで、ドラッグの的を大きくする。 */
@Composable
private fun DropZoneRow() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "ここにドラッグして追加",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** リポ1枚のカード。選んだ色はリポ一覧と同様に左端の縦バーだけで示す(地は淡く塗らない)。
 *  左にドラッグハンドル(セクション間移動)、名前の右に host・ブランチ、下に色ドット、右端に削除。
 *  clone 中はリポ一覧と同様に下部へ進捗バーを出す。 */
@Composable
private fun RepoEditCard(
    repo: Repo,
    dragging: Boolean,
    dragOffset: Float,
    onSetColor: (RepoColor) -> Unit,
    onDelete: () -> Unit,
    onReclone: () -> Unit,
    dragModifier: Modifier,
) {
    val accent = repo.colorTag.accent()
    val cloning = repo.cloneState == CloneState.CLONING
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        // 地色は塗らず既定のまま。色はリポ一覧と同じく左端バーのみで主張を抑える。
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset },
    ) {
      Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // 左端のアクセント色バー(色なしは透明)。リポ一覧と揃える。
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent ?: Color.Transparent))
            // 専用ハンドル: 掴んで上下ドラッグでセクション間を移動。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
                    .width(48.dp)
                    .then(dragModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Menu, contentDescription = "並べ替え・グループ移動")
            }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                // 名前の右に host(github/bitbucket)・ブランチ。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${repo.host.name.lowercase()} · ${repo.branch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RepoColor.entries.forEach { c ->
                        ColorDot(c, selected = repo.colorTag == c, onClick = { onSetColor(c) })
                    }
                }
            }
            // clone 中はメニュー非表示(完了前に消す/作り直さない)。完了後/失敗時のみ ⋮ を出す。
            if (!cloning) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("再Clone") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onReclone() },
                        )
                        DropdownMenuItem(
                            text = { Text("削除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
        // clone 中はリポ一覧と同様にカード下端へ進捗バーを出す。
        if (cloning) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            )
        }
      }
    }
}

@Composable
private fun GroupNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("グループ名") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun ColorDot(color: RepoColor, selected: Boolean, onClick: () -> Unit) {
    val fill = color.accent() ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "色:${color.name}" },
    )
}
