package com.k1.gitreader.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.data.db.RepoColor
import kotlin.math.roundToInt

private val ROW_HEIGHT = 88.dp
private val ROW_SPACING = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoEditScreen(
    repos: List<Repo>,
    onReorder: (List<Repo>) -> Unit,
    onSetColor: (Repo, RepoColor) -> Unit,
    onDelete: (Repo) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val items = remember { mutableStateListOf<Repo>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf<Repo?>(null) }
    // 並べ替えの判定は行スペースを含む実ピッチ(行高+間隔)を基準にする。
    val pitchPx = with(LocalDensity.current) { (ROW_HEIGHT + ROW_SPACING).toPx() }

    // ドラッグ中以外は最新の repos に同期(削除・色変更の反映)。
    LaunchedEffect(repos) {
        if (draggingId == null) {
            items.clear()
            items.addAll(repos)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("リポジトリを編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
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
            itemsIndexed(items, key = { _, r -> r.id }) { _, repo ->
                val dragging = repo.id == draggingId
                Card(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (dragging) 8.dp else 1.dp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        // ドラッグハンドル(掴んで上下にドラッグで並べ替え)。
                        // 専用ハンドルなので長押し不要・即ドラッグ開始。タッチ領域は広めに確保する。
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 4.dp)
                                .width(48.dp)
                                .pointerInput(repo.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = repo.id; dragOffset = 0f },
                                        onDragEnd = { draggingId = null; dragOffset = 0f; onReorder(items.toList()) },
                                        onDragCancel = { draggingId = null; dragOffset = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val from = items.indexOfFirst { it.id == draggingId }
                                            if (from >= 0) {
                                                val to = (from + (dragOffset / pitchPx).roundToInt())
                                                    .coerceIn(0, items.size - 1)
                                                if (to != from) {
                                                    items.add(to, items.removeAt(from))
                                                    dragOffset -= (to - from) * pitchPx
                                                }
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "並べ替え")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                repo.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RepoColor.entries.forEach { c ->
                                    ColorDot(c, selected = repo.colorTag == c, onClick = { onSetColor(repo, c) })
                                }
                            }
                        }
                        IconButton(onClick = { confirmDelete = repo }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
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
