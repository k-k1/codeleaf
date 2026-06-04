package com.k1.gitreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k1.gitreader.data.db.Repo

private sealed interface Screen {
    data object List : Screen
    data object Add : Screen
    data object Settings : Screen
    data class Browse(val repo: Repo, val path: String) : Screen
    data class Search(val repo: Repo) : Screen
    data class View(val repo: Repo, val filePath: String, val line: Int? = null) : Screen
    data class History(val repo: Repo, val filePath: String) : Screen
    data class Diff(val repo: Repo, val filePath: String, val sha: String) : Screen
}

@Composable
fun GitReaderApp() {
    val vm: RepoListViewModel = viewModel(factory = RepoListViewModel.Factory)
    val backStack = remember { mutableStateListOf<Screen>(Screen.List) }
    fun navigate(s: Screen) = backStack.add(s)
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val repos by vm.repos.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()

    when (val current = backStack.last()) {
        Screen.List -> RepoListScreen(
            repos = repos,
            status = status,
            onAddClick = { navigate(Screen.Add) },
            onSettings = { navigate(Screen.Settings) },
            onOpen = { navigate(Screen.Browse(it, "")) },
            onSync = vm::sync,
            onDelete = vm::delete,
            onMessageShown = vm::clearMessage,
        )

        Screen.Add -> AddRepoScreen(
            status = status,
            defaultTheme = settings.defaultTheme,
            onBack = { pop() },
            onSubmit = { input -> vm.addRepo(input) { ok -> if (ok) pop() } },
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            repoCount = repos.size,
            onSetTheme = vm::setDefaultTheme,
            onSetFontScale = vm::setFontScale,
            onSetWrapByDefault = vm::setWrapByDefault,
            onClearCache = { vm.clearCache() },
            onBack = { pop() },
        )

        is Screen.Browse -> GitReaderTheme(current.repo.themeMode) {
            FileBrowserScreen(
                repo = current.repo,
                path = current.path,
                loadDir = { vm.listDir(current.repo, it) },
                loadBranches = { vm.listBranches(current.repo) },
                onSync = { vm.syncNow(current.repo) },
                onSearch = { navigate(Screen.Search(current.repo)) },
                onOpenDir = { navigate(Screen.Browse(current.repo, it)) },
                onOpenFile = { navigate(Screen.View(current.repo, it)) },
                onSwitchBranch = { branch ->
                    vm.switchBranch(current.repo, branch) { updated ->
                        val i = backStack.indexOfLast { it is Screen.Browse }
                        if (i >= 0) {
                            while (backStack.lastIndex > i) backStack.removeAt(backStack.lastIndex)
                            backStack[i] = Screen.Browse(updated, "")
                        }
                    }
                },
                onBack = { pop() },
            )
        }

        is Screen.Search -> GitReaderTheme(current.repo.themeMode) {
            SearchScreen(
                repoName = current.repo.name,
                loadCorpus = { vm.loadSearchCorpus(current.repo) },
                onOpenFile = { path, line -> navigate(Screen.View(current.repo, path, line)) },
                onBack = { pop() },
            )
        }

        is Screen.View -> GitReaderTheme(current.repo.themeMode) {
            FileViewerScreen(
                repo = current.repo,
                filePath = current.filePath,
                workDir = vm.workDirOf(current.repo),
                loadText = { vm.readFile(current.repo, current.filePath) },
                fontScale = settings.fontScale.scale,
                defaultWrap = settings.wrapByDefault,
                targetLine = current.line,
                onHistory = { navigate(Screen.History(current.repo, current.filePath)) },
                onNavigateToFile = { path -> navigate(Screen.View(current.repo, path)) },
                onBack = { pop() },
            )
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
    }
}
