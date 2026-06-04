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
    data class Browse(val repo: Repo, val path: String) : Screen
    data class View(val repo: Repo, val filePath: String) : Screen
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

    when (val current = backStack.last()) {
        Screen.List -> RepoListScreen(
            repos = repos,
            status = status,
            onAddClick = { navigate(Screen.Add) },
            onOpen = { navigate(Screen.Browse(it, "")) },
            onSync = vm::sync,
            onDelete = vm::delete,
            onMessageShown = vm::clearMessage,
        )

        Screen.Add -> AddRepoScreen(
            status = status,
            onBack = { pop() },
            onSubmit = { input -> vm.addRepo(input) { ok -> if (ok) pop() } },
        )

        is Screen.Browse -> GitReaderTheme(current.repo.themeMode) {
            FileBrowserScreen(
                repo = current.repo,
                path = current.path,
                loadDir = { vm.listDir(current.repo, it) },
                loadBranches = { vm.listBranches(current.repo) },
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

        is Screen.View -> GitReaderTheme(current.repo.themeMode) {
            FileViewerScreen(
                repo = current.repo,
                filePath = current.filePath,
                workDir = vm.workDirOf(current.repo),
                loadText = { vm.readFile(current.repo, current.filePath) },
                onBack = { pop() },
            )
        }
    }
}
