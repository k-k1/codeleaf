# CodeLeaf

複数の Git リポジトリ (GitHub.com / Bitbucket Cloud) を clone し、**Markdown を中心に閲覧する読み取り専用の Android リーダー**。add / commit / push は行わず、同期はローカル変更を破棄して常にリモートへ揃える。

> 旧称 git-reader。package / applicationId は `jp.lazmix.codeleaf`。

## 主な機能

- **複数リポ管理** — トークン方式 (GitHub PAT / Bitbucket API token) と OAuth (GitHub Device Flow / Bitbucket Authorization Code Grant) の両対応。グループ (Working Set) 分け・並べ替え。
- **Markdown 整形表示** — GFM (見出し / 表 / タスクリスト / コードブロック)、相対画像・相対リンク解決、`:emoji:`、`mermaid` 図 (WebView でオフライン描画)、frontmatter・目次。
- **コード表示** — シンタックスハイライト (Prism4j 同梱言語)、行番号・折返しの切替、拡張子別ファイルアイコン (Devicon / Material / VS Code / Seti)。
- **履歴とグラフ** — コミットグラフ (lane レイアウト・ref バッジ)、コミット詳細、unified diff。
- **全文検索** — リポ内のファイル名・本文を横断検索。
- **同期** — 手動のみ (`fetch → reset --hard origin/<branch> → clean -fdx`)。pull-to-refresh とカードの ⟳ の二経路。
- **テーマ** — ライト / ダーク等をリポジトリ毎に指定。大画面は幅に応じて 2〜3 ペイン化。

## 技術スタック

Kotlin + Jetpack Compose (Material3) / MVVM + StateFlow / 手動 DI。JGit 7.6 / Markwon 4.6.2 (+WebView で Mermaid) / Prism4j / Room (v4) / 認証情報は Android Keystore で暗号化。minSdk 31 / targetSdk 35。

## ビルドと開発

ソースは `android/`。セットアップ・ビルド・テスト・OAuth 設定の手順は **[`android/DEVELOPMENT.md`](android/DEVELOPMENT.md)** を参照。

```bash
cd android
./gradlew assembleDebug               # デバッグ APK
./gradlew testDebugUnitTest           # JVM 単体テスト
./gradlew connectedDebugAndroidTest   # 計装テスト (要実機/エミュ)
```

## ドキュメント

- 設計判断・画面設計・git 処理フロー: [`DESIGN.md`](DESIGN.md)
- 実装の要点・ハマりどころ: [`CLAUDE.md`](CLAUDE.md)
- 開発環境・OAuth 設定: [`android/DEVELOPMENT.md`](android/DEVELOPMENT.md)
- アイコン出典・ライセンス: `android/app/src/main/assets/ICON_ATTRIBUTION.md`
