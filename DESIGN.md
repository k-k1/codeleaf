# CodeLeaf 設計ドキュメント

> 旧称 git-reader。package / applicationId は `jp.lazmix.codeleaf`。
> このドキュメントは設計判断の根拠を残す。実装の最新事実は `CLAUDE.md`・`android/DEVELOPMENT.md` を正とする。

複数の git リポジトリ (GitHub / Bitbucket Cloud) を clone し、**Markdown を中心にコードを閲覧する読み取り専用 Android リーダー**。
add / commit / push は行わない。ローカルの変更は同期時に常に破棄する。

---

## 1. 要件

### 目的
- GitHub.com / Bitbucket.org の複数リポジトリを登録し、指定ブランチを fetch/pull して閲覧する
- メインユースケースは **Markdown ドキュメントの整形表示**

### 機能
| 区分 | 内容 |
|---|---|
| リポジトリ管理 | URL + 認証で登録、複数管理、グループ(Working Set)分け・並べ替え、削除 |
| 認証 | HTTPS。**トークン方式**(GitHub=PAT / Bitbucket Cloud=API token)と **OAuth**(GitHub=Device Flow / Bitbucket=Authorization Code Grant)。self-hosted 非対応 |
| ブランチ | 全ブランチを **直近更新順** に一覧・フィルタ・選択、閲覧中の切替も可 (fetch 方式) |
| 同期 | **手動のみ**。`fetch → reset --hard origin/<branch> → clean -fdx`（ローカル変更は破棄） |
| 閲覧 | ファイル探索 / コード表示(ハイライト) / **Markdown 整形(メイン)** / コミットグラフ・履歴・unified diff |
| 検索 | リポ内の**全文検索**(ファイル名・本文) |
| テーマ | ダーク/ライト等を **リポジトリ毎** に指定 |

### 非対象
- 書き込み系 (add/commit/push/branch作成)
- self-hosted (GHE / Bitbucket Server)
- Git LFS の**実体取得**(ポインタは検出し Viewer で開かない扱い)
- 自動 / バックグラウンド同期

---

## 2. 技術スタック

| 領域 | 採用 | 備考 |
|---|---|---|
| 言語/UI | Kotlin + Jetpack Compose | MVVM + StateFlow |
| git | **JGit** | Android 唯一の実用解。IO スレッドで実行 |
| Markdown | **Markwon** 4.6.2 | core / ext-tables / ext-strikethrough / ext-tasklist / html / image / syntax-highlight。自動リンクは CommonMark autolink 拡張(Android Linkify は不使用) |
| 絵文字 | emoji-java + shortcode→unicode 変換プラグイン | `:smile:` など |
| Mermaid | アプリ内同梱 `mermaid.min.js` + **WebView** | オフライン描画、テーマ連動 CSS |
| 永続化 | Room (v4) + SharedPreferences | Room は `Repo` のみ。設定は SharedPreferences、ブランチは都度算出(キャッシュ DB は持たない) |
| 認証保存 | Android Keystore + EncryptedSharedPreferences | token / OAuth 資格情報を暗号化 |
| min SDK | **31 (Android 12)** / target 35 | 12+ 端末対応 |

---

## 3. アーキテクチャ

```
:app
 ├─ ui/        Compose 画面 + ViewModel (MVVM, StateFlow)
 │     RepoList / AddRepo / RepoEdit / FileBrowser / FileViewer /
 │     CommitGraph / CommitDetail / History / Diff / Search / Settings
 ├─ data/
 │   ├─ db/        Room: Repo (v4) + RepoDao
 │   ├─ crypto/    TokenStore (Keystore + EncryptedSharedPreferences)
 │   ├─ oauth/     GitHub Device Flow / Bitbucket Auth-Code Grant + REST API
 │   ├─ RepoRepository  Repository 層 (UI ↔ git/db/oauth の仲介)
 │   └─ SettingsStore   アプリ設定 (SharedPreferences)
 ├─ git/         JgitClient (clone/fetch/reset/clean/branches/log/diff/submodule)
 └─ render/      MarkdownRenderer (Markwon) + MermaidWebView + GitGraphLayout
```

### データモデル
- **Room は `Repo` エンティティのみ**(`@Database(entities=[Repo], version=4)`)。
  `Repo(id, name, url, host, username, branch, themeMode, lastSyncedAt, sortOrder, colorTag, authType, groupName)`
  - token / OAuth 資格情報はここに持たず、`TokenStore`(Keystore)に `id` 紐付けで暗号化保存
  - `authType` = TOKEN / OAUTH、`groupName` = Working Set、`colorTag` = 一覧アバター色
- **ブランチ一覧はキャッシュ DB を持たず**、`refs/remotes/origin/*` から都度算出する(下記 §4)
- **設定は Room ではなく `SettingsStore`(SharedPreferences)**: 既定テーマ / フォント倍率 / コード折返し /
  diff 折返し / 行番号 / テーブル表示 / 見出しスティッキー / リンク開き方 / アイコンセット / グループ

---

## 4. git 処理フロー (JGit)

### 登録時 (clone)
1. `UsernamePasswordCredentialsProvider(username, token)` を構築
2. `CloneCommand` で `filesDir/repos/<id>` に clone
3. 全ブランチ取得のため `+refs/heads/*:refs/remotes/origin/*` を fetch

### 同期 (手動)
```
fetch (+refs/heads/*)
→ reset --hard origin/<branch>
→ clean -fdx              # 未追跡ファイル含め完全破棄
→ Repo.lastSyncedAt 更新
```
リポ毎に Mutex で直列化する。OAuth リポは fetch 直前にアクセストークンを必要なら refresh する(同一 Mutex 内)。

### ブランチ一覧 (直近更新順)
- fetch 済みの `refs/remotes/origin/*` を列挙 → 各 ref を解決し committer date で降順ソート
- 両ホスト共通コード・追加 REST API 不要

### 認証メモ
| | GitHub | Bitbucket Cloud |
|---|---|---|
| トークン方式 | PAT (classic / fine-grained, Contents:Read 必須) | API token (App password は廃止方向) |
| OAuth | **Device Flow**(client_secret 不要 / scope=`repo`) | **Authorization Code Grant**(client_id+secret / redirect `codeleaf://oauth`) |
| git の username | TOKEN=任意・空なら `x-access-token` を補完 / OAuth=`x-access-token` | TOKEN=Atlassian メール (必須) / OAuth=`x-token-auth` |

→ トークン方式は「ユーザー名 + トークン」入力。OAuth はログイン後に API でアクセス可能リポを列挙して選択する
(URL 手入力はトークン方式のときだけ)。詳細手順は `android/DEVELOPMENT.md` §9(Bitbucket)・§10(GitHub)。

---

## 5. Markdown 描画 (render/)

- **GFM 基本**: 見出し/テーブル/タスクリスト/コードブロック(シンタックスハイライト)/リンク
- **リポ内画像/相対リンク**: `![](./img/x.png)` や `[](../doc.md)` を現在ファイルからの相対でリポジトリ内ファイルに解決し `file://` で Markwon に渡す。`.md` への相対リンクはアプリ内遷移
- **Mermaid**: ```` ```mermaid ```` ブロックを WebView (同梱 mermaid.js) で描画。リポのテーマに連動した CSS
- **絵文字**: `:smile:` を shortcode→unicode 変換
- **テーマ**: リポ毎設定を既定に描画、ビューア下部 `Aa` でその場上書き可

---

## 6. 画面設計

### 画面遷移
```
Home(リポ一覧) ─┬─ AddRepo / RepoEdit
                └─ FileBrowser(ファイル探索) ─┬─ FileViewer ─┬─ History ─ Diff
                       │  └ ⋮: テーマ/同期/グラフ           └ Diff
                       ├─ Search(全文検索) → FileViewer
                       ├─ CommitGraph → CommitDetail → Diff
                       └ branchチップ → ブランチ BottomSheet
```
- 大画面では `BoxWithConstraints` 幅で多ペイン化(≥600dp=2 / ≥960dp=3ペイン)。詳細は `CLAUDE.md`。

### 設計方針
- **操作系は画面下部に集約**（片手操作・親指で届く）
- ファイル探索は**パンくず式ドリルダウン**（巨大ツリーを縦展開しない）
- 同期は **pull-to-refresh + カードの ⟳** の二経路（手動のみ）

### ① ホーム（リポジトリ一覧）
```
┌────────────────────────────┐
│ CodeLeaf              ⚙   │
├────────────────────────────┤
│ ┌────────────────────────┐ │
│ │◐ my-docs               │ │
│ │  github · main         │ │
│ │  同期: 2分前        ⟳ │ │
│ └────────────────────────┘ │
│ ┌────────────────────────┐ │
│ │◐ team-wiki             │ │
│ │  bitbucket · develop   │ │
│ │  同期: 昨日         ⟳ │ │
│ └────────────────────────┘ │
│                        ＋ │  FAB=追加
└────────────────────────────┘
```

### ② リポジトリ追加 / 編集
```
┌────────────────────────────┐
│ ← リポジトリを追加          │
├────────────────────────────┤
│ ホスト   [GitHub ▾]        │
│ URL      [https://...     ]│
│ 表示名   [(任意)          ]│
│ ユーザー名[               ]│  Bitbucket必須/GitHub任意 注記
│ トークン [•••••••••  👁 ]│
│ テーマ   [システム追従 ▾]  │  ←リポ毎テーマ
│      [接続テスト] [保存・clone]│
└────────────────────────────┘
```

### ③ リポジトリブラウザ（パンくず下部）
```
┌────────────────────────────┐
│ ← my-docs  [main ▾]    ⋮  │  上: 戻る/ブランチ/⋮
├────────────────────────────┤
│ 📁 guide                   │
│ 📁 api                     │
│ 📄 README.md               │
│ 📄 CHANGELOG.md            │
│ 📄 config.yaml             │
│   ⟳ 下に引いて同期          │
├────────────────────────────┤
│ 📁 / › docs ›          ⤴  │  ★下部パンくず（⤴=ひとつ上）
└────────────────────────────┘
```

### ④ ブランチ選択（Bottom Sheet・直近更新順）
```
│ ブランチを選択              │
│ [🔍 フィルタ            ]  │
│ ● main          2分前      │  ●=選択中
│ ○ develop       1時間前    │
│ ○ feature/login 昨日       │
│ ○ release/1.2   3日前      │
```

### ⑤ ファイルビュー（Markdown=メイン）
```
┌────────────────────────────┐
│ ← README.md          ⋮    │  ⋮: 履歴/diff/共有
├────────────────────────────┤
│ # My Docs                  │
│ 本文（整形表示）…          │
│ ┌──────────┐               │
│ │mermaid図 │               │
│ └──────────┘               │
│ :smile: 絵文字             │
├────────────────────────────┤
│ [整形|Raw]  ☰目次   Aa    │  ←下部ツールバー
└────────────────────────────┘
```
- 既定は「整形」、`Raw` ワンタップ切替
- 非 Markdown はコード表示（シンタックスハイライト）

### ⑥ 履歴 → ⑦ diff（unified）
```
履歴                      diff a1b2c3
● a1b2c3 fix typo         README.md
  山田 · 2分前      →    @@ -1,4 +1,5 @@
● d4e5f6 add section       # My Docs
  佐藤 · 昨日             +新しい行     (緑)
                          -古い行       (赤)
```

### ⑧ コミットグラフ / ⑨ 全文検索
- **コミットグラフ**: lane レイアウト(`render/GitGraphLayout.kt`)で DAG を描画。ref バッジ付き、
  現在ブランチに到達しないコミットは減光。タップで CommitDetail → Diff。
- **全文検索**: リポ内のファイル名・本文を横断検索し、ヒットから Viewer へ。

### 設定（⚙ グローバル）
- 既定テーマ / フォント倍率 / コード折返し(本文・diff) / 行番号表示 / テーブル表示(インライン・横スクロール) /
  見出しスティッキー / リンクの開き方(アプリ内・外部ブラウザ) / ファイルアイコンセット / キャッシュ全削除

---

## 7. 技術リスクと検証順序

> いずれも検証済み・実装済み。以下は当時の着手順の記録。

1. **【最優先】JGit の Android 動作確認 (PoC)** — clone → fetch → reset --hard が通るか。
   検証は `androidTest/JgitInstrumentedTest.kt` に常設化。
2. Mermaid の WebView オフライン描画 + テーマ連動 CSS — `MermaidWebViewInstrumentedTest.kt`。
3. 相対画像/リンクのパス解決 — `MarkdownRenderer`。

---

## 8. 今後の検討
- シンタックスハイライト対応言語の拡張(現状は Prism4j 同梱言語に限られる)
- self-hosted (GHE / Bitbucket Server) 対応の是非
- OAuth トークン交換のバックエンド代行(現状はアプリ内で client_secret を扱う Bitbucket 経路がある)

> frontmatter(YAML) 表示・目次自動生成・全文検索は実装済み(§5・§6)。
