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
   - Name: `git-reader`
   - **Callback URL**: `gitreader://oauth`
   - **Permissions**: Repositories → **Read**
2. 発行された **Client ID / Secret** を `android/local.properties` に追記（git 管理外）:
   ```
   BITBUCKET_OAUTH_CLIENT_ID=<Client ID>
   BITBUCKET_OAUTH_CLIENT_SECRET=<Secret>
   ```
3. 再ビルドすると AddRepoScreen に「Bitbucket でログイン」ボタンが出る。
   ログイン後に URL を入れて clone（手入力トークン不要）。access token は1時間で失効するが
   refresh token で自動更新される（再ログイン不要）。

仕組み: 3-legged Authorization Code Grant。`gitreader://oauth` を `OAuthRedirectActivity` が受け、
`BitbucketOAuthService` が code をトークンに交換（`data/oauth/`）。token 交換は `OAuthTokenExchanger`
interface に隔離してあり、将来「バックエンド代行」へ差し替え可能（公開配布時の secret 同梱対策）。

## 10. GitHub OAuth（任意・「GitHub でログイン」を使う場合）
GitHub は **OAuth 2.0 Device Flow**（RFC 8628）を採用。**client_secret 不要**（公開クライアント）なので
`local.properties` には **client_id だけ**置けば有効化される（無くてもビルド可・fine-grained PAT は常用可）。

1. GitHub → Settings → Developer settings → **OAuth Apps** → **New OAuth App**
   - Application name: `git-reader`
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
clone 時の git username は `x-access-token`（`gitUsernameFor`）。リポ一覧は `GitHubApi`(`/user/repos`)。
