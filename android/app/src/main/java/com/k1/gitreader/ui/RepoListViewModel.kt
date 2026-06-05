package com.k1.gitreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.k1.gitreader.GitReaderApplication
import com.k1.gitreader.data.AppSettings
import com.k1.gitreader.data.FileEntry
import com.k1.gitreader.data.FontScale
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.RepoRepository
import com.k1.gitreader.data.SettingsStore
import com.k1.gitreader.data.TextFile
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.data.db.ThemeMode
import com.k1.gitreader.data.oauth.AuthorizationRequest
import com.k1.gitreader.data.oauth.BitbucketOAuthService
import com.k1.gitreader.data.oauth.GitHubDeviceCode
import com.k1.gitreader.data.oauth.GitHubDeviceFlow
import com.k1.gitreader.data.oauth.GitHubDeviceFlowService
import com.k1.gitreader.data.oauth.OAuthAccount
import com.k1.gitreader.data.oauth.RemoteRepo
import com.k1.gitreader.git.BranchInfo
import com.k1.gitreader.git.CommitInfo
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 進行中の非同期操作・エラーを画面に伝えるための状態。 */
data class UiStatus(
    val busy: Boolean = false,
    val message: String? = null,
)

class RepoListViewModel(
    private val repository: RepoRepository,
    private val settingsStore: SettingsStore,
    private val oauthService: BitbucketOAuthService? = null,
    private val githubOAuthService: GitHubDeviceFlowService? = null,
    oauthResults: Channel<Result<OAuthAccount>>? = null,
) : ViewModel() {

    val repos: StateFlow<List<Repo>> = repository.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** OAuth が利用可能か（BuildConfig に client_id/secret がある）。 */
    val bitbucketOAuthAvailable: Boolean = oauthService != null

    /** redirect Activity が交換した OAuth 結果（AddRepoScreen が購読）。 */
    val oauthResult: Flow<Result<OAuthAccount>> = oauthResults?.receiveAsFlow() ?: emptyFlow()

    /** 認可を開始（state 保存）。UI は返り値の url を Custom Tabs で開く。未設定なら null。 */
    fun startBitbucketOAuth(): AuthorizationRequest? = oauthService?.startAuthorization()

    /** GitHub Device Flow が利用可能か（BuildConfig に client_id がある）。 */
    val githubOAuthAvailable: Boolean = githubOAuthService != null

    /** GitHub: device/code を要求（UI は user_code 表示＋verification_uri を開く）。未設定なら失敗。 */
    suspend fun requestGitHubDeviceCode(): Result<GitHubDeviceCode> =
        githubOAuthService?.requestDeviceCode()
            ?: Result.failure(IllegalStateException("GitHub ログインは未設定です"))

    /** GitHub: ユーザ承認をポーリングし、成功で OAuthAccount を返す。 */
    suspend fun pollGitHubToken(code: GitHubDeviceCode): Result<OAuthAccount> =
        githubOAuthService?.pollForToken(code)
            ?: Result.failure(IllegalStateException("GitHub ログインは未設定です"))

    /** 成功したログインを記憶（次回のリポ追加で再ログイン不要にする）。 */
    fun rememberOAuthLogin(account: OAuthAccount) {
        viewModelScope.launch { runCatching { repository.rememberOAuthSession(account) } }
    }

    /** 指定ホストの記憶済みログイン（失効間近なら refresh 済み）。無ければ null。 */
    suspend fun rememberedOAuthAccount(host: com.k1.gitreader.data.db.GitHost): OAuthAccount? {
        val provider = when (host) {
            com.k1.gitreader.data.db.GitHost.GITHUB -> GitHubDeviceFlow.PROVIDER
            com.k1.gitreader.data.db.GitHost.BITBUCKET -> BitbucketOAuthService.PROVIDER
        }
        return runCatching { repository.rememberedOAuthSession(provider) }.getOrNull()
    }

    /** OAuth でアクセス可能かつ未登録のリモートリポ一覧（プルダウン選択用）。provider で振り分け。 */
    suspend fun listClonableRepos(account: OAuthAccount): Result<List<RemoteRepo>> = runCatching {
        when (account.provider) {
            GitHubDeviceFlow.PROVIDER -> repository.listClonableGitHubRepos(account)
            else -> repository.listClonableBitbucketRepos(account)
        }
    }

    private val _status = MutableStateFlow(UiStatus())
    val status: StateFlow<UiStatus> = _status

    fun addRepo(input: NewRepo, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = "clone 中...")
            val ok = runCatching { repository.addAndClone(input) }
            ok.exceptionOrNull()?.let { android.util.Log.w("GitReader", "addAndClone failed", it) }
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

    /** 完了まで待つ同期（pull-to-refresh 用）。失敗時は例外を送出する。 */
    suspend fun syncNow(repo: Repo) {
        repository.sync(repo)
    }

    fun delete(repo: Repo) {
        viewModelScope.launch { runCatching { repository.delete(repo) } }
    }

    fun setRepoColor(repo: Repo, color: com.k1.gitreader.data.db.RepoColor) {
        viewModelScope.launch { runCatching { repository.setColor(repo, color) } }
    }

    /** リポの所属グループを変更(空=未分類)。 */
    fun setRepoGroup(repo: Repo, group: String) {
        viewModelScope.launch { runCatching { repository.setGroup(repo, group) } }
    }

    /** リポ一覧で表示するグループを選択(空=すべて)。 */
    fun setSelectedGroup(group: String) = settingsStore.setSelectedGroup(group)

    fun saveRepoOrder(ordered: List<Repo>) {
        viewModelScope.launch { runCatching { repository.saveOrder(ordered) } }
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

    suspend fun loadSearchCorpus(repo: Repo): List<TextFile> =
        repository.loadSearchCorpus(repo)

    fun workDirOf(repo: Repo): File = repository.workDir(repo)

    suspend fun fileHistory(repo: Repo, relPath: String): List<CommitInfo> =
        repository.fileHistory(repo, relPath)

    suspend fun fileDiff(repo: Repo, relPath: String, sha: String): String =
        repository.fileDiff(repo, relPath, sha)

    suspend fun commitGraph(repo: Repo): List<com.k1.gitreader.git.GraphCommit> =
        repository.commitGraph(repo)

    suspend fun commitDiff(repo: Repo, sha: String): String =
        repository.commitDiff(repo, sha)

    /** リポ毎テーマを変更（保存後、更新済み Repo を onDone で返す）。 */
    fun setRepoTheme(repo: Repo, mode: ThemeMode, onDone: (Repo) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.setTheme(repo, mode) }.getOrNull()?.let(onDone)
        }
    }

    fun clearMessage() {
        _status.value = _status.value.copy(message = null)
    }

    // --- グローバル設定 ---
    fun setDefaultTheme(mode: ThemeMode) = settingsStore.setDefaultTheme(mode)

    fun setFontScale(scale: FontScale) = settingsStore.setFontScale(scale)

    fun setWrapByDefault(wrap: Boolean) = settingsStore.setWrapByDefault(wrap)

    fun setLinkOpenMode(mode: com.k1.gitreader.data.LinkOpenMode) = settingsStore.setLinkOpenMode(mode)

    fun setShowLineNumbers(show: Boolean) = settingsStore.setShowLineNumbers(show)

    fun setTableMode(mode: com.k1.gitreader.data.TableMode) = settingsStore.setTableMode(mode)

    fun setStickyHeadings(on: Boolean) = settingsStore.setStickyHeadings(on)

    fun setIconSet(set: com.k1.gitreader.data.IconSet) = settingsStore.setIconSet(set)

    /** キャッシュ全削除（登録リポジトリ・トークン・作業ツリーを一括削除）。 */
    fun clearCache(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = "削除中...")
            val ok = runCatching { repository.deleteAll() }
            _status.value = UiStatus(
                busy = false,
                message = ok.exceptionOrNull()?.let { "削除失敗: ${it.message}" } ?: "キャッシュを削除しました",
            )
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GitReaderApplication
                RepoListViewModel(
                    app.container.repoRepository,
                    app.container.settingsStore,
                    app.container.bitbucketOAuthService,
                    app.container.githubOAuthService,
                    app.container.oauthResults,
                )
            }
        }
    }
}
