# git-reader 設計ドキュメント (v1)

複数の git リポジトリ (GitHub / Bitbucket cloud) をチェックアウトし、**Markdown を中心にコードを閲覧する読み取り専用 Android リーダー**。
add / commit / push は行わない。ローカルの変更は同期時に常に破棄する。

---

## 1. 要件

### 目的
- GitHub.com / Bitbucket.org の複数リポジトリを登録し、指定ブランチを fetch/pull して閲覧する
- メインユースケースは **Markdown ドキュメントの整形表示**

### 機能
| 区分 | 内容 |
|---|---|
| リポジトリ管理 | URL + 認証情報で登録、複数管理、削除 |
| 認証 | HTTPS + token のみ。GitHub=PAT / Bitbucket Cloud=email+API token。self-hosted 非対応 |
| ブランチ | 全ブランチを **直近更新順** に一覧・フィルタ・選択、閲覧中の切替も可 (fetch 方式) |
| 同期 | **手動のみ**。`fetch → reset --hard origin/<branch> → clean -fdx`（ローカル変更は破棄） |
| 閲覧 | ファイル探索 / コード表示(ハイライト) / **Markdown 整形(メイン)** / コミット履歴・unified diff |
| テーマ | ダーク/ライト等を **リポジトリ毎** に指定 |

### 非対象 (v1)
- 書き込み系 (add/commit/push/branch作成)
- self-hosted (GHE / Bitbucket Server)
- Git LFS
- 自動 / バックグラウンド同期

---

## 2. 技術スタック

| 領域 | 採用 | 備考 |
|---|---|---|
| 言語/UI | Kotlin + Jetpack Compose | MVVM + StateFlow |
| git | **JGit** | Android 唯一の実用解。IO スレッドで実行 |
| Markdown | **Markwon** | core / ext-tables / ext-strikethrough / html / image / syntax-highlight / linkify |
| 絵文字 | shortcode→unicode 変換プラグイン | `:smile:` など |
| Mermaid | アプリ内同梱 `mermaid.min.js` + **WebView** | オフライン描画、テーマ連動 CSS |
| 永続化 | Room | リポジトリ/設定/ブランチキャッシュ |
| 認証保存 | Android Keystore + EncryptedSharedPreferences | token を暗号化 |
| min SDK | **33 (Android 13)** / target 35 | 個人向けのため高め |

---

## 3. アーキテクチャ

```
:app
 ├─ ui/        Compose 画面 + ViewModel (MVVM, StateFlow)
 │     RepoList / AddRepo / RepoBrowser / FileViewer / History / Diff / Settings
 ├─ data/
 │   ├─ db/        Room: Repo, BranchCache, Settings
 │   ├─ crypto/    Keystore + EncryptedSharedPreferences (token)
 │   └─ repo/      Repository 層 (UI ↔ git/db の仲介)
 ├─ git/         JgitClient (clone/fetch/reset/clean/branches/log/diff)
 └─ render/      MarkdownRenderer (Markwon) + MermaidWebView + 相対パス Resolver
```

### データモデル (Room 概略)
- `Repo(id, name, url, host, username, branch, themeMode, lastSyncedAt)`
  - token はここに持たず、Keystore 側に `id` 紐付けで暗号化保存
- `BranchCache(repoId, name, lastCommitAt, lastCommitSha)` — 直近更新順表示用
- `Settings` — アプリ全体デフォルト (テーマ初期値 / フォントサイズ / ハイライト配色)

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
→ BranchCache 更新 (各 refs/remotes/origin/* の committer date)
```

### ブランチ一覧 (直近更新順)
- fetch 済みの `refs/remotes/origin/*` を列挙 → 各 ref を解決し committer date で降順ソート
- 両ホスト共通コード・追加 REST API 不要

### 認証メモ
| | GitHub | Bitbucket Cloud |
|---|---|---|
| token | PAT (classic / fine-grained) | API token (App password は廃止方向) |
| username | 任意 / `x-access-token` | **Atlassian メールアドレス (必須)** |

→ 登録フォームは「ユーザー名 + トークン」2 フィールド。GitHub は username 任意、Bitbucket は必須の注記を出す。

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
Home(リポ一覧) ─┬─ AddRepo
                └─ RepoBrowser(ファイル探索) ─ FileViewer ─┬─ History ─ Diff
                       │  └ ⋮: テーマ/削除/同期            └ Diff
                       └ branchチップ → ブランチ BottomSheet
```

### 設計方針
- **操作系は画面下部に集約**（片手操作・親指で届く）
- ファイル探索は**パンくず式ドリルダウン**（巨大ツリーを縦展開しない）
- 同期は **pull-to-refresh + カードの ⟳** の二経路（手動のみ）

### ① ホーム（リポジトリ一覧）
```
┌────────────────────────────┐
│ git-reader            ⚙   │
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

### 設定（⚙ グローバル）
- デフォルトテーマ / フォントサイズ / コードハイライト配色 / キャッシュ容量・全削除

---

## 7. 技術リスクと検証順序

1. **【最優先】JGit の Android 動作確認 (PoC)**
   - JGit は Java SE 前提の API を含み、バージョンによっては Android で地雷あり
   - 「指定リポを HTTPS+token で clone → fetch → reset --hard」が通るかを最初に検証
2. Mermaid の WebView オフライン描画 + テーマ連動 CSS
3. 相対画像/リンクのパス解決

推奨着手順: **JGit PoC → プロジェクト雛形 → 機能実装**

---

## 8. 未確定 / 今後の検討
- コードのシンタックスハイライト対応言語の範囲
- 全文検索 (将来)
- frontmatter(YAML) の扱い・目次自動生成の詳細
