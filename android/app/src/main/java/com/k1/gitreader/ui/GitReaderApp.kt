package com.k1.gitreader.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.git.GraphCommit

private sealed interface Screen {
    data object List : Screen
    data object Add : Screen
    data object Settings : Screen
    data object RepoEdit : Screen
    data class Browse(val repo: Repo, val path: String) : Screen
    data class Search(val repo: Repo) : Screen
    data class Graph(val repo: Repo) : Screen
    data class View(val repo: Repo, val filePath: String, val line: Int? = null) : Screen
    data class History(val repo: Repo, val filePath: String) : Screen
    data class Diff(val repo: Repo, val filePath: String, val sha: String) : Screen
    data class CommitDetail(val repo: Repo, val commit: GraphCommit) : Screen
}

/** 2ペイン化のしきい値(これ以上の幅で左=一覧/右=詳細)。 */
private val TWO_PANE_MIN_WIDTH = 600.dp

@Composable
fun GitReaderApp() {
    val vm: RepoListViewModel = viewModel(factory = RepoListViewModel.Factory)
    val context = LocalContext.current
    val backStack = remember { mutableStateListOf<Screen>(Screen.List) }
    // 開いているファイルは backStack と直交する別スタックで持つ(2ペインのため)。
    val detailStack = remember { mutableStateListOf<Screen.View>() }
    // コミットグラフ2ペインで右に出す選択コミット。
    var graphSelected by remember { mutableStateOf<GraphCommit?>(null) }

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

    when (val current = backStack.last()) {
        Screen.List -> RepoListScreen(
            repos = repos,
            status = status,
            onAddClick = { navigate(Screen.Add) },
            onSettings = { navigate(Screen.Settings) },
            onEdit = { navigate(Screen.RepoEdit) },
            onOpen = { navigate(Screen.Browse(it, "")) },
            onSync = vm::sync,
            onMessageShown = vm::clearMessage,
        )

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

        is Screen.Browse -> GitReaderTheme(current.repo.themeMode) {
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
                        // 既に右に同じファイルが開いていれば重複追加しない。
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
                // filePath をキーに composable ごと作り直す(Mermaid WebView の前ファイル残留を防ぐ)。
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
                val expanded = maxWidth >= TWO_PANE_MIN_WIDTH
                val file = detailStack.lastOrNull()
                if (!expanded) {
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

        is Screen.Graph -> GitReaderTheme(current.repo.themeMode) {
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
                val expanded = maxWidth >= TWO_PANE_MIN_WIDTH
                if (!expanded) {
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
                                key(sel.sha) {
                                    CommitDetailContent(sel) { vm.commitDiff(repo, sel.sha) }
                                }
                            } else {
                                SelectPlaceholder("コミットを選択")
                            }
                        }
                    }
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

/** 右ペインが空のときのプレースホルダ。 */
@Composable
private fun SelectPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
