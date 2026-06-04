package com.k1.gitreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.k1.gitreader.GitReaderApplication
import com.k1.gitreader.data.FileEntry
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.RepoRepository
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.git.BranchInfo
import com.k1.gitreader.git.CommitInfo
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 進行中の非同期操作・エラーを画面に伝えるための状態。 */
data class UiStatus(
    val busy: Boolean = false,
    val message: String? = null,
)

class RepoListViewModel(
    private val repository: RepoRepository,
) : ViewModel() {

    val repos: StateFlow<List<Repo>> = repository.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow(UiStatus())
    val status: StateFlow<UiStatus> = _status

    fun addRepo(input: NewRepo, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = "clone 中...")
            val ok = runCatching { repository.addAndClone(input) }
            _status.value = UiStatus(
                busy = false,
                message = ok.exceptionOrNull()?.let { "失敗: ${it.message}" },
            )
            onDone(ok.isSuccess)
        }
    }

    fun sync(repo: Repo) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = "${repo.name} を同期中...")
            val ok = runCatching { repository.sync(repo) }
            _status.value = UiStatus(
                busy = false,
                message = ok.exceptionOrNull()?.let { "同期失敗: ${it.message}" } ?: "同期完了",
            )
        }
    }

    fun delete(repo: Repo) {
        viewModelScope.launch { runCatching { repository.delete(repo) } }
    }

    /** 直近順のリモートブランチ一覧。 */
    suspend fun listBranches(repo: Repo): List<BranchInfo> = repository.listBranches(repo)

    /** ブランチを切り替え（= 指定ブランチで同期）。成功時に更新後 Repo を返す。 */
    fun switchBranch(repo: Repo, branch: String, onDone: (Repo) -> Unit) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = "$branch に切替中...")
            val r = runCatching { repository.sync(repo, branch) }
            _status.value = UiStatus(
                busy = false,
                message = r.exceptionOrNull()?.let { "切替失敗: ${it.message}" },
            )
            r.getOrNull()?.let(onDone)
        }
    }

    // --- ファイルブラウザ / 閲覧 ---
    suspend fun listDir(repo: Repo, relPath: String): List<FileEntry> =
        repository.listDir(repo, relPath)

    suspend fun readFile(repo: Repo, relPath: String): String =
        repository.readText(repo, relPath)

    fun workDirOf(repo: Repo): File = repository.workDir(repo)

    suspend fun fileHistory(repo: Repo, relPath: String): List<CommitInfo> =
        repository.fileHistory(repo, relPath)

    suspend fun fileDiff(repo: Repo, relPath: String, sha: String): String =
        repository.fileDiff(repo, relPath, sha)

    fun clearMessage() {
        _status.value = _status.value.copy(message = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GitReaderApplication
                RepoListViewModel(app.container.repoRepository)
            }
        }
    }
}
