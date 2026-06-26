package jp.lazmix.codeleaf

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import jp.lazmix.codeleaf.data.NewRepo
import jp.lazmix.codeleaf.data.db.GitHost
import jp.lazmix.codeleaf.data.db.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import java.io.File

/**
 * 計装 E2E 共通のリポジトリ・フィクスチャ。各テストの @Before で重複していた
 * 「全リポ削除 → ローカル git リポ作成 → file:// で clone」を集約する(ネットワーク非依存)。
 */

/**
 * UI 言語(per-app 言語)をシステム既定へ戻して決定論化する。日本語前提の文字列/testTag を assert する
 * E2E は、前回実行や手動操作で英語等に上書きされた状態が永続化されていると全滅するため、@Before で呼ぶ。
 * 既に空(=システム)なら no-op で Activity 再生成も起きない。setApplicationLocales は main で呼ぶ必要がある。
 */
fun resetAppLocaleToSystem() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }
}

/** 既存リポを一掃して決定論化する(前回失敗実行の残骸対策)。 */
fun CodeLeafApplication.cleanRepos() = runBlocking {
    val repo = container.repoRepository
    repo.observeRepos().first().forEach { repo.delete(it) }
}

/**
 * cacheDir 配下に git リポを作る。[build] には `(git, dir)` を渡し、commit まで呼び出し側が行う
 * (複数ブランチ・マージ等の自由な構成用)。多くのテストは [createSrcRepo] の Map 版で足りる。
 */
fun CodeLeafApplication.gitRepo(name: String, build: (git: Git, dir: File) -> Unit): File {
    val dir = File(cacheDir, name).apply { deleteRecursively(); mkdirs() }
    Git.init().setInitialBranch("main").setDirectory(dir).call().use { build(it, dir) }
    return dir
}

/** `path → 内容` のファイルを書いて単一コミットしたローカル git リポを作る。サブディレクトリも作成。 */
fun CodeLeafApplication.createSrcRepo(name: String, files: Map<String, String>): File =
    gitRepo(name) { git, dir ->
        files.forEach { (path, content) ->
            File(dir, path).apply { parentFile?.mkdirs() }.writeText(content)
        }
        git.commitAll("init")
    }

/** ワークツリーの全変更を add して 1 コミットする(著者/コミッタは固定のテスト値)。 */
fun Git.commitAll(message: String) {
    add().addFilepattern(".").call()
    commit().setMessage(message)
        .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
}

/** ローカル [src] を file パスで TOKEN 方式リポとして登録 & clone する。 */
fun CodeLeafApplication.addFixtureRepo(name: String, src: File, branch: String? = null) = runBlocking {
    container.repoRepository.addAndClone(
        NewRepo(
            name = name,
            url = src.absolutePath,
            host = GitHost.GITHUB,
            username = "",
            token = "x",
            branch = branch,
            themeMode = ThemeMode.SYSTEM,
        ),
    )
}
