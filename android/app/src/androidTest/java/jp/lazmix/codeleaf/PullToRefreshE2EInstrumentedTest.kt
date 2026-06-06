package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.NewRepo
import jp.lazmix.codeleaf.data.db.GitHost
import jp.lazmix.codeleaf.data.db.ThemeMode
import org.eclipse.jgit.api.Git
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * ファイルブラウザの pull-to-refresh で閲覧中リポが同期されることを実 UI で検証する。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 *
 * 検証は「同期完了」スナックバー(瞬間表示・SnackbarHost 依存で取りこぼしやすい)ではなく、
 * **同期の持続的な結果** — clone 後にリモートへ足したコミットのファイルが pull で作業ツリーに現れ、
 * 一覧に表示されること — をアサートする。これによりフルスイートの負荷下でも決定的になる。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class PullToRefreshE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "ptr-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "README.md").writeText("# Title\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "ptr-fixture",
                    url = srcRepo.absolutePath,
                    host = GitHost.GITHUB,
                    username = "",
                    token = "x",
                    branch = null,
                    themeMode = ThemeMode.SYSTEM,
                ),
            )
        }
    }

    @Test
    fun pullToRefresh_syncsCurrentRepo() {
        // リポを開く → ブラウザに README.md(clone 直後の状態)
        compose.onNodeWithText("ptr-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }
        // clone 後にリモート(origin)へ新規コミットを足す。まだ pull していないので一覧には出ない。
        Git.open(srcRepo).use { git ->
            File(srcRepo, "PULLED.md").writeText("# Pulled\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("add pulled")
                .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
        }

        // ファイル一覧を下に引く → 同期(fetch + reset --hard origin/main) → 新ファイルが作業ツリーに現れる。
        // 瞬間表示のスナックバーではなく、同期で取り込まれた PULLED.md の出現で同期成立を判定する。
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("PULLED.md").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PULLED.md").assertIsDisplayed()
    }
}
