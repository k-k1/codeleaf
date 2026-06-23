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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import jp.lazmix.codeleaf.data.NavPosition
import jp.lazmix.codeleaf.data.OpenFile
import jp.lazmix.codeleaf.data.db.Repo
import jp.lazmix.codeleaf.data.db.ThemeMode
import jp.lazmix.codeleaf.git.CommitInfo
import jp.lazmix.codeleaf.git.GraphCommit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    @Parcelize data object Licenses : Screen
    @Parcelize data object RepoEdit : Screen
    @Parcelize data class Browse(override val repo: Repo, val path: String) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    // path = 検索を開いた時点で表示していたフォルダ。検索画面のパス絞り込みの既定値にする(空=リポ全体)。
    @Parcelize data class Search(override val repo: Repo, val path: String = "") : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Graph(override val repo: Repo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    // sha != null = そのコミット時点の版を表示する履歴モード(作業ツリーに無いファイル用・読み取り専用)。
    @Parcelize data class View(
        override val repo: Repo,
        val filePath: String,
        val line: Int? = null,
        val sha: String? = null,
    ) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class History(
        override val repo: Repo,
        val filePath: String,
        /** submodule の場合 true。親リポにある gitlink(ポインタ)移動の履歴である旨を画面で明示する。 */
        val isSubmodule: Boolean = false,
    ) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
    @Parcelize data class Diff(
        override val repo: Repo,
        val filePath: String,
        val commit: CommitInfo,
        /** submodule の履歴から開いた gitlink 変更か。true なら範囲(コミット列)表示にする。 */
        val isSubmodule: Boolean = false,
    ) : WithRepo {
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
    @Parcelize data class Favorites(override val repo: Repo) : WithRepo {
        override fun withRepo(updated: Repo) = copy(repo = updated)
    }
}

/** 2ペイン(左=一覧/右=詳細)・3ペイン(左=リポ一覧/中=一覧/右=詳細)のしきい値。 */
private val TWO_PANE_MIN_WIDTH = 600.dp
private val THREE_PANE_MIN_WIDTH = 960.dp

@Composable
fun CodeLeafApp() {
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
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
    // detailStack と並走し、各ファイルを開いた時点の backStack 深さ(=フォルダ階層の深さ)を記録する。
    // 戻る時に「ファイルを開いた後にフォルダを潜ったか」を深さ比較で判定するために使う。
    val detailDepth = rememberSaveable(
        saver = listSaver<SnapshotStateList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<Int>() }
    // ブラウザ/ビューアのスクロール等を、1ペインのアンマウント(戻る・ファイル開閉)を跨いで保持する。
    // ファイル毎/ブラウズパス毎に SaveableStateProvider で包む(プロセス死も跨ぐ)。
    val navStateHolder = rememberSaveableStateHolder()
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

    // detailStack(開いているファイル)を深さ記録と同期して操作する。深さは戻る判定に使う。
    fun pushDetail(v: Screen.View) { detailStack.add(v); detailDepth.add(backStack.size) }
    fun popDetail() {
        detailStack.removeAt(detailStack.lastIndex)
        if (detailDepth.isNotEmpty()) detailDepth.removeAt(detailDepth.lastIndex)
    }
    fun clearDetails() { detailStack.clear(); detailDepth.clear() }

    // 指定 Browse の親フォルダへ一つ上がる。直下が親なら pop、非線形なら親に置換する。
    fun goUpBrowse(b: Screen.Browse) {
        val parent = b.path.substringBeforeLast('/', "")
        val below = backStack.getOrNull(backStack.lastIndex - 1)
        if (below is Screen.Browse && below.repo.id == b.repo.id && below.path == parent) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            backStack[backStack.lastIndex] = Screen.Browse(b.repo, parent)
        }
    }

    // System Back と Viewer の戻る矢印を一本化する。
    fun handleBack() {
        val top = backStack.last()
        if (top is Screen.Browse && detailStack.isNotEmpty()) {
            // 集中モード中: リンク等で別ファイルへ潜っていれば(detailStack に戻れる前のファイルがある)、
            // 集中を解除せず前のファイルへ戻る。先頭ファイルなら従来どおり集中を解除する。
            if (focusMode) {
                if (detailStack.size > 1) popDetail() else focusMode = false
                return
            }
            // 多ペイン: 開いているファイルより後にフォルダを潜っていれば(backStack が当時より深い)、
            // ファイルを閉じる前にフォルダを一つ上げる(「フォルダ遷移直後の戻る」を直感に合わせる)。
            val openedAtDepth = detailDepth.lastOrNull() ?: backStack.size
            if (top.path.isNotEmpty() && backStack.size > openedAtDepth) {
                goUpBrowse(top)
                return
            }
            popDetail() // 次に開いているファイルを1つ戻す
            return
        }
        val before = top
        pop()
        // リポ閲覧から抜けたら開いていたファイルを掃除する。
        if (before is Screen.Browse && backStack.last() !is Screen.Browse) {
            clearDetails()
            focusMode = false
        }
    }

    // メモのエントリから該当ファイルの行を開く。メモ画面を畳んで Browse に戻り(無ければ作る)、
    // ビューア(detailStack)に View を積む。
    fun openFileAt(repo: Repo, path: String, line: Int? = null) {
        while (backStack.size > 1 && backStack.last() !is Screen.Browse) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack.last() !is Screen.Browse) {
            backStack.add(Screen.Browse(repo, path.substringBeforeLast('/', "")))
        }
        pushDetail(Screen.View(repo, path, line))
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

    // diff のファイルを Viewer で開く。作業ツリーに在れば現物(ディレクトリを開き＋ビューア)、
    // 無ければそのコミット時点の版(履歴モード)を開く。submodule 等ディレクトリはファイルとして開かない。
    fun openDiffFile(repo: Repo, path: String, sha: String) {
        val f = java.io.File(vm.workDirOf(repo), path)
        while (backStack.size > 1 && backStack.last() !is Screen.Browse) backStack.removeAt(backStack.lastIndex)
        if (f.isDirectory) {
            // submodule の変更など対象がディレクトリ: ファイルとして開くと EISDIR。
            // そのフォルダをブラウザで開き、ファイルは未選択にする。
            if (backStack.last() is Screen.Browse) navigateToDir(repo, path) else backStack.add(Screen.Browse(repo, path))
            clearDetails()
            focusMode = false
        } else if (f.exists()) {
            val dir = path.substringBeforeLast('/', "")
            if (backStack.last() is Screen.Browse) navigateToDir(repo, dir) else backStack.add(Screen.Browse(repo, dir))
            pushDetail(Screen.View(repo, path))
        } else {
            if (backStack.last() !is Screen.Browse) backStack.add(Screen.Browse(repo, "")) // ビューアのホスト確保
            pushDetail(Screen.View(repo, path, sha = sha))
        }
    }

    // リポを出てリポ一覧へ戻る(Browse チェーンを畳む)。ブラウザ ← の動作。
    fun leaveRepo() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        clearDetails()
        focusMode = false
    }

    // リポ更新(テーマ/色など)を backStack / detailStack 内の同一リポ全画面スナップショットへ即反映する。
    fun applyRepoUpdate(updated: Repo) {
        for (idx in backStack.indices) {
            val s = backStack[idx]
            if (s is Screen.WithRepo && s.repo.id == updated.id) backStack[idx] = s.withRepo(updated)
        }
        for (idx in detailStack.indices) {
            val s = detailStack[idx]
            if (s.repo.id == updated.id) detailStack[idx] = s.copy(repo = updated)
        }
    }

    // 現在表示中のリポの「最後にいた場所」(フォルダチェーン＋開いているファイル＋集中モード)を切り出す。
    // backStack には常に1リポ分の Browse しか積まれない(openRepo が切替時に一旦 List へ畳むため)。
    // Browse が無い(=Graph/お気に入り直入り)・リポ外(List/設定)は null=保存しない。
    fun captureRepoPosition(): Pair<Long, NavPosition>? {
        val repoId = (backStack.lastOrNull { it is Screen.WithRepo } as? Screen.WithRepo)?.repo?.id
            ?: return null
        val chain = backStack.filterIsInstance<Screen.Browse>().filter { it.repo.id == repoId }.map { it.path }
        if (chain.isEmpty()) return null
        val files = detailStack.filter { it.repo.id == repoId }.map { OpenFile(it.filePath, it.line) }
        return repoId to NavPosition(chain, files, focusMode)
    }

    fun flushCurrentRepoPosition() {
        captureRepoPosition()?.let { (id, pos) -> vm.saveNavPosition(id, pos) }
    }

    // リポを開く(一覧/レールから)。離脱元を保存してから List まで畳み、保存済み位置を復元する。
    // 「切替＝push 積み増し」をやめ畳み直しにすることで、往復で同一リポが重複せず、
    // detailStack に別リポのファイルが残る不整合も避ける。
    fun openRepo(target: Repo) {
        flushCurrentRepoPosition()
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        clearDetails()
        focusMode = false
        graphSelected = null
        historySelected = null
        // 設定 OFF のときは復元せず常にトップ・ファイル未オープンで開く(保存自体は継続)。
        val saved = if (vm.settings.value.restoreLastPosition) vm.savedNavPosition(target.id) else null
        if (saved == null) {
            backStack.add(Screen.Browse(target, ""))
        } else {
            // chain は get 側で「非空・先頭 ""」を保証済み。target は DB 最新の Repo を使う(branch/theme 反映)。
            saved.chain.forEach { backStack.add(Screen.Browse(target, it)) }
            if (backStack.last() !is Screen.Browse) backStack.add(Screen.Browse(target, ""))
            saved.files.forEach { pushDetail(Screen.View(target, it.path, it.line)) }
            focusMode = saved.focus
        }
    }

    BackHandler(enabled = backStack.size > 1 || detailStack.isNotEmpty()) { handleBack() }

    val repos by vm.repos.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()

    // プロセス死からの復元で backStack/detailStack が「現存しないリポ」(削除済み・別端末同期前など)を
    // 指すことがある。その画面はファイル取得に失敗する幽霊になり、一覧の上に旧リポ名が重なって見える。
    // リポ確定後(非空)に検出したら一覧へ戻す。リポ id がテスト毎に変わる E2E の決定化にも効く。
    LaunchedEffect(repos) {
        if (repos.isEmpty()) return@LaunchedEffect // 初回ロード前(空)は復元画面を温存する
        val ids = repos.mapTo(HashSet()) { it.id }
        val stale = backStack.any { it is Screen.WithRepo && it.repo.id !in ids } ||
            detailStack.any { it.repo.id !in ids }
        if (stale) {
            backStack.clear()
            backStack.add(Screen.List)
            clearDetails()
            focusMode = false
            graphSelected = null
        }
    }

    // 現在のリポのナビ位置を継続保存する。戻る/leaveRepo など離脱経路を取りこぼさず、
    // SharedPreferences へ書くのでアプリ再起動も跨いで復元できる(openRepo が読み戻す)。
    LaunchedEffect(Unit) {
        snapshotFlow { captureRepoPosition() }
            .distinctUntilChanged()
            .collect { it?.let { (id, pos) -> vm.saveNavPosition(id, pos) } }
    }

    // 左レール(リポ一覧)。List 全画面・3ペインの左で共有する。
    // グループ一覧 = 定義済み(settings, 表示順) ＋ 念のため未登録のリポ所属名(末尾)。
    val allGroups = run {
        val orphans = repos.map { it.groupName }.filter { it.isNotBlank() && it !in settings.groups }.distinct().sorted()
        settings.groups + orphans
    }

    // onItemSelected はドロワー再利用時に「遷移したら閉じる」ために各導線の手前で呼ぶ(既定 no-op)。
    @Composable
    fun RailPane(
        selectedRepoId: Long?,
        onCollapse: (() -> Unit)? = null,
        onItemSelected: () -> Unit = {},
        compact: Boolean = false,
    ) {
        // 選択中グループが消えていたら「すべて」に退避。
        val selectedGroup = settings.selectedGroup.takeIf { it.isNotEmpty() && it in allGroups } ?: ""
        val shownRepos = if (selectedGroup.isEmpty()) repos else repos.filter { it.groupName == selectedGroup }
        RepoListScreen(
            repos = shownRepos,
            status = status,
            onAddClick = { onItemSelected(); navigate(Screen.Add) },
            onSettings = { onItemSelected(); navigate(Screen.Settings) },
            onEdit = { onItemSelected(); navigate(Screen.RepoEdit) },
            onOpen = { onItemSelected(); openRepo(it) },
            onOpenGraph = { onItemSelected(); graphSelected = null; navigate(Screen.Graph(it)) },
            onOpenFavorites = { onItemSelected(); navigate(Screen.Favorites(it)) },
            onSync = vm::sync,
            onRetry = vm::retryClone,
            onReclone = vm::reclone,
            onDelete = vm::delete,
            onMessageShown = vm::clearMessage,
            groups = allGroups,
            selectedGroup = selectedGroup,
            onSelectGroup = vm::setSelectedGroup,
            selectedRepoId = selectedRepoId,
            onCollapse = onCollapse,
            compact = compact,
        )
    }

    // レール畳み時の細いアイコンレール: ≡(展開) / リポのアバター縦並び / 下に編集(or＋)・設定。
    @Composable
    fun IconRail(selectedRepoId: Long?) {
        CodeLeafTheme(settings.defaultTheme) {
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
                                onClick = { openRepo(r) },
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
                    CodeLeafTheme(settings.defaultTheme) {
                        RailPane(selectedRepoId, onCollapse = { railCollapsed = true }, compact = true)
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(0.75f)) { CodeLeafTheme(repoTheme) { content() } }
            } else {
                IconRail(selectedRepoId)
                VerticalDivider()
                Box(Modifier.weight(1f)) { CodeLeafTheme(repoTheme) { content() } }
            }
        }
    }

    // リポ詳細系画面の共通ホスト: 3ペインなら左レール付き、未満ならリポ毎テーマで全画面。
    @Composable
    fun RepoPaneHost(three: Boolean, repo: Repo, content: @Composable () -> Unit) {
        if (three) {
            ThreePaneScaffold(repo.id, repo.themeMode) { content() }
        } else {
            CodeLeafTheme(repo.themeMode) { content() }
        }
    }

    when (val current = backStack.last()) {
        Screen.List -> BoxWithConstraints {
            if (maxWidth >= THREE_PANE_MIN_WIDTH) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.25f)) { RailPane(selectedRepoId = null, compact = true) }
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
            onSubmit = { input -> vm.addRepo(input); pop() },
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
            onSetColor = { r, color -> vm.setRepoColor(r, color) { updated -> applyRepoUpdate(updated) } },
            onDelete = vm::delete,
            onReclone = vm::reclone,
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
            onSetShowCommitInfo = vm::setShowCommitInfo,
            onSetFileNameDisplay = vm::setFileNameDisplay,
            onSetIconSet = vm::setIconSet,
            onSetRestoreLastPosition = vm::setRestoreLastPosition,
            onSetSelectByDefault = vm::setSelectByDefault,
            onClearCache = { vm.clearCache() },
            onLicenses = { navigate(Screen.Licenses) },
            onBack = { pop() },
        )

        Screen.Licenses -> LicensesScreen(onBack = { pop() })

        is Screen.Browse -> {
            val repo = current.repo
            // 1/2ペインのリポ一覧ドロワー。≡ で開き、リポ選択や設定遷移で閉じる。
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()

            // ひとつ上のディレクトリへ(共通実装に委譲)。
            fun goUp() = goUpBrowse(current)

            @Composable
            fun BrowserPane(threePane: Boolean, onMenu: (() -> Unit)? = null) {
                // ← の挙動:
                //  サブフォルダ: どのペインでもひとつ上の階層へ。
                //  ルート: 3ペインのみ非表示(レールがリポ切替/退出を担う)、1/2ペインはリポ一覧へ。
                val backAction: (() -> Unit)? = when {
                    current.path.isNotEmpty() -> ({ goUp() })
                    threePane -> null
                    else -> ({ leaveRepo() })
                }
                val favorites by vm.observeFavorites(repo.id).collectAsState(initial = emptyList())
                // ブラウズパス毎に一覧スクロールを保持(ファイル開閉/フォルダ戻りのアンマウントを跨ぐ)。
                navStateHolder.SaveableStateProvider("browse:${repo.id}/${current.path}") {
                FileBrowserScreen(
                    repo = repo,
                    path = current.path,
                    busy = status.busy,
                    loadDir = { vm.listDir(repo, it) },
                    loadBranches = { vm.listBranches(repo) },
                    onSync = { vm.syncNow(repo) },
                    onSearch = { navigate(Screen.Search(repo, current.path)) },
                    onGraph = { graphSelected = null; navigate(Screen.Graph(repo)) },
                    onMemos = { navigate(Screen.Memos(repo)) },
                    onFavorites = { navigate(Screen.Favorites(repo)) },
                    favoritePaths = favorites.mapTo(HashSet()) { it.relPath },
                    onToggleFavorite = { e -> vm.toggleFavorite(repo.id, e.relPath, e.isDir) },
                    onNavigateToDir = { target -> navigateToDir(repo, target) },
                    onSetTheme = { mode -> vm.setRepoTheme(repo, mode) { updated -> applyRepoUpdate(updated) } },
                    onOpenDir = { navigate(Screen.Browse(repo, it)) },
                    onOpenFile = {
                        if (detailStack.lastOrNull()?.filePath != it) pushDetail(Screen.View(repo, it))
                    },
                    onOpenHistory = { p, isSub -> historySelected = null; navigate(Screen.History(repo, p, isSub)) },
                    iconSet = settings.iconSet,
                    fileNameDisplay = settings.fileNameDisplay,
                    // 設定 ON のときだけ最終コミット取得関数を渡す(OFF は null=走査ゼロ)。
                    loadCommitMeta = if (settings.showCommitInfo) {
                        { p, es -> vm.dirCommitMeta(repo, p, es) }
                    } else {
                        null
                    },
                    onSwitchBranch = { branch ->
                        vm.switchBranch(repo, branch) { updated ->
                            val i = backStack.indexOfLast { it is Screen.Browse }
                            if (i >= 0) {
                                while (backStack.lastIndex > i) backStack.removeAt(backStack.lastIndex)
                                backStack[i] = Screen.Browse(updated, "")
                            }
                            clearDetails() // 作業ツリー書換でファイルが変化/消滅しうる
                            focusMode = false
                        }
                    },
                    onBack = backAction,
                    onMenu = onMenu,
                    onUp = { goUp() },
                    // 3ペインは左レール(リポ一覧)が グラフ/お気に入り を担うので上部の常設行は出さない。
                    showRepoActions = !threePane,
                )
                }
            }

            @Composable
            fun ViewerPane(file: Screen.View, showBack: Boolean, onTitleClick: (() -> Unit)? = null) {
                // ファイル毎に状態をスコープ。キー変更で中身は作り直しつつ、saveable(スクロール等)は holder に保持。
                navStateHolder.SaveableStateProvider("view:${file.repo.id}/${file.sha}/${file.filePath}") {
                    val repoMemos by vm.observeMemos(file.repo.id).collectAsState(initial = emptyList())
                    // sha != null = そのコミット時点の版を blob から表示する履歴モード(読み取り専用)。
                    val histSha = file.sha
                    val historical = histSha != null
                    val notFound = "この時点のファイルは見つかりません"
                    FileViewerScreen(
                        repo = file.repo,
                        filePath = file.filePath,
                        workDir = vm.workDirOf(file.repo),
                        loadText = if (historical) {
                            { cs, max -> vm.readBlobText(file.repo, file.filePath, histSha!!, cs, max) ?: error(notFound) }
                        } else {
                            { cs, max -> vm.readFile(file.repo, file.filePath, cs, max) }
                        },
                        probeFile = if (historical) {
                            { vm.probeBlob(file.repo, file.filePath, histSha!!) ?: error(notFound) }
                        } else {
                            { vm.probeFile(file.repo, file.filePath) }
                        },
                        revisionLabel = if (historical) "コミット ${shortSha(histSha!!)} 時点" else null,
                        fontScale = settings.fontScale.scale,
                        defaultWrap = settings.wrapByDefault,
                        onToggleWrap = vm::setWrapByDefault,
                        defaultSelectable = settings.selectByDefault,
                        onToggleSelectable = vm::setSelectByDefault,
                        linkOpenMode = settings.linkOpenMode,
                        showLineNumbers = settings.showLineNumbers,
                        tableMode = settings.tableMode,
                        stickyHeadings = settings.stickyHeadings,
                        targetLine = file.line,
                        onHistory = { historySelected = null; navigate(Screen.History(file.repo, file.filePath)) },
                        onMemos = { navigate(Screen.Memos(file.repo)) },
                        onNavigateToFile = { path -> pushDetail(Screen.View(file.repo, path)) },
                        onNavigateToDir = { dir ->
                            // ディレクトリリンク: 開いているファイルを畳んでブラウザを当該フォルダへ。
                            clearDetails()
                            focusMode = false
                            navigate(Screen.Browse(file.repo, dir))
                        },
                        onBack = { handleBack() },
                        showBack = showBack,
                        onTitleClick = onTitleClick,
                        // 履歴モードは作業ツリーに無いので前後送り・メモは無効。
                        loadSiblings = if (historical) {
                            { emptyList() }
                        } else {
                            {
                                val dir = file.filePath.substringBeforeLast('/', "")
                                vm.listDir(file.repo, dir)
                                    .filter { !it.isDir && !it.isSubmodule && !it.isLfs }
                                    .map { it.relPath }
                            }
                        },
                        onOpenSibling = { path ->
                            if (detailStack.isNotEmpty()) {
                                detailStack[detailStack.lastIndex] = Screen.View(file.repo, path)
                            }
                        },
                        memos = if (historical) emptyList() else repoMemos,
                        onAddMemoEntry = if (historical) {
                            { _, _, _, _, _ -> }
                        } else {
                            { memoId, ls, le, quote, comment ->
                                vm.addMemoEntry(memoId, file.filePath, ls, le, quote, comment)
                            }
                        },
                        onCreateMemoWithEntry = if (historical) {
                            { _, _, _, _, _ -> }
                        } else {
                            { title, ls, le, quote, comment ->
                                vm.createMemoWithEntry(file.repo.id, title, file.filePath, ls, le, quote, comment)
                            }
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
                // 集中モード(全幅)は多ペインでのみ意味を持つ。1ペインは常に全幅なので無効化する。
                val focused = focusMode && file != null && two

                when {
                    // 集中モード: レール・一覧を隠して全幅ビューア。
                    focused -> CodeLeafTheme(repo.themeMode) {
                        Box(Modifier.fillMaxSize()) {
                            // タイトルタップ=集中解除(○< と同じ。ファイルは残しブラウザを再表示)。
                            ViewerPane(file!!, showBack = false, onTitleClick = { focusMode = false })
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
                    // 1/2ペイン: 縦レールは廃し、≡ で開くリポ一覧ドロワー(=RepoListScreen 再利用)に集約。
                    else -> ModalNavigationDrawer(
                        drawerState = drawerState,
                        // 1ペインでファイル表示中はビューアの横スクロールと競合するためスワイプ無効。
                        gesturesEnabled = !(file != null && !two),
                        drawerContent = {
                            ModalDrawerSheet {
                                CodeLeafTheme(settings.defaultTheme) {
                                    RailPane(
                                        selectedRepoId = repo.id,
                                        onItemSelected = { drawerScope.launch { drawerState.close() } },
                                    )
                                }
                            }
                        },
                    ) {
                        CodeLeafTheme(repo.themeMode) {
                            val openDrawer: () -> Unit = { drawerScope.launch { drawerState.open() } }
                            if (two) {
                                Row(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(0.4f)) { BrowserPane(threePane = false, onMenu = openDrawer) }
                                    VerticalDivider()
                                    Box(Modifier.weight(0.6f)) { ViewerArea(file, showBack = false) }
                                }
                            } else if (file != null) {
                                // 1ペインでファイル表示: 常に全幅なので集中ハンドル(○<)は出さない。←=閉じる。
                                // タイトルタップも ← と同じくブラウザへ戻す。
                                ViewerPane(file, showBack = true, onTitleClick = { handleBack() })
                            } else {
                                BrowserPane(threePane = false, onMenu = openDrawer)
                            }
                        }
                    }
                }
            }
        }

        is Screen.Search -> CodeLeafTheme(current.repo.themeMode) {
            SearchScreen(
                repoName = current.repo.name,
                initialPath = current.path,
                loadCorpus = { vm.loadSearchCorpus(current.repo) },
                onOpenFile = { path, line ->
                    pop() // Search を閉じて Browse(+右ペイン) に戻してから、その深さで開く
                    pushDetail(Screen.View(current.repo, path, line))
                },
                onBack = { pop() },
            )
        }

        is Screen.Graph -> {
            val repo = current.repo
            // リポが変わったら(別リポのグラフへ切替/戻る)選択コミットを破棄する。
            // 残ると別リポ SHA で commitDiff して "diff取得失敗" になる。
            LaunchedEffect(repo.id) { graphSelected = null }

            @Composable
            fun GraphPane(selectedSha: String?, multiPane: Boolean, onSelect: (GraphCommit) -> Unit) {
                // リポ毎・ブランチ毎に作り直す。リポ id だけだと別リポ切替で前コミットが残り
                // commitDiff が別リポ SHA で失敗する。branch も含めるのは長押し切替後に再読込して
                // 現在ブランチ強調(RefChip)と未到達グレーを更新するため。
                key(repo.id, repo.branch) {
                    CommitGraphScreen(
                        repoName = repo.name,
                        branch = repo.branch,
                        accentColor = repoAvatarColor(repo),
                        multiPane = multiPane,
                        loadGraph = { vm.commitGraph(repo) },
                        onBack = { handleBack() },
                        selectedSha = selectedSha,
                        onSelectCommit = onSelect,
                        onSwitchBranch = { branch ->
                            vm.switchBranch(repo, branch) { updated ->
                                // Graph は WithRepo なので backStack の該当画面が更新版へ差し替わり、
                                // repo.branch 変化で key が変わりグラフが再読込される。選択は作業ツリー変化で無効化。
                                applyRepoUpdate(updated)
                                graphSelected = null
                            }
                        },
                        onSync = { vm.syncNow(repo) },
                        busy = status.busy,
                    )
                }
            }

            BoxWithConstraints {
                val three = maxWidth >= THREE_PANE_MIN_WIDTH
                val two = maxWidth >= TWO_PANE_MIN_WIDTH

                @Composable
                fun ContentPanes() {
                    if (!two) {
                        GraphPane(selectedSha = null, multiPane = false, onSelect = { navigate(Screen.CommitDetail(repo, it)) })
                    } else {
                        val sel = graphSelected
                        // コミット選択中だけ一覧を畳める(未選択時は一覧を出す)。
                        val showList = sel == null || !graphListCollapsed
                        Row(Modifier.fillMaxSize()) {
                            if (showList) {
                                Box(Modifier.weight(0.45f)) {
                                    GraphPane(selectedSha = sel?.sha, multiPane = true, onSelect = { graphSelected = it })
                                }
                                VerticalDivider()
                            }
                            Box(Modifier.weight(0.55f)) {
                                if (sel != null) {
                                    key(sel.sha) {
                    CommitDetailContent(
                        sel,
                        loadDiff = { vm.commitDiff(repo, sel.sha) },
                        onOpenFile = { openDiffFile(repo, it, sel.sha) },
                    )
                }
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
                    isSubmodule = current.isSubmodule,
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
                        HistoryPane(selSha = null, onSelect = { navigate(Screen.Diff(repo, filePath, it, current.isSubmodule)) })
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
                                    key(sel.sha) {
                    FileDiffPane(
                        sel,
                        loadDiff = { vm.fileDiff(repo, filePath, sel.sha) },
                        onOpenFile = { openDiffFile(repo, it, sel.sha) },
                        loadSubmoduleChange = if (current.isSubmodule) {
                            { vm.submoduleChange(repo, filePath, sel.sha) }
                        } else {
                            null
                        },
                    )
                }
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

        is Screen.Diff -> CodeLeafTheme(current.repo.themeMode) {
            DiffScreen(
                commit = current.commit,
                loadDiff = { vm.fileDiff(current.repo, current.filePath, current.commit.sha) },
                onBack = { pop() },
                onOpenFile = { openDiffFile(current.repo, it, current.commit.sha) },
                loadSubmoduleChange = if (current.isSubmodule) {
                    { vm.submoduleChange(current.repo, current.filePath, current.commit.sha) }
                } else {
                    null
                },
            )
        }

        is Screen.CommitDetail -> CodeLeafTheme(current.repo.themeMode) {
            CommitDetailScreen(
                commit = current.commit,
                loadDiff = { vm.commitDiff(current.repo, current.commit.sha) },
                onBack = { handleBack() },
                onOpenFile = { openDiffFile(current.repo, it, current.commit.sha) },
            )
        }

        is Screen.Memos -> CodeLeafTheme(current.repo.themeMode) {
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

        is Screen.MemoDetail -> CodeLeafTheme(current.repo.themeMode) {
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

        is Screen.Favorites -> CodeLeafTheme(current.repo.themeMode) {
            val repo = current.repo
            val favList by vm.observeFavorites(repo.id).collectAsState(initial = emptyList())
            FavoritesScreen(
                repoName = repo.name,
                favorites = favList,
                iconSet = settings.iconSet,
                checkExists = { rel -> vm.favoriteExists(repo, rel) },
                onOpen = { fav ->
                    // お気に入り画面を閉じてから対象へ遷移する。
                    pop()
                    if (fav.isDir) {
                        navigate(Screen.Browse(repo, fav.relPath))
                    } else {
                        openFileAt(repo, fav.relPath)
                    }
                },
                onDelete = { id -> vm.deleteFavorite(id) },
                onReorder = { ids -> vm.reorderFavorites(ids) },
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

