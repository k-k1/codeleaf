package jp.lazmix.codeleaf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import jp.lazmix.codeleaf.CodeLeafApplication
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.data.AppSettings
import jp.lazmix.codeleaf.data.FavoriteRepository
import jp.lazmix.codeleaf.data.FileEntry
import jp.lazmix.codeleaf.data.FontScale
import jp.lazmix.codeleaf.data.MemoRepository
import jp.lazmix.codeleaf.data.NavPosition
import jp.lazmix.codeleaf.data.NavPositionStore
import jp.lazmix.codeleaf.data.NewRepo
import jp.lazmix.codeleaf.data.RepoRepository
import jp.lazmix.codeleaf.data.SettingsStore
import jp.lazmix.codeleaf.data.SearchFile
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.ThemeMode
import jp.lazmix.codeleaf.data.oauth.AuthorizationRequest
import jp.lazmix.codeleaf.data.oauth.BitbucketOAuthService
import jp.lazmix.codeleaf.data.oauth.GitHubDeviceCode
import jp.lazmix.codeleaf.data.oauth.GitHubDeviceFlow
import jp.lazmix.codeleaf.data.oauth.GitHubDeviceFlowService
import jp.lazmix.codeleaf.data.oauth.OAuthAccount
import jp.lazmix.codeleaf.data.oauth.RemoteRepo
import jp.lazmix.codeleaf.git.BranchInfo
import jp.lazmix.codeleaf.git.EntryCommit
import jp.lazmix.codeleaf.git.CommitInfo
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

/** 進行中の非同期操作・エラーを画面に伝えるための状態。文言は表示時に解決する [UiText]。 */
data class UiStatus(
    val busy: Boolean = false,
    val message: UiText? = null,
    /** 同期失敗時にスナックバーへ「再Clone」アクションを出す対象。null ならアクション無し。 */
    val recloneTarget: Repo? = null,
)

/**
 * アプリ全体の主 ViewModel。リポジトリの一覧・追加(clone)・同期・削除・並べ替え・グループ分け、
 * OAuth ログイン、ファイル/ブランチ/履歴/diff の読み出し、全文検索、設定の読み書きを束ねる。
 * 状態は [repos]・[status]・[settings] の StateFlow で公開し、画面はこれを購読する。
 * DI は手動(`AppContainer` 経由・[Factory])で、OAuth 系依存は未設定なら null(機能無効)。
 */
class MainViewModel(
    private val repository: RepoRepository,
    private val settingsStore: SettingsStore,
    private val memos: MemoRepository,
    private val favorites: FavoriteRepository,
    private val navPositions: NavPositionStore,
    private val bitbucketOAuthService: BitbucketOAuthService? = null,
    private val githubOAuthService: GitHubDeviceFlowService? = null,
    oauthResults: Channel<Result<OAuthAccount>>? = null,
) : ViewModel() {

    val repos: StateFlow<List<Repo>> = repository.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** OAuth が利用可能か（BuildConfig に client_id/secret がある）。 */
    val bitbucketOAuthAvailable: Boolean = bitbucketOAuthService != null

    /** redirect Activity が交換した OAuth 結果（AddRepoScreen が購読）。 */
    val oauthResult: Flow<Result<OAuthAccount>> = oauthResults?.receiveAsFlow() ?: emptyFlow()

    /** 認可を開始（state 保存）。UI は返り値の url を Custom Tabs で開く。未設定なら null。 */
    fun startBitbucketOAuth(): AuthorizationRequest? = bitbucketOAuthService?.startAuthorization()

    /** GitHub Device Flow が利用可能か（BuildConfig に client_id がある）。 */
    val githubOAuthAvailable: Boolean = githubOAuthService != null

    /** GitHub: device/code を要求（UI は user_code 表示＋verification_uri を開く）。未設定なら失敗。 */
    suspend fun requestGitHubDeviceCode(): Result<GitHubDeviceCode> =
        githubOAuthService?.requestDeviceCode()
            // メッセージは持たせず、表示文言は呼び出し側(UI)がロケール解決した文言にフォールバックさせる。
            ?: Result.failure(IllegalStateException())

    /** GitHub: ユーザ承認をポーリングし、成功で OAuthAccount を返す。 */
    suspend fun pollGitHubToken(code: GitHubDeviceCode): Result<OAuthAccount> =
        githubOAuthService?.pollForToken(code)
            ?: Result.failure(IllegalStateException())

    /** 成功したログインを記憶（次回のリポ追加で再ログイン不要にする）。 */
    fun rememberOAuthLogin(account: OAuthAccount) {
        viewModelScope.launch { runCatching { repository.rememberOAuthSession(account) } }
    }

    /** 指定ホストの記憶済みログイン（失効間近なら refresh 済み）。無ければ null。 */
    suspend fun rememberedOAuthAccount(host: jp.lazmix.codeleaf.data.db.GitHost): OAuthAccount? {
        val provider = when (host) {
            jp.lazmix.codeleaf.data.db.GitHost.GITHUB -> GitHubDeviceFlow.PROVIDER
            jp.lazmix.codeleaf.data.db.GitHost.BITBUCKET -> BitbucketOAuthService.PROVIDER
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

    init {
        // 起動時: プロセス死で中断した clone(CLONING 残留)を FAILED に倒す。
        viewModelScope.launch { runCatching { repository.failInterruptedClones() } }
    }

    /**
     * リポを登録し（一覧に即「clone 中」パネルとして出現）、バックグラウンドで clone する。
     * 呼び出し側は戻りを待たず即一覧へ戻ってよい。状態は [repos] の `cloneState` で駆動する。
     */
    fun addRepo(input: NewRepo) {
        viewModelScope.launch {
            val repo = runCatching { repository.register(input) }.getOrElse {
                _status.value = UiStatus(message = cloneErrorUiText(it))
                return@launch
            }
            runClone(repo)
        }
    }

    /** 失敗(FAILED)リポの clone を再試行する。 */
    fun retryClone(repo: Repo) {
        viewModelScope.launch { runClone(repo) }
    }

    /** ローカル clone が壊れたときに作り直す（削除→再 clone）。実体は [retryClone] と同じ。 */
    fun reclone(repo: Repo) {
        viewModelScope.launch { runClone(repo, UiText.Res(R.string.repo_recloned, listOf(repo.name))) }
    }

    private suspend fun runClone(
        repo: Repo,
        successMessage: UiText = UiText.Res(R.string.repo_added, listOf(repo.name)),
    ) {
        val ok = runCatching { repository.cloneRegistered(repo) }
        ok.exceptionOrNull()?.let { android.util.Log.w("CodeLeaf", "clone failed", it) }
        _status.value = UiStatus(
            message = ok.exceptionOrNull()?.let { cloneErrorUiText(it) } ?: successMessage,
        )
    }

    fun sync(repo: Repo) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = UiText.Res(R.string.repo_syncing, listOf(repo.name)))
            val ok = runCatching { repository.sync(repo) }
            _status.value = UiStatus(
                busy = false,
                message = syncResultUiText(ok.exceptionOrNull()),
                // 失敗時は作り直しの導線を出す(破損・取得不能からの復帰手段)。
                recloneTarget = repo.takeIf { ok.isFailure },
            )
        }
    }

    /** 完了まで待つ同期（pull-to-refresh 用）。失敗時は例外を送出する。 */
    suspend fun syncNow(repo: Repo) {
        repository.sync(repo)
    }

    fun delete(repo: Repo) {
        navPositions.clear(repo.id)
        viewModelScope.launch { runCatching { repository.delete(repo) } }
    }

    fun setRepoColor(repo: Repo, color: jp.lazmix.codeleaf.data.db.RepoColor, onDone: (Repo) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.setColor(repo, color) }.getOrNull()?.let(onDone)
        }
    }

    /** リポ一覧で表示するグループを選択(空=すべて)。 */
    fun setSelectedGroup(group: String) = settingsStore.setSelectedGroup(group)

    /** 空のグループを新規作成する(名前一覧に追加)。 */
    fun addGroup(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val cur = settings.value.groups
        if (cur.none { it == n }) settingsStore.setGroups(cur + n)
    }

    /** グループ名を変更する(名前一覧＋所属リポの groupName を更新)。 */
    fun renameGroup(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return
        viewModelScope.launch {
            settingsStore.setGroups(settings.value.groups.map { if (it == old) n else it })
            repos.value.filter { it.groupName == old }.forEach { runCatching { repository.setGroup(it, n) } }
        }
    }

    /** グループを削除する(名前一覧から除外＋所属リポを未分類に戻す)。 */
    fun deleteGroup(name: String) {
        viewModelScope.launch {
            settingsStore.setGroups(settings.value.groups.filter { it != name })
            repos.value.filter { it.groupName == name }.forEach { runCatching { repository.setGroup(it, "") } }
        }
    }

    /** 編集画面のセクション D&D 結果(表示順＋所属)を保存する。 */
    fun saveRepoGroupsAndOrder(ordered: List<Repo>) {
        viewModelScope.launch { runCatching { repository.saveGroupsAndOrder(ordered) } }
    }

    /** 直近順のリモートブランチ一覧。 */
    suspend fun listBranches(repo: Repo): List<BranchInfo> = repository.listBranches(repo)

    /** ブランチを切り替え（= 指定ブランチで同期）。成功時に更新後 Repo を返す。 */
    fun switchBranch(repo: Repo, branch: String, onDone: (Repo) -> Unit) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = UiText.Res(R.string.branch_switching, listOf(branch)))
            val r = runCatching { repository.sync(repo, branch) }
            _status.value = UiStatus(
                busy = false,
                message = r.exceptionOrNull()?.let { UiText.GitError(R.string.switch_failed, gitErrorKind(it), it.message) },
                recloneTarget = repo.takeIf { r.isFailure },
            )
            r.getOrNull()?.let {
                // 作業ツリー書換でフォルダ/ファイルが失効しうるので、保存済みナビ位置を破棄。
                navPositions.clear(repo.id)
                onDone(it)
            }
        }
    }

    // --- ナビ位置（リポを開き直したとき最後のフォルダ/ファイルを復元する） ---
    /** 保存済みのナビ位置を同期取得（無ければ null）。 */
    fun savedNavPosition(repoId: Long): NavPosition? = navPositions.get(repoId)

    fun saveNavPosition(repoId: Long, pos: NavPosition) = navPositions.save(repoId, pos)

    // --- ファイルブラウザ / 閲覧 ---
    suspend fun listDir(repo: Repo, relPath: String): List<FileEntry> =
        repository.listDir(repo, relPath, collapse = settingsStore.settings.value.collapseFolders)

    /** 一覧エントリの最終コミット(著者・日時)。設定 ON のブラウザからのみ呼ぶ。 */
    suspend fun dirCommitMeta(repo: Repo, relDir: String, entries: List<FileEntry>): Map<String, EntryCommit> =
        repository.dirCommitMeta(repo, relDir, entries)

    suspend fun readFile(
        repo: Repo,
        relPath: String,
        charsetName: String? = null,
        maxBytes: Long = Long.MAX_VALUE,
    ): jp.lazmix.codeleaf.data.TextLoad =
        repository.readText(repo, relPath, resolveCharset(charsetName), maxBytes)

    private fun resolveCharset(name: String?): java.nio.charset.Charset =
        name?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8

    suspend fun probeFile(repo: Repo, relPath: String): jp.lazmix.codeleaf.data.FileInfo =
        repository.probeFile(repo, relPath)

    /** 指定コミット時点のファイル種別(履歴表示用・blob が無ければ null)。 */
    suspend fun probeBlob(repo: Repo, relPath: String, sha: String): jp.lazmix.codeleaf.data.FileInfo? =
        repository.probeBlob(repo, relPath, sha)

    /** 指定コミット時点のテキスト本文(履歴表示用・blob が無ければ null)。 */
    suspend fun readBlobText(
        repo: Repo,
        relPath: String,
        sha: String,
        charsetName: String? = null,
        maxBytes: Long = Long.MAX_VALUE,
    ): jp.lazmix.codeleaf.data.TextLoad? =
        repository.readBlobText(repo, relPath, sha, resolveCharset(charsetName), maxBytes)

    suspend fun loadSearchCorpus(repo: Repo): List<SearchFile> =
        repository.loadSearchCorpus(repo)

    fun workDirOf(repo: Repo): File = repository.workDir(repo)

    suspend fun fileHistory(repo: Repo, relPath: String): List<CommitInfo> =
        repository.fileHistory(repo, relPath)

    suspend fun fileDiff(repo: Repo, relPath: String, sha: String): String =
        repository.fileDiff(repo, relPath, sha)

    /** submodule(gitlink)変更を old→new の範囲コミット列に解決する。submodule diff の意味化に使う。 */
    suspend fun submoduleChange(repo: Repo, path: String, sha: String): jp.lazmix.codeleaf.git.SubmoduleChange? =
        repository.submoduleChange(repo, path, sha)

    suspend fun commitGraph(repo: Repo): List<jp.lazmix.codeleaf.git.GraphCommit> =
        repository.commitGraph(repo)

    suspend fun commitDiff(repo: Repo, sha: String): String =
        repository.commitDiff(repo, sha)

    /** コミットの変更ファイル一覧(本文未整形・安価)。各ファイル本文は fileDiff で遅延取得する。 */
    suspend fun commitFileSummaries(repo: Repo, sha: String): List<jp.lazmix.codeleaf.git.DiffFileSummary> =
        repository.commitFileSummaries(repo, sha)

    /** リポ毎テーマを変更（保存後、更新済み Repo を onDone で返す）。 */
    fun setRepoTheme(repo: Repo, mode: ThemeMode, onDone: (Repo) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.setTheme(repo, mode) }.getOrNull()?.let(onDone)
        }
    }

    fun clearMessage() {
        _status.value = _status.value.copy(message = null, recloneTarget = null)
    }

    // --- グローバル設定 ---
    fun setDefaultTheme(mode: ThemeMode) = settingsStore.setDefaultTheme(mode)

    fun setFontScale(scale: FontScale) = settingsStore.setFontScale(scale)

    fun setWrapByDefault(wrap: Boolean) = settingsStore.setWrapByDefault(wrap)

    fun setDiffWrap(wrap: Boolean) = settingsStore.setDiffWrap(wrap)

    fun setLinkOpenMode(mode: jp.lazmix.codeleaf.data.LinkOpenMode) = settingsStore.setLinkOpenMode(mode)

    fun setShowLineNumbers(show: Boolean) = settingsStore.setShowLineNumbers(show)

    fun setTableMode(mode: jp.lazmix.codeleaf.data.TableMode) = settingsStore.setTableMode(mode)

    fun setStickyHeadings(on: Boolean) = settingsStore.setStickyHeadings(on)

    fun setCollapseFolders(on: Boolean) = settingsStore.setCollapseFolders(on)

    fun setShowCommitInfo(on: Boolean) = settingsStore.setShowCommitInfo(on)

    fun setFileNameDisplay(mode: jp.lazmix.codeleaf.data.FileNameDisplay) =
        settingsStore.setFileNameDisplay(mode)

    fun setIconSet(set: jp.lazmix.codeleaf.data.IconSet) = settingsStore.setIconSet(set)

    fun setRestoreLastPosition(on: Boolean) = settingsStore.setRestoreLastPosition(on)

    fun setSelectByDefault(on: Boolean) = settingsStore.setSelectByDefault(on)

    /** キャッシュ全削除（登録リポジトリ・トークン・作業ツリーを一括削除）。 */
    fun clearCache(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _status.value = UiStatus(busy = true, message = UiText.Res(R.string.cache_deleting))
            val ok = runCatching { repository.deleteAll() }
            _status.value = UiStatus(
                busy = false,
                message = ok.exceptionOrNull()
                    ?.let { UiText.Res(R.string.cache_delete_failed, listOf(it.message ?: it.javaClass.simpleName)) }
                    ?: UiText.Res(R.string.cache_deleted),
            )
            onDone()
        }
    }

    // --- メモ ---
    fun observeMemos(repoId: Long) = memos.observeMemos(repoId)
    fun observeMemoEntries(memoId: Long) = memos.observeEntries(memoId)
    suspend fun getMemoEntries(memoId: Long) = memos.getEntries(memoId)

    fun createMemo(repoId: Long, title: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(memos.createMemo(repoId, title)) }
    }

    fun renameMemo(id: Long, title: String) {
        viewModelScope.launch { memos.renameMemo(id, title) }
    }

    fun deleteMemo(id: Long) {
        viewModelScope.launch { memos.deleteMemo(id) }
    }

    fun addMemoEntry(
        memoId: Long,
        filePath: String,
        lineStart: Int,
        lineEnd: Int,
        quote: String,
        comment: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            memos.addEntry(memoId, filePath, lineStart, lineEnd, quote, comment)
            onDone()
        }
    }

    /** 新規メモ帳を作って即エントリを1件追加する(ビューアの「新規メモ帳に保存」用)。 */
    fun createMemoWithEntry(
        repoId: Long,
        title: String,
        filePath: String,
        lineStart: Int,
        lineEnd: Int,
        quote: String,
        comment: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val memoId = memos.createMemo(repoId, title)
            memos.addEntry(memoId, filePath, lineStart, lineEnd, quote, comment)
            onDone()
        }
    }

    fun deleteMemoEntry(id: Long) {
        viewModelScope.launch { memos.deleteEntry(id) }
    }

    fun editMemoEntryComment(entryId: Long, memoId: Long, comment: String) {
        viewModelScope.launch { memos.editEntryComment(entryId, memoId, comment) }
    }

    /** メモ帳は残してエントリだけ全削除。 */
    fun clearMemoEntries(memoId: Long) {
        viewModelScope.launch { memos.clearEntries(memoId) }
    }

    // --- お気に入り ---
    fun observeFavorites(repoId: Long) = favorites.observeFavorites(repoId)

    /** ファイル/フォルダのお気に入り登録をトグルする(登録済みなら解除)。 */
    fun toggleFavorite(repoId: Long, relPath: String, isDir: Boolean) {
        viewModelScope.launch { favorites.toggle(repoId, relPath, isDir) }
    }

    fun deleteFavorite(id: Long) {
        viewModelScope.launch { favorites.delete(id) }
    }

    /** お気に入りの並べ替えを保存する([orderedIds] の並び順で sortOrder を振り直す)。 */
    fun reorderFavorites(orderedIds: List<Long>) {
        viewModelScope.launch { favorites.reorder(orderedIds) }
    }

    /** お気に入りの実体が作業ツリーに残っているか(一覧のグレーアウト判定)。 */
    suspend fun favoriteExists(repo: Repo, relPath: String): Boolean = repository.exists(repo, relPath)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CodeLeafApplication
                MainViewModel(
                    app.container.repoRepository,
                    app.container.settingsStore,
                    app.container.memoRepository,
                    app.container.favoriteRepository,
                    app.container.navPositionStore,
                    app.container.bitbucketOAuthService,
                    app.container.githubOAuthService,
                    app.container.oauthResults,
                )
            }
        }
    }
}
