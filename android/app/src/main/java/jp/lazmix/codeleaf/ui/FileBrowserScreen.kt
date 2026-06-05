package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.data.FileEntry
import jp.lazmix.codeleaf.data.IconSet
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    repo: Repo,
    path: String,
    busy: Boolean,
    loadDir: suspend (String) -> List<FileEntry>,
    loadBranches: suspend () -> List<jp.lazmix.codeleaf.git.BranchInfo>,
    onSync: suspend () -> Unit,
    onSearch: () -> Unit,
    onGraph: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSwitchBranch: (String) -> Unit,
    /** リポを出てリポ一覧へ。3ペインではレールが担うため null で ← を非表示にする。 */
    onBack: (() -> Unit)?,
    /** ひとつ上のディレクトリへ(パスから親を算出して遷移)。ルートでは無効。 */
    onUp: () -> Unit,
    iconSet: IconSet = IconSet.MATERIAL,
) {
    var entries by remember(repo.id, path) { mutableStateOf<List<FileEntry>?>(null) }
    var error by remember(repo.id, path) { mutableStateOf<String?>(null) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // ブランチ切替(busy)や同期(refreshing)中は、同一作業ツリーへの並行操作を防ぐためロックする。
    val locked = busy || refreshing

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
                            modifier = Modifier.clickable(enabled = !locked) { showBranchSheet = true },
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
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
                        // タイトルの小さな「branch ▾」が分かりづらいので、切替導線をここにも置く。
                        DropdownMenuItem(
                            text = { Text("ブランチを切り替え（${repo.branch}）") },
                            enabled = !locked,
                            onClick = { menuExpanded = false; showBranchSheet = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("コミットグラフ") },
                            onClick = { menuExpanded = false; onGraph() },
                        )
                        HorizontalDivider()
                        Text(
                            "テーマ",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        val themeLabels = listOf(
                            ThemeMode.SYSTEM to "システム",
                            ThemeMode.LIGHT to "ライト",
                            ThemeMode.DARK to "ダーク",
                        )
                        themeLabels.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text((if (repo.themeMode == mode) "● " else "○ ") + label) },
                                onClick = { menuExpanded = false; onSetTheme(mode) },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            SlimBottomBar {
                // ルート(path 空)では親が無いので無効化。
                IconButton(onClick = onUp, enabled = path.isNotEmpty()) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ひとつ上へ")
                }
                Text(
                    text = "/" + path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    if (busy) return@PullToRefreshBox // 切替中は同期させない
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
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                    entries!!.isEmpty() -> Text("（空のディレクトリ）", Modifier.padding(16.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(entries!!, key = { it.relPath }) { e ->
                            EntryRow(e, iconSet, onClick = {
                                // ロック中はファイル/フォルダを開かない(作業ツリー書換中の読込回避)
                                if (!locked) {
                                    when {
                                        // LFS は実体未取得のため Viewer では開かず、その旨を通知する
                                        e.isLfs -> scope.launch {
                                            snackbar.showSnackbar("Git LFS ファイルです（実体は未取得のため表示できません）")
                                        }
                                        e.isDir -> onOpenDir(e.relPath)
                                        else -> onOpenFile(e.relPath)
                                    }
                                }
                            })
                            HorizontalDivider()
                        }
                    }
                }
            }

            // ブランチ切替中は全面ブロック(タッチを消費)してプログレス表示
            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("ブランチ切替中…", color = Color.White)
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
private fun EntryRow(entry: FileEntry, iconSet: IconSet, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mark = FileIcons.mark(entry.name)

    // 分類ごとの描画スタイル(先頭バー/文字色/字形/チップ)を解決する。
    val barColor: Color? = when (mark) {
        FileMark.AI -> cs.tertiary
        FileMark.SECRET -> cs.error
        else -> null
    }
    val textColor: Color = when (mark) {
        FileMark.AI -> cs.tertiary
        FileMark.SECRET -> cs.error
        FileMark.GENERATED -> cs.onSurfaceVariant.copy(alpha = 0.5f) // 最も減光
        FileMark.DOTFILE -> cs.onSurfaceVariant                      // 少しグレー
        FileMark.DOC, FileMark.NONE -> cs.onSurface
    }
    val fontWeight = if (mark == FileMark.DOC) FontWeight.Bold else null
    val fontStyle = if (mark == FileMark.GENERATED) FontStyle.Italic else null // ドット始まりと区別
    val chip: Pair<String, Color>? = when (mark) {
        FileMark.AI -> "AI" to cs.tertiary
        FileMark.SECRET -> "!" to cs.error
        else -> null
    }

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 先頭アクセントバー(非対象は透明で確保し、アイコン位置を全行で揃える)。
        Box(Modifier.width(3.dp).fillMaxHeight().background(barColor ?: Color.Transparent))
        Row(
            Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FileEntryIcon(entry, iconSet)
            Text(
                entry.name,
                color = textColor,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            chip?.let { (label, color) -> MarkChip(label, color) }
        }
    }
}

/** AI/機密などの分類を示す小さなピル。枠線＋淡い背景でアクセント色を主張しすぎない。 */
@Composable
private fun MarkChip(label: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val ICON_SIZE = 24.dp

@Composable
private fun FileEntryIcon(entry: FileEntry, iconSet: IconSet) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        // submodule は git ロゴで「ネストした git リポジトリ」と分かるようにする
        entry.isSubmodule -> BrandIcon(FileIcons.forKey(iconSet, "git"), "submodule", tint)
        entry.isDir -> Icon(
            painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        // LFS ポインタは「リモート保管(未取得)」を示すクラウドアイコン
        entry.isLfs -> Icon(
            painterResource(R.drawable.ic_lfs),
            contentDescription = "Git LFS",
            tint = tint,
            modifier = Modifier.size(ICON_SIZE),
        )
        else -> BrandIcon(FileIcons.forFile(iconSet, entry.name), null, tint)
    }
}

/**
 * ブランドアイコン(SVG)を描画する。spec が null(未対応 or セット未収録)のときは
 * 汎用ファイルアイコンにフォールバックする。
 */
@Composable
private fun BrandIcon(spec: FileIconSpec?, contentDescription: String?, fallbackTint: Color) {
    if (spec == null) {
        Icon(
            painterResource(R.drawable.ic_file_generic),
            contentDescription = contentDescription,
            tint = fallbackTint,
            modifier = Modifier.size(ICON_SIZE),
        )
        return
    }
    val colorFilter = when (spec.tint) {
        IconTint.NONE -> null
        IconTint.ON_SURFACE -> ColorFilter.tint(fallbackTint)
        IconTint.FIXED -> ColorFilter.tint(Color(spec.color))
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(spec.uri).build(),
        imageLoader = rememberSvgLoader(),
        contentDescription = contentDescription,
        colorFilter = colorFilter,
        modifier = Modifier.size(ICON_SIZE),
    )
}

/**
 * SVG をデコードできる Coil ImageLoader。アイコンのアセットは数十KB と小さいため
 * Application 単位で 1 つあれば十分。Activity の context から remember する。
 */
@Composable
private fun rememberSvgLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}
