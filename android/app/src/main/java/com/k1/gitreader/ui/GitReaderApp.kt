package com.k1.gitreader.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Parcelable
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.data.db.ThemeMode
import com.k1.gitreader.git.GraphCommit
import kotlinx.parcelize.Parcelize

// プロセス死から復元するため Parcelable(各メンバ @Parcelize)。
private sealed interface Screen : Parcelable {
    @Parcelize data object List : Screen
    @Parcelize data object Add : Screen
    @Parcelize data object Settings : Screen
    @Parcelize data object RepoEdit : Screen
    @Parcelize data class Browse(val repo: Repo, val path: String) : Screen
    @Parcelize data class Search(val repo: Repo) : Screen
    @Parcelize data class Graph(val repo: Repo) : Screen
    @Parcelize data class View(val repo: Repo, val filePath: String, val line: Int? = null) : Screen
    @Parcelize data class History(val repo: Repo, val filePath: String) : Screen
    @Parcelize data class Diff(val repo: Repo, val filePath: String, val sha: String) : Screen
    @Parcelize data class CommitDetail(val repo: Repo, val commit: GraphCommit) : Screen
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
    // 3ペインの左レール(リポ一覧)を畳んでいるか。
    var railCollapsed by rememberSaveable { mutableStateOf(false) }

    fun navigate(s: Screen) = backStack.add(s)
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // System Back と Viewer の戻る矢印を一本化する。
    fun handleBack() {
        val top = backStack.last()
        if (top is Screen.Browse && detailStack.isNotEmpty()) {
            detailStack.removeAt(detailStack.lastIndex) // まず開いているファイルを1つ戻す
            return
        }
        val before = top
        pop()
        // リポ閲覧から抜けたら開いていたファイルを掃除する。
        if (before is Screen.Browse && backStack.last() !is Screen.Browse) detailStack.clear()
    }

    // リポ毎テーマ変更を backStack / detailStack 内の同一リポ全画面へ反映する。
    fun applyThemeUpdate(updated: Repo) {
        for (idx in backStack.indices) {
            when (val s = backStack[idx]) {
                is Screen.Browse -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                is Screen.Graph -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                is Screen.Search -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                is Screen.History -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                is Screen.Diff -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                is Screen.CommitDetail -> if (s.repo.id == updated.id) backStack[idx] = s.copy(repo = updated)
                else -> {}
            }
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
    @Composable
    fun RailPane(selectedRepoId: Long?, onCollapse: (() -> Unit)? = null) {
        RepoListScreen(
            repos = repos,
            status = status,
            onAddClick = { navigate(Screen.Add) },
            onSettings = { navigate(Screen.Settings) },
            onEdit = { navigate(Screen.RepoEdit) },
            onOpen = { navigate(Screen.Browse(it, "")) },
            onSync = vm::sync,
            onMessageShown = vm::clearMessage,
            selectedRepoId = selectedRepoId,
            onCollapse = onCollapse,
        )
    }

    // 3ペインの枠: 左=レール(default テーマ) / 中右=content(repo テーマ)。レール畳み時は細い展開ストリップ。
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
                RailExpandStrip(onExpand = { railCollapsed = false })
                VerticalDivider()
                Box(Modifier.weight(1f)) { GitReaderTheme(repoTheme) { content() } }
            }
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
            loadBitbucketRepos = { account -> vm.listClonableBitbucketRepos(account) },
        )

        Screen.RepoEdit -> RepoEditScreen(
            repos = repos,
            onReorder = vm::saveRepoOrder,
            onSetColor = vm::setRepoColor,
            onDelete = vm::delete,
            onBack = { pop() },
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            repoCount = repos.size,
            onSetTheme = vm::setDefaultTheme,
            onSetFontScale = vm::setFontScale,
            onSetWrapByDefault = vm::setWrapByDefault,
            onSetLinkOpenMode = vm::setLinkOpenMode,
            onSetShowLineNumbers = vm::setShowLineNumbers,
            onSetTableMode = vm::setTableMode,
            onSetStickyHeadings = vm::setStickyHeadings,
            onSetIconSet = vm::setIconSet,
            onClearCache = { vm.clearCache() },
            onBack = { pop() },
        )

        is Screen.Browse -> {
            val repo = current.repo

            @Composable
            fun BrowserPane() {
                FileBrowserScreen(
                    repo = repo,
                    path = current.path,
                    busy = status.busy,
                    loadDir = { vm.listDir(repo, it) },
                    loadBranches = { vm.listBranches(repo) },
                    onSync = { vm.syncNow(repo) },
                    onSearch = { navigate(Screen.Search(repo)) },
                    onGraph = { graphSelected = null; navigate(Screen.Graph(repo)) },
                    onSetTheme = { mode -> vm.setRepoTheme(repo, mode) { updated -> applyThemeUpdate(updated) } },
                    onOpenDir = { navigate(Screen.Browse(repo, it)) },
                    onOpenFile = {
                        if (detailStack.lastOrNull()?.filePath != it) detailStack.add(Screen.View(repo, it))
                    },
                    iconSet = settings.iconSet,
                    onSwitchBranch = { branch ->
                        vm.switchBranch(repo, branch) { updated ->
                            val i = backStack.indexOfLast { it is Screen.Browse }
                            if (i >= 0) {
                                while (backStack.lastIndex > i) backStack.removeAt(backStack.lastIndex)
                                backStack[i] = Screen.Browse(updated, "")
                            }
                            detailStack.clear() // 作業ツリー書換でファイルが変化/消滅しうる
                        }
                    },
                    onBack = { handleBack() },
                )
            }

            @Composable
            fun ViewerPane(file: Screen.View) {
                key(file.repo.id, file.filePath) {
                    FileViewerScreen(
                        repo = file.repo,
                        filePath = file.filePath,
                        workDir = vm.workDirOf(file.repo),
                        loadText = { vm.readFile(file.repo, file.filePath) },
                        fontScale = settings.fontScale.scale,
                        defaultWrap = settings.wrapByDefault,
                        linkOpenMode = settings.linkOpenMode,
                        showLineNumbers = settings.showLineNumbers,
                        tableMode = settings.tableMode,
                        stickyHeadings = settings.stickyHeadings,
                        targetLine = file.line,
                        onHistory = { navigate(Screen.History(file.repo, file.filePath)) },
                        onNavigateToFile = { path -> detailStack.add(Screen.View(file.repo, path)) },
                        onBack = { handleBack() },
                    )
                }
            }

            BoxWithConstraints {
                val three = maxWidth >= THREE_PANE_MIN_WIDTH
                val two = maxWidth >= TWO_PANE_MIN_WIDTH
                val file = detailStack.lastOrNull()

                @Composable
                fun ContentPanes() {
                    if (!two) {
                        if (file != null) ViewerPane(file) else BrowserPane()
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.4f)) { BrowserPane() }
                            VerticalDivider()
                            Box(Modifier.weight(0.6f)) {
                                if (file != null) ViewerPane(file) else SelectPlaceholder("ファイルを選択")
                            }
                        }
                    }
                }

                if (three) {
                    ThreePaneScaffold(repo.id, repo.themeMode) { ContentPanes() }
                } else {
                    GitReaderTheme(repo.themeMode) { ContentPanes() }
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
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.45f)) {
                                GraphPane(selectedSha = graphSelected?.sha, onSelect = { graphSelected = it })
                            }
                            VerticalDivider()
                            Box(Modifier.weight(0.55f)) {
                                val sel = graphSelected
                                if (sel != null) {
                                    key(sel.sha) { CommitDetailContent(sel) { vm.commitDiff(repo, sel.sha) } }
                                } else {
                                    SelectPlaceholder("コミットを選択")
                                }
                            }
                        }
                    }
                }

                if (three) {
                    ThreePaneScaffold(repo.id, repo.themeMode) { ContentPanes() }
                } else {
                    GitReaderTheme(repo.themeMode) { ContentPanes() }
                }
            }
        }

        is Screen.History -> GitReaderTheme(current.repo.themeMode) {
            HistoryScreen(
                filePath = current.filePath,
                loadHistory = { vm.fileHistory(current.repo, current.filePath) },
                onOpenDiff = { sha -> navigate(Screen.Diff(current.repo, current.filePath, sha)) },
                onBack = { pop() },
            )
        }

        is Screen.Diff -> GitReaderTheme(current.repo.themeMode) {
            DiffScreen(
                sha = current.sha,
                loadDiff = { vm.fileDiff(current.repo, current.filePath, current.sha) },
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

        // View は backStack ではなく detailStack で扱う(Browse 分岐内で描画)。到達不能。
        is Screen.View -> Unit
    }
}

/** 中央が空のときのプレースホルダ。 */
@Composable
private fun SelectPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** レールを畳んだときの細い展開ストリップ(≡ で再表示)。 */
@Composable
private fun RailExpandStrip(onExpand: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onExpand) {
            Icon(Icons.Default.Menu, contentDescription = "リポ一覧を表示")
        }
    }
}
