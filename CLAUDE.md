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
package `com.k1.gitreader` / minSdk 33 / targetSdk 35。ソースは `android/app/src/main/java/com/k1/gitreader/`(data/git/render/ui)。

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
- **kapt** は `kotlin("kapt")` を **version なし**で適用(catalog alias は失敗)。
  `configurations.all { exclude(group="org.jetbrains", module="annotations-java5") }` で dex 重複を回避。
- **Prism4j 同梱言語のみ**ハイライト可(bash / typescript / rust は不可)。
- **Markdown 本文は `AndroidView(TextView)`** で Compose セマンティクスから不可視 → 本文/リンクは Compose test で検証不可。
  検証は (a)ロジックを純粋関数化し JVM 単体, (b)到達は Compose ノード(CodeView/表/frontmatter/見出し), (c)実機 uiautomator。
- **リンク**: `setTextIsSelectable(true)` は MovementMethod を奪う → 使わず setMarkdown 後に `LinkMovementMethod` を明示。
  相対 .md はアプリ内遷移・外部リンクは設定で CustomTabs / 外部ブラウザ。
- **GitHub HTTPS 認証**: username 空だと 401 → `JgitClient.credentials` が `x-access-token` を補う。PAT は Contents: Read-only 必須。
- **認証種別**: `Repo.authType` = TOKEN(手入力 PAT/API token) / OAUTH。OAuth token は `TokenStore` の `oauth_<id>` に
  JSON 暗号化保存。git の username はホスト/種別で分岐(`gitUsernameFor`): Bitbucket OAuth=`x-token-auth`、GitHub=`x-access-token`。
- **Bitbucket OAuth**(`data/oauth/`): Authorization Code Grant。client_id/secret は local.properties→BuildConfig(git管理外)。
  redirect `gitreader://oauth` を `OAuthRedirectActivity` が受け、`BitbucketOAuthService` が code 交換。access token 1h 失効→
  `RepoRepository.credentialsFor` が sync 直前に refresh(同一リポ Mutex 内)。交換は `OAuthTokenExchanger` interface に隔離
  (将来バックエンド代行へ差し替え可)。PKCE/Device Flow 非対応。設定手順は `android/DEVELOPMENT.md` §9。
  OAuth ログイン後は URL 手入力でなく `BitbucketApi`(`/2.0/repositories?role=member`)で clone 可能リポを取得し
  プルダウン選択(登録済みは `normalizeRepoUrl` で除外)。URL 手入力欄はトークン方式のときだけ。
- **同期**: `fetch → reset --hard origin/<branch> → clean -fdx`(ローカル変更は破棄)。
  `RepoRepository.sync` はリポ毎 Mutex で直列化し、実行中は FileBrowser をブロックする。
- **整形/スティッキー**: セクションは LazyColumn 化しない(Mermaid WebView 再生成回避)。
  表ヘッダ・見出しの固定は `graphicsLayer.translationY + zIndex + positionInRoot` による擬似スティッキー。
- **ファイルアイコン**: 拡張子 → 種別キー → `assets/<セット>/<キー>.svg` を Coil で描画(`ui/FileIcons.kt`)。
  セットは設定で切替(Devicon/Material/VS Code/Seti, `SettingsStore.IconSet`)。Devicon の黒ロゴと Seti 全体は
  ティント(Seti はタイプ別 `SETI_COLOR`)。セット未収録キー(Seti の groovy/nodejs)・フォルダ・未対応は
  `res/drawable` ベクターにフォールバック。出典/License は `assets/ICON_ATTRIBUTION.md`。既定セットは Material。
- **特殊ファイル強調**: `FileIcons.mark(name)` がファイル名で分類(優先順 AI>機密>生成物>ドキュメント>ドット始まり)。
  行描画(`EntryRow`)で 先頭バー＋文字色＋チップ等を付与。AI=tertiary+「AI」, 機密(.env/鍵)=error+「!」,
  生成物/ロック=減光斜体, README等=太字, ドット始まり=薄グレー。種別アイコン自体は変えない。
- **E2E**: `Git.init().setInitialBranch("main")` で端末上にローカルリポを作り `file パス`で clone(NW 不要・credentials 無視)。
  @Before で `container.repoRepository` の既存リポを一掃して決定論化。

## さらに詳しく
設計判断の全体・全機能リスト・設定項目・テスト一覧は メモリ `project-spec-v1` と `android/DEVELOPMENT.md` を参照。
