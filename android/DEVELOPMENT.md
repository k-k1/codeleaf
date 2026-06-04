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

## 7. 再現性の担保について
- **SDK/エミュは git に入れない**。代わりに本書の手順 + 固定済みバージョンで再現する。
- さらに自動化したい場合は **Gradle Managed Devices**（`build.gradle` にデバイスを宣言すると
  Gradle が system image を自動取得してヘッドレス実行）を導入するとCIでも完全再現できる。
