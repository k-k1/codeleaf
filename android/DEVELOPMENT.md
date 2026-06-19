# 開発環境セットアップ（他PCで継続する人向け）

git には **アプリのソースと Gradle 設定のみ** をコミットしている。
Android SDK / system image / エミュ / `local.properties` は **マシン毎に用意**する（巨大バイナリ・OS依存・ライセンス物のためコミットしない）。
ビルドに必要なバージョンは `gradle/libs.versions.toml` と `app/build.gradle.kts` に固定済みなので、下記で同じ環境を再現できる。

## 1. 必要なもの
- JDK 17 以上（このリポは JDK 21 で動作確認）
- Android SDK command-line tools（Android Studio 同梱でも可）

## 2. SDK パッケージの導入
`sdkmanager` で以下を入れる（Android Studio を使う場合は SDK Manager GUI でも同じ）:

```bash
sdkmanager --licenses          # ライセンス同意
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64"
```

> CI などで非対話に同意したい場合は `$SDK/licenses/android-sdk-license` に
> ライセンスハッシュを直接置く方法もある。

## 3. local.properties（コミット対象外）
プロジェクト直下 `android/local.properties` に SDK の場所を書く:

```properties
sdk.dir=/path/to/Android/Sdk
# Windows 例: sdk.dir=C\:\\Android\\Sdk
```

## 4. ビルド
```bash
cd android
./gradlew assembleDebug      # debug APK
```

## 5. エミュレータ（ランタイム確認用）
```bash
# AVD 作成（初回のみ）
avdmanager create avd -n gitreader -k "system-images;android-35;google_apis;x86_64" -d pixel_6

# 起動
emulator -avd gitreader            # GUI
# emulator -avd gitreader -no-window -no-audio -gpu swiftshader_indirect   # ヘッドレス
```

実機を使う場合は system image 不要。USB デバッグを有効化して接続すればよい。

## 6. ランタイム ART 検証（計装テスト）
JGit が実機/エミュ上で動くことを `app/src/androidTest` の `JgitInstrumentedTest` で検証する
（公開リポを clone/fetch/reset/log/diff、ネットワーク必須）:

```bash
# エミュ or 実機を起動・接続した状態で
./gradlew connectedDebugAndroidTest
```

## 7. Gradle Managed Devices（推奨・CI/他PCで完全再現）
`app/build.gradle.kts` にデバイス `pixel6Api35` を宣言済み。手動でエミュを作らなくても、
Gradle が system image を自動取得し管理AVDをヘッドレス構築してテストまで流す:

```bash
cd android
./gradlew pixel6Api35DebugAndroidTest
# レポート: app/build/reports/androidTests/managedDevice/debug/allDevices/index.html
```

これが他PC/CIでの再現の本命。AVD作成（手順5）すら不要で、定義は git に入っている。

## 8. 再現性の担保について
- **SDK/system image/エミュ本体は git に入れない**（巨大・OS依存・ライセンス物）。
  再現は「本書の手順 + 固定済みバージョン(`gradle/libs.versions.toml`, `app/build.gradle.kts`) +
  Gradle Managed Devices 定義」で担保する。
- ローカルでサッと動かすなら手順5の手動エミュ + `connectedDebugAndroidTest`、
  CI/他PCでの確実な再現なら手順7の Managed Devices、という使い分け。

## 9. Bitbucket OAuth（任意・「Bitbucket でログイン」を使う場合）
OAuth は `local.properties` に client_id/secret がある時だけ有効化される（無くてもビルド可・手動トークンは常用可）。

1. Bitbucket Cloud → 対象 Workspace → **Workspace settings** → Apps and features → **OAuth clients**
   →「**Create OAuth client**」。
   - Name: `CodeLeaf`
   - **Callback URL**: `codeleaf://oauth`
   - **Permissions**: Repositories → **Read** ＋ Account → **Read**
     （Account:Read は CHANGE-2770 後のワークスペース列挙 `GET /2.0/user/workspaces` に必須。
     不足すると 403 でリポ一覧が取れない。権限変更後は要・再ログイン）
2. 発行された **Client ID / Secret** を `android/local.properties` に追記（git 管理外）:
   ```
   BITBUCKET_OAUTH_CLIENT_ID=<Client ID>
   BITBUCKET_OAUTH_CLIENT_SECRET=<Secret>
   ```
3. 再ビルドすると AddRepoScreen に「Bitbucket でログイン」ボタンが出る。
   ログイン後に URL を入れて clone（手入力トークン不要）。access token は1時間で失効するが
   refresh token で自動更新される（再ログイン不要）。

仕組み: 3-legged Authorization Code Grant。`codeleaf://oauth` を `OAuthRedirectActivity` が受け、
`BitbucketOAuthService` が code をトークンに交換（`data/oauth/`）。token 交換は `OAuthTokenExchanger`
interface に隔離してあり、将来「バックエンド代行」へ差し替え可能（公開配布時の secret 同梱対策）。

## 10. GitHub OAuth（任意・「GitHub でログイン」を使う場合）
GitHub は **OAuth 2.0 Device Flow**（RFC 8628）を採用。**client_secret 不要**（公開クライアント）なので
`local.properties` には **client_id だけ**置けば有効化される（無くてもビルド可・fine-grained PAT は常用可）。

1. GitHub → Settings → Developer settings → **OAuth Apps** → **New OAuth App**
   - Application name: `CodeLeaf`
   - Homepage URL: 任意（例 `https://example.com`）
   - Authorization callback URL: 任意（Device Flow では未使用。例 `https://example.com/callback`）
   - 作成後、アプリ設定で **「Enable Device Flow」にチェック**。
2. 発行された **Client ID** を `android/local.properties` に追記（git 管理外・**Secret は不要**）:
   ```
   GITHUB_OAUTH_CLIENT_ID=<Client ID>
   ```
3. 再ビルドすると GitHub タブの認証方法に「GitHub でログイン（OAuth）」が出る。
   ボタン押下 → 表示された **user_code** を控え、開いたブラウザ（`https://github.com/login/device`）で入力・承認
   → アプリが自動でトークンを取得 → 未登録リポをプルダウンから選んで clone。

**権限のトレードオフ（重要）**: classic OAuth App には read-only な repo scope が無く、private を読むには
scope `repo`（read+**write** 全権）が必要。本アプリは読むだけだがトークンには書き込み権限も付く。最小権限を
厳密に求めるなら GitHub App（Contents: Read-only）化が必要だがインストール手順が増えるため、個人利用前提で
OAuth App を採用した。user token は既定で無期限（`OAuthAccount.expiresAt` を遠未来に設定し refresh しない）。

仕組み: `GitHubDeviceFlowService` が `device/code` 取得 → `access_token` をポーリング（`data/oauth/GitHubDeviceFlow*`）。
redirect/Custom Tabs deep link は使わない（user_code 表示＋ポーリング方式）ので `OAuthRedirectActivity` は不要。

## 11. リリース手順（バージョン X.Y.Z を出す）
バージョンは `app/build.gradle.kts` の `appVersionName` 一箇所で管理（`versionCode` は機械算出）。
リリース署名は `local.properties` の `RELEASE_KEYSTORE` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`（git 管理外）。
GitHub Release（タイトル `CodeLeaf X.Y.Z`）に署名 APK を添付する運用。**`gh` CLI が必要**。

1. **CHANGELOG を追記**: `CHANGELOG.md` の先頭へ `## X.Y.Z (YYYY-MM-DD)` 節を足す
   （`### 新機能` / `### 修正` / `### 内部・整理`。前タグ以降の `feat`/`fix` を中心に。`chore`/`docs`/`test` は割愛）。
   差分は `git log --no-merges --format="%s" v<前版>..HEAD` で拾う。
2. **versionName を上げる**: `appVersionName = "X.Y.Z"`（コメントも「X.Y.Z リリース版」に）。
3. **リリースコミット**: `chore(android): X.Y.Z リリース版へ versionName を更新`（CHANGELOG と同コミットでも可）。
   APK に埋まる `gitSha` をこのコミットに合わせるため **ビルドはコミット後**に行う。
4. **署名 APK をビルド**: `cd android && ./gradlew assembleRelease`
   → `app/build/outputs/apk/release/codeleaf-X.Y.Z.apk`。
   署名検証: `"$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" verify --print-certs <apk>`（`CN=CodeLeaf, O=Lazmix`）。
   `sha256sum <apk>` を控える（リリースノートに載せる）。
5. **タグ＋push**: `git tag -a vX.Y.Z -m "CodeLeaf X.Y.Z"` → `git push origin main` → `git push origin vX.Y.Z`。
6. **GitHub Release 作成**: `gh release create vX.Y.Z <apk> --title "CodeLeaf X.Y.Z" --notes "..."`
   （ノートは前回踏襲: アプリ説明＋主な変更＝CHANGELOG 該当節＋インストール手順／minSdk/targetSdk／署名 DN／sha256）。
7. **次の開発版へ**: `appVersionName = "X.Y.(Z+1)"`（コメントは「X.Y.Z はタグ vX.Y.Z に凍結済み。これは次の開発版。」）
   → `chore(android): 次の開発版へ versionName を X.Y.(Z+1) に更新` → `git push origin main`。

---

# 環境別の実構成（実際に使っているマシン）

手順1〜10は汎用。ここからは **このプロジェクトで実際にビルド/実機検証しているマシン固有**の構成を、
OS ごとに具体値で残す（マシン移行・再 clone のときに迷わないため）。`gradlew` 系コマンドは原則 `cd android` 後。

## A. Linux（Ubuntu 26.04・現用のメイン環境）

すべて **ユーザー空間導入（sudo 不要）**。Temurin JDK 21 tarball ＋ Android cmdline-tools。

- **環境変数**: `source ~/android-dev/env.sh` で一括設定。
  - `JAVA_HOME=~/android-dev/jdk-21.0.11+10`
  - `ANDROID_SDK_ROOT=~/Android/Sdk`
  - `PATH` に `platform-tools` / `emulator` / `cmdline-tools/latest/bin` を追加
- **SDK 配置**: `~/Android/Sdk`（`platform-tools` / `platforms;android-35` / `build-tools;35.0.0` / `emulator` /
  `system-images;android-35;google_apis;x86_64`）。
- **local.properties**: `android/local.properties`（gitignore 済）に最低限 `sdk.dir=$HOME/Android/Sdk` の絶対パス
  （`local.properties` は変数展開しないので実値で書く。例 `sdk.dir=/home/<user>/Android/Sdk`）。
  OAuth を使うなら同ファイルに client_id/secret も置く（§9/§10）。
  **マシン移行/再 clone で local.properties は引き継がれない → 空だと AddRepo の OAuth 選択肢が丸ごと消える**
  （BuildConfig 経由で `*OAuthAvailable=false`）。追記後リビルドで復活。
- **gradlew の exec ビット**: Windows clone 由来で欠落しがち。`chmod +x gradlew`
  （`git update-index --chmod=+x gradlew` で stage 可能）。
- **ビルド/テスト**:
  ```bash
  cd android
  ./gradlew assembleDebug
  ./gradlew testDebugUnitTest --tests "<FQN>"
  ./gradlew connectedDebugAndroidTest    # 実機 USB 計装(エミュは下記 KVM 待ち→実機優先)
  ```
- **実機(USB)デバッグ**: 有効。`udev` ルール `/etc/udev/rules.d/51-android.rules` に VendorID を登録済み
  （`0fce`=Sony / `19d2`=ZTE、`MODE=0660 GROUP=plugdev`）。検証端末: Sony SOV43 / SO-51E / ZTE NP05J。
  ```bash
  ./gradlew installDebug
  adb exec-out screencap -p > x.png      # スクショ(`>` で OK)
  ```
- **新端末を挿したら**: `adb devices` が `no permissions` なら未登録ベンダー。`lsusb` で idVendor を確認し
  上記ルールに1行追加（要 sudo）→ `sudo udevadm control --reload-rules` → **ケーブル抜き差し**
  （既存接続には reload だけでは反映されない・add イベントが必要）。
- **署名不一致**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）: 端末に別署名の codeleaf が残っていることがある。
  `adb uninstall jp.lazmix.codeleaf`（**アプリ内データ消失・破壊的**）してから `installDebug`。
- **エミュ(GMD/x86_64)は KVM 待ち**: `/dev/kvm` は root:kvm 660。user を kvm グループに入れる必要がある
  （`sudo usermod -aG kvm $USER` ＋ 再ログイン）。実機があれば不要。
- **既知フレーク**: `PullToRefreshE2EInstrumentedTest` が実機フルスイートで稀にタイムアウト
  （「同期完了」スナックバー待ち15s・3回中1回程度）。未修正・機能影響なし。

## B. Windows（PowerShell）

- **環境変数**（PowerShell セッション内）:
  ```powershell
  $env:JAVA_HOME="C:\programs\java\jdk-21.0.9+10"
  $env:ANDROID_SDK_ROOT="C:\Android\Sdk"
  ```
- **local.properties**: `android/local.properties` に `sdk.dir=C\:\\Android\\Sdk`（バックスラッシュは要エスケープ）。
- **ビルド/テスト**:
  ```powershell
  cd android
  .\gradlew.bat assembleDebug
  .\gradlew.bat testDebugUnitTest --tests "<FQN>"
  .\gradlew.bat pixel6Api35DebugAndroidTest [-Pandroid.testInstrumentationRunnerArguments.class=<FQN>]
  ```
- **adb**: `C:\Android\Sdk\platform-tools\adb.exe`。
- **スクショ**: `adb shell screencap -p /sdcard/x.png; adb pull /sdcard/x.png`
  （**PowerShell の `>` リダイレクトはバイナリを壊す**ので使わない）。
- **絶対パス注意**: Write/Edit の file_path は必ず絶対パス。git の cwd ズレ回避に
  `git -C C:/private_workspace/git-reader ...`。
clone 時の git username は `x-access-token`（`gitUsernameFor`）。リポ一覧は `GitHubApi`(`/user/repos`)。
