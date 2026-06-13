package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import jp.lazmix.codeleaf.data.FileNameDisplay
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
    onMemos: () -> Unit,
    onFavorites: () -> Unit,
    /** 現在登録済みのお気に入り relPath 集合(行に★を出すため)。 */
    favoritePaths: Set<String> = emptySet(),
    /** 行の長押しメニューからお気に入り登録/解除をトグルする。 */
    onToggleFavorite: (FileEntry) -> Unit = {},
    /** パンくずのセグメントから任意の階層へ移動(repo ルート相対パス, 空=ルート)。 */
    onNavigateToDir: (String) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    /** ファイル長押しメニューから、そのファイルのコミット履歴へ。 */
    onOpenHistory: (String) -> Unit = {},
    onSwitchBranch: (String) -> Unit,
    /** リポを出てリポ一覧へ。3ペインではレールが担うため null で ← を非表示にする。 */
    onBack: (() -> Unit)?,
    /** 1/2ペインのハンバーガー。非 null なら ← の代わりに ≡ を出しリポ一覧ドロワーを開く。 */
    onMenu: (() -> Unit)? = null,
    /** ひとつ上のディレクトリへ(パスから親を算出して遷移)。ルートでは無効。 */
    onUp: () -> Unit,
    /**
     * 上部に コミットグラフ / お気に入り の常設アクション行(BrowserActionRow)を出すか。
     * 3ペインは左レール(リポ一覧)が同ボタンを担うため false にし、従来どおり ⋮ 内へ収める。
     */
    showRepoActions: Boolean = true,
    iconSet: IconSet = IconSet.MATERIAL,
    fileNameDisplay: FileNameDisplay = FileNameDisplay.WRAP,
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
    // パンくず上の横線に使うリポ色。色なし(NONE)はリスト同様に中立グレー(outline)にする
    // (名前ハッシュの自動色=アバター色は使わない。一覧カードと挙動を合わせる)。
    val accentColor = repo.colorTag.accent() ?: MaterialTheme.colorScheme.outline

    LaunchedEffect(repo.id, path) {
        error = null
        entries = runCatching { loadDir(path) }
            .getOrElse { error = it.message; emptyList() }
    }

    Scaffold(
        // 多ペインではこのペインは画面端でないのに、既定だと横向きの側面ナビバー インセットが
        // 本文へ右パディングとして入り、ファイル一覧の右端に余白が出ていた。bottomBar 側が
        // 下端を自前で padding するので、本文インセットから navigationBars を除外する。
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
          Column {
            TopAppBar(
                title = {
                    if (showRepoActions) {
                        // 1/2ペイン: branch は下の BrowserActionRow に出すのでタイトルはリポ名1行。
                        Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        // 3ペイン(従来どおり): リポ名＋branch ▾ の2行タイトル。
                        Column {
                            Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${repo.branch} ▾",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable(enabled = !locked) { showBranchSheet = true },
                            )
                        }
                    }
                },
                navigationIcon = {
                    // 1/2ペイン=≡(リポ一覧ドロワー)、それ以外で onBack があれば ←。階層の上りは下部▲/パンくず。
                    when {
                        onMenu != null -> IconButton(onClick = onMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "リポ一覧")
                        }
                        onBack != null -> BackButton(onBack)
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
                        // 1/2ペインは グラフ/お気に入り を BrowserActionRow に常設するため ⋮ からは省く。
                        // 3ペインは従来どおり ⋮ 内に置く(左レールにもボタンがある)。
                        if (!showRepoActions) {
                            DropdownMenuItem(
                                text = { Text("コミットグラフ") },
                                onClick = { menuExpanded = false; onGraph() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("メモ") },
                            onClick = { menuExpanded = false; onMemos() },
                        )
                        if (!showRepoActions) {
                            DropdownMenuItem(
                                text = { Text("お気に入り") },
                                onClick = { menuExpanded = false; onFavorites() },
                            )
                        }
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
            if (showRepoActions) {
                BrowserActionRow(
                    branch = repo.branch,
                    locked = locked,
                    onBranch = { showBranchSheet = true },
                    onGraph = onGraph,
                    onFavorites = onFavorites,
                )
            }
            // パンくずの上にリポ色の細い横線(左端の縦バーと同色)。
            HorizontalDivider(thickness = 3.dp, color = accentColor)
            PathBreadcrumb(path = path, onNavigate = onNavigateToDir)
          }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            SlimBottomBar {
                // 片手操作用にひとつ上へ。階層飛ばしは上部パンくずから。ルートでは無効。
                IconButton(onClick = onUp, enabled = path.isNotEmpty()) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "ひとつ上へ")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (path.isEmpty()) "ルート" else path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        snackbar.showSnackbar(syncResultMessage(result.exceptionOrNull()))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                    entries!!.isEmpty() -> Text("（空のディレクトリ）", Modifier.padding(16.dp))
                    else -> LazyColumn(Modifier.fillMaxSize().testTag("browserFileList")) {
                        items(entries!!, key = { it.relPath }) { e ->
                            EntryRow(
                                entry = e,
                                iconSet = iconSet,
                                nameDisplay = fileNameDisplay,
                                isFavorite = e.relPath in favoritePaths,
                                onClick = {
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
                                },
                                onToggleFavorite = { onToggleFavorite(e) },
                                // 履歴はファイルのみ(ディレクトリ/サブモジュールは対象外)。
                                onHistory = if (!e.isDir) ({ onOpenHistory(e.relPath) }) else null,
                            )
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

/**
 * 上部の副アクション行。左にブランチ切替(`branch ▾`)、右に コミットグラフ / お気に入りを常設する。
 * リポ名タイトル(TopAppBar)と横幅を奪い合わないよう独立した1行に分け、狭い2ペインでも収まる。
 */
@Composable
private fun BrowserActionRow(
    branch: String,
    locked: Boolean,
    onBranch: () -> Unit,
    onGraph: () -> Unit,
    onFavorites: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$branch ▾",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !locked, onClick = onBranch)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onGraph, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_graph),
                    contentDescription = "コミットグラフ",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onFavorites, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Star, contentDescription = "お気に入り", modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * GitHub 風のパンくず。ルート(ホーム)＋各フォルダ名をセグメント表示し、祖先をタップで
 * その階層へ一気に移動する。現在地は太字・非リンク。深いパスは右端(現在地)へ自動スクロール。
 */
@Composable
private fun PathBreadcrumb(path: String, onNavigate: (String) -> Unit) {
    val parts = if (path.isEmpty()) emptyList() else path.split("/")
    val scroll = rememberScrollState()
    // 内容が伸びた(=階層が深くなった)ら末尾の現在地が見えるよう右端へ。
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "ルート",
                    tint = if (parts.isEmpty()) cs.onSurface else cs.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = parts.isNotEmpty()) { onNavigate("") }
                        .padding(2.dp)
                        .size(18.dp),
                )
                var acc = ""
                parts.forEachIndexed { i, part ->
                    Text("/", color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    acc = if (acc.isEmpty()) part else "$acc/$part"
                    val target = acc
                    val last = i == parts.lastIndex
                    Text(
                        part,
                        color = if (last) cs.onSurface else cs.primary,
                        fontWeight = if (last) FontWeight.Bold else null,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !last) { onNavigate(target) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: FileEntry,
    iconSet: IconSet,
    nameDisplay: FileNameDisplay,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHistory: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val mark = FileIcons.mark(entry.name)
    val clipboard = LocalClipboardManager.current
    var rowMenu by remember { mutableStateOf(false) }

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

    Box {
        Row(
            // タップで開く・長押しでお気に入りトグルのメニューを出す。
            // アクセントバーは drawBehind で左端に全高描画する(IntrinsicSize.Min を使わず行が自然に
            // 全幅へ広がるようにする。これがないと折り返しテキスト＋バッジで名前が極端に潰れ、
            // 行が全幅にならず右端に余白が出ていた)。
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { rowMenu = true })
                .drawBehind {
                    if (barColor != null) drawRect(barColor, size = Size(3.dp.toPx(), size.height))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(3.dp)) // アクセントバー分のオフセット(アイコン位置を全行で揃える)。
            Row(
                Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FileEntryIcon(entry, iconSet)
                FileNameText(
                    text = entry.displayName,
                    mode = nameDisplay,
                    color = textColor,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    modifier = Modifier.weight(1f),
                )
                // 登録済みは小さな★で示す(チップの手前)。
                if (isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "お気に入り",
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // submodule はファイル名でなくフラグで分かるので、AI 等と同様にバッジで明示する。
                // 未取得(取得失敗で空)は赤の「未取得」にして再同期を促す。
                if (entry.isSubmodule) {
                    if (entry.submoduleUnfetched) MarkChip("未取得", cs.error)
                    else MarkChip("SUB", cs.primary)
                }
                chip?.let { (label, color) -> MarkChip(label, color) }
            }
        }
        DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
            onHistory?.let { history ->
                DropdownMenuItem(
                    text = { Text("履歴") },
                    onClick = { rowMenu = false; history() },
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(if (isFavorite) "お気に入りから解除" else "お気に入りに追加") },
                onClick = { rowMenu = false; onToggleFavorite() },
            )
            HorizontalDivider()
            // 名前は実体名(entry.name)、パスはリポルートからの相対(entry.relPath。畳んだ連鎖は最深)。
            DropdownMenuItem(
                text = { Text("ファイル名をコピー") },
                onClick = { rowMenu = false; clipboard.setText(AnnotatedString(entry.name)) },
            )
            DropdownMenuItem(
                text = { Text("ファイルのパスをコピー") },
                onClick = { rowMenu = false; clipboard.setText(AnnotatedString(entry.relPath)) },
            )
        }
    }
}

/**
 * ファイル名を設定に応じて表示する。WRAP=折り返し全表示 / END_ELLIPSIS=末尾を… /
 * MIDDLE_ELLIPSIS=中央を…(先頭と末尾を残す。Compose1.7 に MiddleEllipsis が無いため
 * TextMeasurer＋onSizeChanged で実幅を測り二分探索で詰める。`IntrinsicSize.Min` 行で
 * BoxWithConstraints は使えないため onSizeChanged を採用)。
 */
@Composable
private fun FileNameText(
    text: String,
    mode: FileNameDisplay,
    color: Color,
    fontWeight: FontWeight?,
    fontStyle: FontStyle?,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        FileNameDisplay.WRAP -> Text(
            text, color = color, fontWeight = fontWeight, fontStyle = fontStyle,
            softWrap = true, modifier = modifier,
        )
        FileNameDisplay.END_ELLIPSIS -> Text(
            text, color = color, fontWeight = fontWeight, fontStyle = fontStyle,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
        FileNameDisplay.MIDDLE_ELLIPSIS -> {
            val measurer = rememberTextMeasurer()
            val style = LocalTextStyle.current.merge(
                TextStyle(color = color, fontWeight = fontWeight, fontStyle = fontStyle),
            )
            var widthPx by remember { mutableStateOf(0) }
            val display = remember(text, widthPx, style) {
                fun fits(s: String) =
                    measurer.measure(s, style, softWrap = false, maxLines = 1).size.width <= widthPx
                if (widthPx <= 0 || fits(text)) {
                    text
                } else {
                    var lo = 0
                    var hi = text.length - 1
                    var best = "…"
                    while (lo <= hi) {
                        val keep = (lo + hi) / 2
                        val head = keep / 2
                        val cand = text.take(head) + "…" + text.takeLast(keep - head)
                        if (fits(cand)) { best = cand; lo = keep + 1 } else { hi = keep - 1 }
                    }
                    best
                }
            }
            Text(
                display, style = style, maxLines = 1, softWrap = false,
                modifier = modifier.onSizeChanged { if (it.width != widthPx) widthPx = it.width },
            )
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
internal fun FileEntryIcon(entry: FileEntry, iconSet: IconSet) {
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
