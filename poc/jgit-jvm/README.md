# JGit PoC (pure-JVM)

`DESIGN.md` の技術リスク #1「JGit で clone/fetch/reset --hard が想定通り動くか」を最小コストで検証する PoC。
**JDK のみで動作**（Android SDK 不要）。Android ランタイム(ART) での最終確認は別途必要（下記）。

## 検証内容
1. HTTPS(+token) で clone
2. 全ブランチ fetch (`+refs/heads/*:refs/remotes/origin/*`)
3. ブランチを **直近コミット順** に並べる（committer date 降順）
4. ローカル変更（追跡ファイル改変＋未追跡ファイル作成）を作り、
   `reset --hard origin/<branch>` + `clean -fdx` で**完全破棄**できるか検証

## 実行
```powershell
# 公開リポ (既定: octocat/Hello-World)
.\run.ps1

# private リポ（トークンはコミット/貼り付けしない）
$env:GIT_URL   = "https://github.com/you/private.git"
$env:GIT_USER  = "you"           # GitHub=任意 / Bitbucket=Atlassianメール(必須)
$env:GIT_TOKEN = "<PAT or API token>"
$env:GIT_BRANCH= "main"          # 省略可
.\run.ps1
```

## 結果 (2026-06, 公開リポ)
```
RESULT: PASS ✅
- HTTPS clone / fetch / branch 直近順ソート / reset --hard + clean -fdx すべて成功
- 追跡ファイル復元・未追跡削除・status clean を確認
```

## 依存 (libs/)
- org.eclipse.jgit 7.6.0
- JavaEWAH 1.2.3 / slf4j-api 2.0.17 / slf4j-simple 2.0.17 / commons-codec 1.22.0

## このPoCで分かること / 分からないこと
- ✅ JGit の API・認証・git処理フローが設計通り動く（そのまま `git/JgitClient` の雛形になる）
- ❓ **ART(Android実機/エミュ)での動作は未検証**。JGit 7.x は Java 17 bytecode のため、
  Android 実装では D8/desugaring の確認、または Java 11 bytecode の **JGit 6.x 採用**も検討。
  → DESIGN.md「技術リスク」B/C（エミュ or 手元 Android Studio）で最終確認する。
