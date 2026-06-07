package jp.lazmix.codeleaf.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Parcelable
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.ThemeMode
import jp.lazmix.codeleaf.git.CommitInfo
import jp.lazmix.codeleaf.git.GraphCommit
import kotlinx.parcelize.Parcelize

// プロセス死から復元するため Parcelable(各メンバ @Parcelize)。
private sealed interface Screen : Parcelable {
    /** repo を持つ画面の共通口。テーマ変更時に backStack/detailStack 内を一括差し替えするのに使う。 */
    sealed interface WithRepo : Screen {
        val repo: Repo
        fun withRepo(updated: Repo): WithRepo
    }
    @Parcelize data object List : Screen
    @Parcelize data object Add : Screen
    @Parcelize data object Settings : Screen
    @Parcelize data object RepoEdit : Screen
    @Parcelize data class Browse(override val repo: Repo, val path: String) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Search(override val repo: Repo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Graph(override val repo: Repo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class View(override val repo: Repo, val filePath: String, val line: Int? = null) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class History(override val repo: Repo, val filePath: String) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Diff(override val repo: Repo, val filePath: String, val commit: CommitInfo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class CommitDetail(override val repo: Repo, val commit: GraphCommit) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Memos(override val repo: Repo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class MemoDetail(override val repo: Repo, val memoId: Long, val memoTitle: String) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
}

/** 2ペイン(左=一覧/右=詳細)・3ペイン(左=リポ一覧/中=一覧/右=詳細)のしきい値。 */
private val TWO_PANE_MIN_WIDTH = 600.dp
private val THREE_PANE_MIN_WIDTH = 960.dp

@Composable
fun GitReaderApp() {
    val vm: RepoListViewModel = viewModel(factory = RepoListViewModel.Factory)
    val context = LocalContext.current
    // backStack/detailStack/graphSelected はプロセス死から復元する(rememberSaveable + @Parcelize)。
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { saved -> (saved.ifEmpty { listOf(Screen.List) }).toMutableStateList() },
        ),
    ) { mutableStateListOf<Screen>(Screen.List) }
    // 開いているファイルは backStack と直交する別スタックで持つ(2/3ペインのため)。
    val detailStack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen.View>, Screen.View>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<Screen.View>() }
    // コミットグラフ2/3ペインで右に出す選択コミット。
    var graphSelected by rememberSaveable { mutableStateOf<GraphCommit?>(null) }
    // ファイル履歴2/3ペインで右に出す選択コミット(CommitInfo は非Parcelableのため非保存・回転は維持)。
    var historySelected by remember { mutableStateOf<CommitInfo?>(null) }
    // 3ペインの左レール(リポ一覧)を畳んでいるか。
    var railCollapsed by rememberSaveable { mutableStateOf(false) }
    // 集中モード: ファイルを開いている間、レールと一覧を隠してビューアを全幅にする(1/2/3ペイン共通)。
    var focusMode by rememberSaveable { mutableStateOf(false) }
    // コミットグラフ2/3ペインでコミット一覧ペインを畳んで詳細を全幅にしているか。
    var graphListCollapsed by rememberSaveable { mutableStateOf(false) }
    // ファイル履歴2/3ペインでコミット一覧ペインを畳んで差分を全幅にしているか。
    var historyListCollapsed by rememberSaveable { mutableStateOf(false) }

    fun navigate(s: Screen) = backStack.add(s)
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // System Back と Viewer の戻る矢印を一本化する。
    fun handleBack() {
        val top = backStack.last()
        if (top is Screen.Browse && detailStack.isNotEmpty()) {
            // 集中モード中はまず集中を解除する(ファイルは開いたまま・レールと一覧を戻す)。
            if (focusMode) { focusMode = false; return }
            detailStack.removeAt(detailStack.lastIndex) // 次に開いているファイルを1つ戻す
            return
        }
        val before = top
        pop()
        // リポ閲覧から抜けたら開いていたファイルを掃除する。
        if (before is Screen.Browse && backStack.last() !is Screen.Browse) {
            detailStack.clear()
            focusMode = false
        }
    }

    // メモのエントリから該当ファイルの行を開く。メモ画面を畳んで Browse に戻り(無ければ作る)、
    // ビューア(detailStack)に View を積む。
    fun openFileAt(repo: Repo, path: String, line: Int) {
        while (backStack.size > 1 && backStack.last() !is Screen.Browse) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack.last() !is Screen.Browse) {
            backStack.add(Screen.Browse(repo, path.substringBeforeLast('/', "")))
        }
        detailStack.add(Screen.View(repo, path, line))
    }

    // パンくずから任意の階層へ。スタックに同じ Browse があればそこまで戻り(GitHub 風の上り)、
    // 無ければ現在の Browse を置換する。開いているファイル(detailStack)はそのまま。
    fun navigateToDir(repo: Repo, path: String) {
        val idx = backStack.indexOfLast { it is Screen.Browse && it.repo.id == repo.id && it.path == path }
        if (idx >= 0) {
            while (backStack.lastIndex > idx) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack[backStack.lastIndex] = Screen.Browse(repo, path)
        }
    }

    // リポを出てリポ一覧へ戻る(Browse チェーンを畳む)。ブラウザ ← の動作。
    fun leaveRepo() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        detailStack.clear()
        focusMode = false
    }

    // リポ毎テーマ変更を backStack / detailStack 内の同一リポ全画面へ反映する。
    fun applyThemeUpdate(updated: Repo) {
        for (idx in backStack.indices) {
            val s = backStack[idx]
            if (s is Screen.WithRepo && s.repo.id == updated.id) backStack[idx] = s.withRepo(updated)
        }
        for (idx in detailStack.indices) {
            val s = detailStack[idx]
            if (s.repo.id == updated.id) detailStack[idx] = s.copy(repo = updated)
        }
    }

    BackHandler(enabled = backStack.size > 1 || detailStack.isNotEmpty()) { handleBack() }

    val repos by vm.repos.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()

    // 左レール(リポ一覧)。List 全画面・3ペインの左で共有する。
    // グループ一覧 = 定義済み(settings, 表示順) ＋ 念のため未登録のリポ所属名(末尾)。
    val allGroups = run {
        val orphans = repos.map { it.groupName }.filter { it.isNotBlank() && it !in settings.groups }.distinct().sorted()
        settings.groups + orphans
    }

    @Composable
    fun RailPane(selectedRepoId: Long?, onCollapse: (() -> Unit)? = null) {
        // 選択中グループが消えていたら「すべて」に退避。
        val selectedGroup = settings.selectedGroup.takeIf { it.isNotEmpty() && it in allGroups } ?: ""
        val shownRepos = if (selectedGroup.isEmpty()) repos else repos.filter { it.groupName == selectedGroup }
        RepoListScreen(
            repos = shownRepos,
            status = status,
            onAddClick = { navigate(Screen.Add) },
            onSettings = { navigate(Screen.Settings) },
            onEdit = { navigate(Screen.RepoEdit) },
            onOpen = { navigate(Screen.Browse(it, "")) },
            onOpenGraph = { graphSelected = null; navigate(Screen.Graph(it)) },
            onSync = vm::sync,
            onMessageShown = vm::clearMessage,
            groups = allGroups,
            selectedGroup = selectedGroup,
            onSelectGroup = vm::setSelectedGroup,
            selectedRepoId = selectedRepoId,
            onCollapse = onCollapse,
        )
    }

    // レール畳み時の細いアイコンレール: ≡(展開) / リポのアバター縦並び / 下に編集(or＋)・設定。
    @Composable
    fun IconRail(selectedRepoId: Long?) {
        GitReaderTheme(settings.defaultTheme) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxHeight().width(64.dp)) {
                Column(
                    Modifier.fillMaxHeight().statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ≡ は TopAppBar(64dp)中央に合わせ、展開時の「畳む<」と縦位置を揃える。
                    Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { railCollapsed = false }) {
                            Icon(Icons.Default.Menu, contentDescription = "リポ一覧を表示")
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repos.forEach { r ->
                            RepoAvatar(
                                repo = r,
                                selected = r.id == selectedRepoId,
                                onClick = { navigate(Screen.Browse(r, "")) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Column(
                        Modifier.navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (repos.isEmpty()) {
                            IconButton(onClick = { navigate(Screen.Add) }) {
                                Icon(Icons.Default.Add, contentDescription = "リポジトリを追加")
                            }
                        } else {
                            IconButton(onClick = { navigate(Screen.RepoEdit) }) {
                                Icon(Icons.Default.Create, contentDescription = "リポジトリを編集")
                            }
                        }
                        IconButton(onClick = { navigate(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                }
            }
        }
    }

    // 3ペインの枠: 左=レール(default テーマ) / 中右=content(repo テーマ)。レール畳み時はアイコンレール。
    @Composable
    fun ThreePaneScaffold(selectedRepoId: Long?, repoTheme: ThemeMode, content: @Composable () -> Unit) {
        Row(Modifier.fillMaxSize()) {
            if (!railCollapsed) {
                Box(Modifier.weight(0.25f)) {
                    GitReaderTheme(settings.defaultTheme) {
                        RailPane(selectedRepoId, onCollapse = { railCollapsed = true })
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(0.75f)) { GitReaderTheme(repoTheme) { content() } }
            } else {
                IconRail(selectedRepoId)
                VerticalDivider()
                Box(Modifier.weight(1f)) { GitReaderTheme(repoTheme) { content() } }
            }
        }
    }

    // リポ詳細系画面の共通ホスト: 3ペインなら左レール付き、未満ならリポ毎テーマで全画面。
    @Composable
    fun RepoPaneHost(three: Boolean, repo: Repo, content: @Composable () -> Unit) {
        if (three) {
            ThreePaneScaffold(repo.id, repo.themeMode) { content() }
        } else {
            GitReaderTheme(repo.themeMode) { content() }
        }
    }

    when (val current = backStack.last()) {
        Screen.List -> BoxWithConstraints {
            if (maxWidth >= THREE_PANE_MIN_WIDTH) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.25f)) { RailPane(selectedRepoId = null) }
                    VerticalDivider()
                    Box(Modifier.weight(0.75f)) { SelectPlaceholder("リポジトリを選択") }
                }
            } else {
                RailPane(selectedRepoId = null)
            }
        }

        Screen.Add -> AddRepoScreen(
            status = status,
            defaultTheme = settings.defaultTheme,
            onBack = { pop() },
            onSubmit = { input -> vm.addRepo(input) { ok -> if (ok) pop() } },
            bitbucketOAuthAvailable = vm.bitbucketOAuthAvailable,
            onStartBitbucketOAuth = {
                vm.startBitbucketOAuth()?.let { req ->
                    CustomTabsIntent.Builder().build().launchUrl(context, req.url.toUri())
                }
            },
            oauthResult = vm.oauthResult,
            githubOAuthAvailable = vm.githubOAuthAvailable,
            requestGitHubDeviceCode = { vm.requestGitHubDeviceCode() },
            pollGitHubToken = { code -> vm.pollGitHubToken(code) },
            onOpenUrl = { url -> CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) },
            loadOAuthRepos = { account -> vm.listClonableRepos(account) },
            rememberedAccount = { host -> vm.rememberedOAuthAccount(host) },
            onOAuthLogin = { account -> vm.rememberOAuthLogin(account) },
        )

        Screen.RepoEdit -> RepoEditScreen(
            repos = repos,
            groups = allGroups,
            onReorderAndGroup = vm::saveRepoGroupsAndOrder,
            onSetColor = vm::setRepoColor,
            onDelete = vm::delete,
            onAddGroup = vm::addGroup,
            onRenameGroup = vm::renameGroup,
            onDeleteGroup = vm::deleteGroup,
            onAdd = { navigate(Screen.Add) },
            onBack = { pop() },
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            repoCount = repos.size,
            onSetTheme = vm::setDefaultTheme,
            onSetFontScale = vm::setFontScale,
            onSetWrapByDefault = vm::setWrapByDefault,
            onSetDiffWrap = vm::setDiffWrap,
            onSetLinkOpenMode = vm::setLinkOpenMode,
            onSetShowLineNumbers = vm::setShowLineNumbers,
            onSetTableMode = vm::setTableMode,
            onSetStickyHeadings = vm::setStickyHeadings,
            onSetCollapseFolders = vm::setCollapseFolders,
            onSetFileNameDisplay = vm::setFileNameDisplay,
            onSetIconSet = vm::setIconSet,
            onClearCache = { vm.clearCache() },
            onBack = { pop() },
        )

        is Screen.Browse -> {
            val repo = current.repo

            // ひとつ上のディレクトリへ。直下が親なら pop、非線形なら親に置換。
            fun goUp() {
                val parent = current.path.substringBeforeLast('/', "")
                val below = backStack.getOrNull(backStack.lastIndex - 1)
                if (below is Screen.Browse && below.repo.id == repo.id && below.path == parent) {
                    backStack.removeAt(backStack.lastIndex)
                } else {
                    backStack[backStack.lastIndex] = Screen.Browse(repo, parent)
                }
            }

            @Composable
            fun BrowserPane(threePane: Boolean) {
                // ← の挙動:
                //  サブフォルダ: どのペインでもひとつ上の階層へ。
                //  ルート: 3ペインのみ非表示(レールがリポ切替/退出を担う)、1/2ペインはリポ一覧へ。
                val backAction: (() -> Unit)? = when {
                    current.path.isNotEmpty() -> ({ goUp() })
                    threePane -> null
                    else -> ({ leaveRepo() })
                }
                FileBrowserScreen(
                    repo = repo,
                    path = current.path,
                    busy = status.busy,
                    loadDir = { vm.listDir(repo, it) },
                    loadBranches = { vm.listBranches(repo) },
                    onSync = { vm.syncNow(repo) },
                    onSearch = { navigate(Screen.Search(repo)) },
                    onGraph = { graphSelected = null; navigate(Screen.Graph(repo)) },
                    onMemos = { navigate(Screen.Memos(repo)) },
                    onNavigateToDir = { target -> navigateToDir(repo, target) },
                    onSetTheme = { mode -> vm.setRepoTheme(repo, mode) { updated -> applyThemeUpdate(updated) } },
                    onOpenDir = { navigate(Screen.Browse(repo, it)) },
                    onOpenFile = {
                        if (detailStack.lastOrNull()?.filePath != it) detailStack.add(Screen.View(repo, it))
                    },
                    iconSet = settings.iconSet,
                    fileNameDisplay = settings.fileNameDisplay,
                    onSwitchBranch = { branch ->
                        vm.switchBranch(repo, branch) { updated ->
                            val i = backStack.indexOfLast { it is Screen.Browse }
                            if (i >= 0) {
                                while (backStack.lastIndex > i) backStack.removeAt(backStack.lastIndex)
                                backStack[i] = Screen.Browse(updated, "")
                            }
                            detailStack.clear() // 作業ツリー書換でファイルが変化/消滅しうる
                            focusMode = false
                        }
                    },
                    onBack = backAction,
                    onUp = { goUp() },
                )
            }

            @Composable
            fun ViewerPane(file: Screen.View, showBack: Boolean) {
                key(file.repo.id, file.filePath) {
                    val repoMemos by vm.observeMemos(file.repo.id).collectAsState(initial = emptyList())
                    FileViewerScreen(
                        repo = file.repo,
                        filePath = file.filePath,
                        workDir = vm.workDirOf(file.repo),
                        loadText = { cs, max -> vm.readFile(file.repo, file.filePath, cs, max) },
                        probeFile = { vm.probeFile(file.repo, file.filePath) },
                        fontScale = settings.fontScale.scale,
                        defaultWrap = settings.wrapByDefault,
                        onToggleWrap = vm::setWrapByDefault,
                        linkOpenMode = settings.linkOpenMode,
                        showLineNumbers = settings.showLineNumbers,
                        tableMode = settings.tableMode,
                        stickyHeadings = settings.stickyHeadings,
                        targetLine = file.line,
                        onHistory = { historySelected = null; navigate(Screen.History(file.repo, file.filePath)) },
                        onMemos = { navigate(Screen.Memos(file.repo)) },
                        onNavigateToFile = { path -> detailStack.add(Screen.View(file.repo, path)) },
                        onBack = { handleBack() },
                        showBack = showBack,
                        loadSiblings = {
                            val dir = file.filePath.substringBeforeLast('/', "")
                            vm.listDir(file.repo, dir)
                                .filter { !it.isDir && !it.isSubmodule && !it.isLfs }
                                .map { it.relPath }
                        },
                        onOpenSibling = { path ->
                            if (detailStack.isNotEmpty()) {
                                detailStack[detailStack.lastIndex] = Screen.View(file.repo, path)
                            }
                        },
                        memos = repoMemos,
                        onAddMemoEntry = { memoId, ls, le, quote, comment ->
                            vm.addMemoEntry(memoId, file.filePath, ls, le, quote, comment)
                        },
                        onCreateMemoWithEntry = { title, ls, le, quote, comment ->
                            vm.createMemoWithEntry(file.repo.id, title, file.filePath, ls, le, quote, comment)
                        },
                    )
                }
            }

            // 集中モードの開閉ハンドル(ビューア左下)。集中ON=全幅 / OFF=レール+一覧。
            @Composable
            fun BoxScope.FocusHandle() {
                PaneToggleHandle(
                    collapsed = focusMode,
                    onToggle = { focusMode = !focusMode },
                    expandLabel = "一覧とレールを表示",
                    collapseLabel = "集中モード(全幅)",
                    // 下部バー(目次/送り)と重ならないよう少し上に。
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(bottom = 96.dp)
                        .offset(x = if (focusMode) 4.dp else (-20).dp),
                )
            }

            // ビューアと集中ハンドルを重ねた右ペイン。未選択時はプレースホルダ。
            @Composable
            fun ViewerArea(file: Screen.View?, showBack: Boolean) {
                Box(Modifier.fillMaxSize()) {
                    if (file != null) ViewerPane(file, showBack = showBack)
                    else SelectPlaceholder("ファイルを選択")
                    if (file != null) FocusHandle()
                }
            }

            BoxWithConstraints {
                val three = maxWidth >= THREE_PANE_MIN_WIDTH
                val two = maxWidth >= TWO_PANE_MIN_WIDTH
                val file = detailStack.lastOrNull()
                val focused = focusMode && file != null

                when {
                    // 集中モード: レール・一覧を隠して全幅ビューア。
                    focused -> GitReaderTheme(repo.themeMode) {
                        Box(Modifier.fillMaxSize()) {
                            ViewerPane(file!!, showBack = false)
                            FocusHandle()
                        }
                    }
                    // 3ペイン: 既存のレール(展開/アイコン) + 一覧 + ビューア。
                    three -> ThreePaneScaffold(repo.id, repo.themeMode) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.3f)) { BrowserPane(threePane = true) }
                            VerticalDivider()
                            Box(Modifier.weight(0.7f)) { ViewerArea(file, showBack = false) }
                        }
                    }
                    // 1/2ペイン: アイコンレール常設(レール=default テーマ / 中右=repo テーマ)。
                    else -> Row(Modifier.fillMaxSize()) {
                        IconRail(repo.id)
                        VerticalDivider()
                        Box(Modifier.weight(1f)) {
                            GitReaderTheme(repo.themeMode) {
                                if (two) {
                                    Row(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(0.4f)) { BrowserPane(threePane = false) }
                                        VerticalDivider()
                                        Box(Modifier.weight(0.6f)) { ViewerArea(file, showBack = false) }
                                    }
                                } else if (file != null) {
                                    // 1ペインでファイル表示: ←=閉じる + 集中ハンドルでレールを隠せる。
                                    Box(Modifier.fillMaxSize()) {
                                        ViewerPane(file, showBack = true)
                                        FocusHandle()
                                    }
                                } else {
                                    BrowserPane(threePane = false)
                                }
                            }
                        }
                    }
                }
            }
        }

        is Screen.Search -> GitReaderTheme(current.repo.themeMode) {
            SearchScreen(
                repoName = current.repo.name,
                loadCorpus = { vm.loadSearchCorpus(current.repo) },
                onOpenFile = { path, line ->
                    detailStack.add(Screen.View(current.repo, path, line))
                    pop() // Search を閉じて Browse(+右ペイン) に戻る
                },
                onBack = { pop() },
            )
        }

        is Screen.Graph -> {
            val repo = current.repo

            @Composable
            fun GraphPane(selectedSha: String?, onSelect: (GraphCommit) -> Unit) {
                CommitGraphScreen(
                    repoName = repo.name,
                    loadGraph = { vm.commitGraph(repo) },
                    onBack = { handleBack() },
                    selectedSha = selectedSha,
                    onSelectCommit = onSelect,
                    onSync = { vm.syncNow(repo) },
                )
            }

            BoxWithConstraints {
                val three = maxWidth >= THREE_PANE_MIN_WIDTH
                val two = maxWidth >= TWO_PANE_MIN_WIDTH

                @Composable
                fun ContentPanes() {
                    if (!two) {
                        GraphPane(selectedSha = null, onSelect = { navigate(Screen.CommitDetail(repo, it)) })
                    } else {
                        val sel = graphSelected
                        // コミット選択中だけ一覧を畳める(未選択時は一覧を出す)。
                        val showList = sel == null || !graphListCollapsed
                        Row(Modifier.fillMaxSize()) {
                            if (showList) {
                                Box(Modifier.weight(0.45f)) {
                                    GraphPane(selectedSha = sel?.sha, onSelect = { graphSelected = it })
                                }
                                VerticalDivider()
                            }
                            Box(Modifier.weight(0.55f)) {
                                if (sel != null) {
                                    key(sel.sha) { CommitDetailContent(sel) { vm.commitDiff(repo, sel.sha) } }
                                } else {
                                    SelectPlaceholder("コミットを選択")
                                }
                                // 区切り線下部の開閉ハンドル(片手でコミット一覧を畳む/戻す)。選択中のみ。
                                if (sel != null) {
                                    PaneToggleHandle(
                                        collapsed = graphListCollapsed,
                                        onToggle = { graphListCollapsed = !graphListCollapsed },
                                        // diff 下部の折り返しバーと重ならないよう少し上に。
                                        modifier = Modifier.align(Alignment.BottomStart)
                                            .padding(bottom = 72.dp)
                                            .offset(x = if (graphListCollapsed) 4.dp else (-20).dp),
                                    )
                                }
                            }
                        }
                    }
                }

                RepoPaneHost(three, repo) { ContentPanes() }
            }
        }

        is Screen.History -> {
            val repo = current.repo
            val filePath = current.filePath

            @Composable
            fun HistoryPane(selSha: String?, onSelect: (CommitInfo) -> Unit) {
                HistoryScreen(
                    filePath = filePath,
                    loadHistory = { vm.fileHistory(repo, filePath) },
                    onSelectCommit = onSelect,
                    onBack = { pop() },
                    selectedSha = selSha,
                    onSync = { vm.syncNow(repo) },
                )
            }

            BoxWithConstraints {
                val three = maxWidth >= THREE_PANE_MIN_WIDTH
                val two = maxWidth >= TWO_PANE_MIN_WIDTH

                @Composable
                fun ContentPanes() {
                    if (!two) {
                        HistoryPane(selSha = null, onSelect = { navigate(Screen.Diff(repo, filePath, it)) })
                    } else {
                        val sel = historySelected
                        // コミット選択中だけ一覧を畳める(未選択時は一覧を出す)。
                        val showList = sel == null || !historyListCollapsed
                        Row(Modifier.fillMaxSize()) {
                            if (showList) {
                                Box(Modifier.weight(0.45f)) {
                                    HistoryPane(selSha = sel?.sha, onSelect = { historySelected = it })
                                }
                                VerticalDivider()
                            }
                            Box(Modifier.weight(0.55f)) {
                                if (sel != null) {
                                    key(sel.sha) { FileDiffPane(sel) { vm.fileDiff(repo, filePath, sel.sha) } }
                                } else {
                                    SelectPlaceholder("コミットを選択")
                                }
                                // 区切り線下部の開閉ハンドル(片手でコミット一覧を畳む/戻す)。選択中のみ。
                                if (sel != null) {
                                    PaneToggleHandle(
                                        collapsed = historyListCollapsed,
                                        onToggle = { historyListCollapsed = !historyListCollapsed },
                                        // diff 下部の折り返しバーと重ならないよう少し上に。
                                        modifier = Modifier.align(Alignment.BottomStart)
                                            .padding(bottom = 72.dp)
                                            .offset(x = if (historyListCollapsed) 4.dp else (-20).dp),
                                    )
                                }
                            }
                        }
                    }
                }

                RepoPaneHost(three, repo) { ContentPanes() }
            }
        }

        is Screen.Diff -> GitReaderTheme(current.repo.themeMode) {
            DiffScreen(
                commit = current.commit,
                loadDiff = { vm.fileDiff(current.repo, current.filePath, current.commit.sha) },
                onBack = { pop() },
            )
        }

        is Screen.CommitDetail -> GitReaderTheme(current.repo.themeMode) {
            CommitDetailScreen(
                commit = current.commit,
                loadDiff = { vm.commitDiff(current.repo, current.commit.sha) },
                onBack = { handleBack() },
            )
        }

        is Screen.Memos -> GitReaderTheme(current.repo.themeMode) {
            val memoList by vm.observeMemos(current.repo.id).collectAsState(initial = emptyList())
            MemosScreen(
                repoName = current.repo.name,
                memos = memoList,
                onOpenMemo = { navigate(Screen.MemoDetail(current.repo, it.id, it.title)) },
                onCreateMemo = { title -> vm.createMemo(current.repo.id, title) },
                onRenameMemo = { id, title -> vm.renameMemo(id, title) },
                onDeleteMemo = { id -> vm.deleteMemo(id) },
                loadEntries = { id -> vm.getMemoEntries(id) },
                onBack = { pop() },
            )
        }

        is Screen.MemoDetail -> GitReaderTheme(current.repo.themeMode) {
            val entries by vm.observeMemoEntries(current.memoId).collectAsState(initial = emptyList())
            MemoDetailScreen(
                repoName = current.repo.name,
                memoTitle = current.memoTitle,
                entries = entries,
                onOpenEntry = { e -> openFileAt(current.repo, e.filePath, e.lineStart) },
                onDeleteEntry = { vm.deleteMemoEntry(it) },
                onRenameMemo = { vm.renameMemo(current.memoId, it) },
                onDeleteMemo = { vm.deleteMemo(current.memoId); pop() },
                onBack = { pop() },
            )
        }

        // View は backStack ではなく detailStack で扱う(Browse 分岐内で描画)。到達不能。
        is Screen.View -> Unit
    }
}

/** 区切り線下部に置く、一覧ペインの開閉ハンドル(片手操作用の丸ボタン＋シェブロン)。 */
@Composable
private fun PaneToggleHandle(
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    expandLabel: String = "一覧を表示",
    collapseLabel: String = "一覧を隠す",
) {
    // 本文に被さるので半透明にして主張を抑える(タップは効く)。
    FilledTonalIconButton(onClick = onToggle, modifier = modifier.size(40.dp).alpha(0.6f)) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft,
            contentDescription = if (collapsed) expandLabel else collapseLabel,
        )
    }
}

/** 中央が空のときのプレースホルダ。 */
@Composable
private fun SelectPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

