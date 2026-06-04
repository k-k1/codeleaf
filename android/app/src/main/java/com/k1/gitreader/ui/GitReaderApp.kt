package com.k1.gitreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed interface Screen {
    data object List : Screen
    data object Add : Screen
}

@Composable
fun GitReaderApp() {
    val vm: RepoListViewModel = viewModel(factory = RepoListViewModel.Factory)
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    val repos by vm.repos.collectAsState()
    val status by vm.status.collectAsState()

    when (screen) {
        Screen.List -> RepoListScreen(
            repos = repos,
            status = status,
            onAddClick = { screen = Screen.Add },
            onSync = vm::sync,
            onDelete = vm::delete,
            onMessageShown = vm::clearMessage,
        )

        Screen.Add -> AddRepoScreen(
            status = status,
            onBack = { screen = Screen.List },
            onSubmit = { input ->
                vm.addRepo(input) { ok -> if (ok) screen = Screen.List }
            },
        )
    }
}
