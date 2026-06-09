# CLAUDE.md

> 毎セッションのコンテキストに読み込まれる。**高シグナルだけ**を簡潔に保つ。
> 更新ルール: 1事実 = 1〜2行 / 重複・自明・git やコードから追える情報は書かない /
> 詳細はメモリ `project-spec-v1`・`DESIGN.md`・`android/DEVELOPMENT.md` へ退避し、ここはポインタに。
> **このファイルは 120 行を超えさせない**（超えそうなら削るか退避する）。

## 何のアプリか
複数 git リポ(GitHub / Bitbucket cloud)を clone し Markdown 中心に閲覧する **読み取り専用** Android
リーダー。push / commit はしない。確定仕様・画面モックは `DESIGN.md`。

## 技術スタック
Kotlin + Jetpack Compose(Material3) / MVVM + StateFlow / 手動DI(`GitReaderApplication.container = AppContainer`)。
JGit 7.6 / Markwon 4.6.2(+WebView で Mermaid) / Prism4j(kapt) / Room / token は AndroidKeystore 暗号化。
package `jp.lazmix.codeleaf` / minSdk 31 / targetSdk 35。ソースは `android/app/src/main/java/jp/lazmix/codeleaf/`(data/git/render/ui)。

## ビルド / テスト (PowerShell・cd android 前提)
- 環境: `$env:JAVA_HOME="C:\programs\java\jdk-21.0.9+10"; $env:ANDROID_SDK_ROOT="C:\Android\Sdk"`
- ビルド: `.\gradlew.bat assembleDebug`
- JVM単体: `.\gradlew.bat testDebugUnitTest --tests "<FQN>"`
- 計装(GMD): `.\gradlew.bat pixel6Api35DebugAndroidTest [-Pandroid.testInstrumentationRunnerArguments.class=<FQN>]`
- adb は `C:\Android\Sdk\platform-tools\adb.exe`。スクショは `adb shell screencap -p /sdcard/x.png; adb pull ...`
  (PowerShell の `>` リダイレクトはバイナリを壊す)。

## 進め方
- 1機能 = 1スライス: 実装 → assembleDebug → (該当なら)JVM/GMD テスト → commit → push(origin/main 逐次)。
- Write/Edit の file_path は**必ず絶対パス**(cwd=android だと相対は android/android/ に作られる)。
- git は cwd ズレ回避に `git -C C:/private_workspace/git-reader ...`。コミット末尾に Co-Authored-By 行。

## ハマりどころ(コードから読み取りにくい点)
- **ナビ/多ペイン**(`ui/GitReaderApp.kt`): 手書き `backStack`。**開いているファイルは `detailStack`**(backStack と直交)。
  幅 `BoxWithConstraints` で `>=600dp`=2ペイン / `>=960dp`=3ペイン(左=リポ一覧レール｜中=一覧｜右=詳細)、未満は全画面。
  **1/2ペインの Browse はハンバーガー(`FileBrowserScreen` の `onMenu`=≡)→`ModalNavigationDrawer`**(中身は `RepoListScreen` 再利用・
  `drawerState`/`drawerScope` は Browse 枝で保持、選択で `onItemSelected`→`drawerState.close()`、1ペインのファイル表示中だけ `gesturesEnabled=false`)。
  **`IconRail`(64dp・`RepoAvatar`)は3ペイン専用**(`ThreePaneScaffold`・`railCollapsed` で展開レール↔IconRail)。テーマは レール/ドロワー=default/中右=repo に分割。
  **お気に入りはリポ一覧(`RepoListScreen` の各 `RepoCard` の★・`onOpenFavorites`)から直行**(ドロワー/List全画面/3ペインレール共通)。
  **集中モード `focusMode`**(rememberSaveable): ファイル表示中にレール+一覧を隠し全幅ビューア(1/2/3共通)。下部左の `PaneToggleHandle` で開閉、
  **戻るは集中解除を優先**(`handleBack`: focus→**フォルダ上げ**→detailStack→pop の順)。リポ退出/ブランチ切替で false に戻す。
  多ペインで「ファイルを開いた後にフォルダを潜った」場合は detailStack を閉じる前に `goUpBrowse` でフォルダを一つ上げる
  (操作順どおりに巻き戻す)。判定は detailStack と並走する `detailDepth`(開いた時点の `backStack` 深さ)を `pushDetail/popDetail/clearDetails` で同期し比較。
  履歴(`Screen.History`)・グラフは2/3ペイン(左=一覧/右=`FileDiffPane`等、`historySelected`/`graphSelected`、各 `*ListCollapsed`)。
  リポ追加の＋は一覧0件時のみ右上、1件以上は `RepoEditScreen` の右上。`MainActivity` の `configChanges` で回転は状態保持。
  **状態永続化**: `Screen`/`Repo`/`GraphCommit` を `@Parcelize`(`kotlin("plugin.parcelize")`, Instant は `InstantParceler`)、
  `backStack`/`detailStack`/`graphSelected`/`focusMode` を `rememberSaveable` でプロセス死から復元。
  **instrumented E2E は portrait(compact)前提** — landscape で実行すると2/3ペインになり一部 assert が崩れる。
- **ブラウザ**(`FileBrowserScreen`): 上部に GitHub 風パンくず(`PathBreadcrumb`・🏠＋各フォルダ、祖先タップで `onNavigateToDir`→`navigateToDir` が
  スタックを当該 Browse まで畳む/無ければ置換)。ファイル名表示は設定 `fileNameDisplay`(`FileNameText`)= 折り返し(既定)/中央省略/末尾省略。
  中央省略は Compose1.7 に `MiddleEllipsis` が無いため `TextMeasurer`＋`onSizeChanged` で自前(BoxWithConstraints は `IntrinsicSize.Min` 行で不可)。
  **単一子フォルダ連鎖は畳む**(`RepoRepository.collapseDirChain`・設定 `collapseFolders` で切替)= `src/main/java` を1エントリ(`FileEntry.displayName`)にし relPath は最深、タップで直行(submodule は越えない)。
- **kapt** は `kotlin("kapt")` を **version なし**で適用(catalog alias は失敗)。
  `configurations.all { exclude(group="org.jetbrains", module="annotations-java5") }` で dex 重複を回避。
- **ハイライト**: Prism4j 2.0.0 同梱文法を `@PrismBundle`(`PrismGrammarLocator`)で生成。未同梱の
  **bash / typescript / rust は手書き文法**を `CodeGrammarLocator`(GrammarLocatorDef をラップ)で重ねる
  (`CustomGrammars`、文字列内の変数補間など細部は未対応の実用サブセット)。別名/拡張子マップは `CodeHighlight.languageForFile`。
- **ファイル種別ビューア**(`FileViewerScreen`+`data/FileKind.kt`): `probeFile`(先頭8KB)で Text/Image/Pdf/Binary を判定
  (`FileClassifier`・magic+拡張子・純粋関数)。テキストは BOM→`juniversalchardet` でエンコード推定し**非UTF-8は再デコード**、
  上部スリムバー(`fileMetaLine`)にエンコード/BOM/改行/サイズ、画像はフォーマット/寸法/サイズ。画像は Coil で fit＋ピンチズーム、
  PDF は `PdfViewer`(`PdfRenderer`・**ARGB_8888必須**・同時1ページ→Mutex直列化・−/＋ で1〜3倍)、非画像バイナリは種別/サイズ/16進カード。
- **Markdown 本文は `AndroidView(TextView)`** で Compose セマンティクスから不可視 → 本文/リンクは Compose test で検証不可。
  検証は (a)ロジックを純粋関数化し JVM 単体, (b)到達は Compose ノード(CodeView/表/frontmatter/見出し), (c)実機 uiautomator。
- **リンク**: `setTextIsSelectable(true)` は MovementMethod を奪う → 使わず setMarkdown 後に `LinkMovementMethod` を明示。
  相対リンクは `RepoLinkResolver.resolveRepoTarget` で解決(`%XX`/`<…>` は decode)= 既存ファイル→ビューア / ディレクトリ→ブラウザ
  (`onNavigateToDir`・ルートは path="")。リポ外/不在は無視。外部リンクは設定で CustomTabs / 外部ブラウザ。
- **GitHub HTTPS 認証**: username 空だと 401 → `JgitClient.credentials` が `x-access-token` を補う。PAT は Contents: Read-only 必須。
- **認証種別**: `Repo.authType` = TOKEN(手入力 PAT/API token) / OAUTH。OAuth token は `TokenStore` の `oauth_<id>` に
  JSON 暗号化保存。git の username はホスト/種別で分岐(`gitUsernameFor`): Bitbucket OAuth=`x-token-auth`、GitHub=`x-access-token`。
- **Bitbucket OAuth**(`data/oauth/`): Authorization Code Grant。client_id/secret は local.properties→BuildConfig(git管理外)。
  redirect `codeleaf://oauth` を `OAuthRedirectActivity` が受け、`BitbucketOAuthService` が code 交換。access token 1h 失効→
  `RepoRepository.credentialsFor` が sync 直前に refresh(同一リポ Mutex 内)。交換は `OAuthTokenExchanger` interface に隔離
  (将来バックエンド代行へ差し替え可)。PKCE/Device Flow 非対応。設定手順は `android/DEVELOPMENT.md` §9。
  OAuth ログイン後は URL 手入力でなく `BitbucketApi`(`/2.0/repositories?role=member`)で clone 可能リポを取得し
  プルダウン選択(登録済みは `normalizeRepoUrl` で除外)。URL 手入力欄はトークン方式のときだけ。
- **GitHub OAuth**(`data/oauth/GitHubDeviceFlow*`): **Device Flow**。**client_secret 不要**(client_id のみ・`GITHUB_OAUTH_CLIENT_ID`)。
  redirect/`OAuthRedirectActivity` 不使用 → user_code 表示＋`GitHubDeviceFlowService.pollForToken` でポーリング。
  scope=`repo`(read-only repo scope が無く write も付くトレードオフ)。user token は無期限扱い(refresh 無し・`expiresAt`遠未来)。
  リポ一覧は `GitHubApi`(`/user/repos`)。UI は AddRepoScreen の host 別アコーディオン。設定手順は `DEVELOPMENT.md` §10。
  Device Flow のブラウザは**自動で開かない**(コードが隠れるため)→パネルの「ブラウザを開く」で開く。
- **OAuth ログイン共有**: 成功ログインを provider 単位で `TokenStore.oauth_session_<P>` に記憶し、Add 画面で再利用(再ログイン不要)。
  失効間近は `RepoRepository.rememberedOAuthSession` が refresh して保存し直す。各リポの git 認証は従来どおり clone 時に `oauth_<id>` へスナップショット。
- **同期**: `fetch → reset --hard origin/<branch> → clean -fdx`(ローカル変更は破棄)。
  `RepoRepository.sync` はリポ毎 Mutex で直列化し、実行中は FileBrowser をブロックする。
- **submodule**(`JgitClient.updateSubmodules`): clone/sync 後に init→update(1階層)。`.gitmodules` が **SSH URL**
  (`git@host:..`/`ssh://..`)だと JGit が取得できないため `sshToHttps` で **HTTPS に書換えてから** update(認証は親と同じ token cp)。
  **submodule 毎に個別 update＋リトライ**(`SUBMODULE_RETRIES`・1個の失敗が他を巻き込まない)。取得失敗は致命にせず `Log.w(JgitSubmodule)`。
  **未取得の可視化**: `listDir` が空の submodule ディレクトリを `FileEntry.submoduleUnfetched` にし、ブラウザで赤「未取得」バッジ(通常は紫「SUB」)。
  メイン URL の https 選択は Bitbucket API 側(`httpsCloneHref`)。
- **diff 表示**(`DiffScreen.kt` `DiffText`/`parseDiffRows`): `diff --git`/index/---/+++ 等のノイズ行を畳みファイル名ヘッダ帯に
  (非ASCIIは `gitUnquotePath` で8進復元)。ファイル毎に折りたたみ(`groupDiffByFile`)、追加緑/削除赤背景、@@ から行番号ガター、長行は自動改行。
- **整形/スティッキー**: セクションは LazyColumn 化しない(Mermaid WebView 再生成回避)。
  表ヘッダ・見出しの固定は `graphicsLayer.translationY + zIndex + positionInRoot` による擬似スティッキー。
- **ファイルアイコン**: 拡張子 → 種別キー → `assets/<セット>/<キー>.svg` を Coil で描画(`ui/FileIcons.kt`)。
  セットは設定で切替(Devicon/Material/VS Code/Seti, `SettingsStore.IconSet`)。Devicon の黒ロゴと Seti 全体は
  ティント(Seti はタイプ別 `SETI_COLOR`)。セット未収録キー(Seti の groovy/nodejs)・フォルダ・未対応は
  `res/drawable` ベクターにフォールバック。出典/License は `assets/ICON_ATTRIBUTION.md`。既定セットは Material。
- **特殊ファイル強調**: `FileIcons.mark(name)` がファイル名で分類(優先順 AI>機密>生成物>ドキュメント>ドット始まり)。
  行描画(`EntryRow`)で 先頭バー＋文字色＋チップ等を付与。AI=tertiary+「AI」, 機密(.env/鍵)=error+「!」,
  生成物/ロック=減光斜体, README等=太字, ドット始まり=薄グレー。種別アイコン自体は変えない。
- **メモ**(`data/db/Memo*`・`ui/Memos*`): リポ→**メモ帳**(`memos`)→**エントリ**(`memo_entries`: file/行範囲/引用/コメント)の2階層。
  Room **v5**(`MIGRATION_4_5`・親削除で CASCADE)。追加は **行/ブロックの長押し** → `AddMemoSheet`(行範囲＋メモ帳選択/新規)。
  行範囲指定は `RangeSlider`(連続値→整数丸め)＋`LineField`(円形 −＋ボタンは `RepeatingIconButton` で長押しオートリピート・数字タップで直接入力)。
  コード/Raw は `CodeView` の行長押し、**整形 Markdown は `MarkdownView`(TextView)の `OnLongClickListener`**＋`blockLineRange`(ブロックを全文検索しソース行算出)。
  共有/コピーは `MemoFormat`(Markdown 風・純粋関数)。入口はビューア⋮と FileBrowser⋮の「メモ」。
  ※整形本文は Compose セマンティクス不可視 → E2E はビュー階層から TextView を探し `performLongClick()`(`MarkdownMemoE2EInstrumentedTest`)。
- **E2E**: `Git.init().setInitialBranch("main")` で端末上にローカルリポを作り `file パス`で clone(NW 不要・credentials 無視)。
  @Before で `container.repoRepository` の既存リポを一掃して決定論化。

## さらに詳しく
設計判断の全体・全機能リスト・設定項目・テスト一覧は メモリ `project-spec-v1` と `android/DEVELOPMENT.md` を参照。
