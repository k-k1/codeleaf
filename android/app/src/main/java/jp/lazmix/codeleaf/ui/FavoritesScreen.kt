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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.data.FileEntry
import jp.lazmix.codeleaf.data.IconSet
import jp.lazmix.codeleaf.data.db.Favorite

/**
 * リポジトリのお気に入り(ファイル/フォルダ)一覧。各行は親パス(小)＋名前(通常)の2段で、
 * タップで対象へ遷移する。実体が消えた項目はグレーアウトしてタップ無効にし、末尾の ✕ で削除だけ可。
 * 存在判定は [checkExists] を表示時に非同期で行う(未判定の間は存在扱いで描画し、ちらつきを避ける)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    repoName: String,
    favorites: List<Favorite>,
    iconSet: IconSet,
    checkExists: suspend (relPath: String) -> Boolean,
    onOpen: (Favorite) -> Unit,
    onDelete: (id: Long) -> Unit,
    onBack: () -> Unit,
) {
    // relPath → 実体が存在するか。未判定(キー無し)は存在扱いにする。
    val existence = remember(favorites) { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(favorites) {
        favorites.forEach { f -> existence[f.relPath] = checkExists(f.relPath) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("お気に入り", style = MaterialTheme.typography.titleMedium)
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
            )
        },
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "お気に入りはまだありません。ファイル一覧で項目を長押しして追加します。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(favorites, key = { it.id }) { fav ->
                    val exists = existence[fav.relPath] != false // null(未判定)は存在扱い
                    FavoriteRow(
                        fav = fav,
                        iconSet = iconSet,
                        exists = exists,
                        onOpen = { if (exists) onOpen(fav) },
                        onDelete = { onDelete(fav.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    fav: Favorite,
    iconSet: IconSet,
    exists: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val parent = fav.relPath.substringBeforeLast('/', "")
    val name = fav.relPath.substringAfterLast('/')
    // 消失した項目は全体を減光する(✕ だけ通常色で残す)。
    val contentAlpha = if (exists) 1f else 0.4f

    Row(
        Modifier.fillMaxWidth().clickable(enabled = exists, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = 16.dp).alpha(contentAlpha)) {
            FileEntryIcon(FileEntry(name = name, relPath = fav.relPath, isDir = fav.isDir), iconSet)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
            // 同名ファイル混同を避けるため、名前の上に親パスを小さく出す。
            Text(
                text = if (parent.isEmpty()) "（ルート直下）" else parent,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!exists) {
                    Text(
                        text = "  （削除済み）",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "お気に入りから削除", tint = cs.onSurfaceVariant)
        }
    }
}
