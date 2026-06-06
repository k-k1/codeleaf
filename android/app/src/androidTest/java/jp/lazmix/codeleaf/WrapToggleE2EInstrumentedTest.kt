package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * コード表示の折り返し ON/OFF(=横スクロール)トグルを検証する。
 * 折り返し OFF 時の LazyColumn + horizontalScroll が実機でクラッシュしないことを確認する。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class WrapToggleE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        val repo = app.container.repoRepository
        runBlocking {
            // 他テスト(WrapDefault)が折り返し既定を OFF に変えたまま終わると、
            // ファイルが「折り返しOFF」で開きこのテストの前提が崩れる。既定 ON に戻す。
            app.container.settingsStore.setWrapByDefault(true)
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "wrap-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "Sample.kt").writeText("val wrapcheck = 123\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("init")
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "wrap-fixture",
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

    /** 下部バーのトグルが実際にビューポートに表示されるまで待つ(遷移アニメ整定を許容)。 */
    private fun waitUntilDisplayed(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        }
    }

    @Test
    fun toggleWrap_switchesAndRendersWithoutCrash() {
        compose.onNodeWithText("wrap-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Sample.kt").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sample.kt").performClick()

        // コード行が表示され、折り返しトグルが ON
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("val wrapcheck = 123").fetchSemanticsNodes().isNotEmpty()
        }
        // 折り返しトグルは画面下部バー。コード行の semantics 出現直後はまだファイル遷移
        // アニメが整定しておらず一瞬「未表示」になり得る(高負荷時)。安定するまで待って検証。
        waitUntilDisplayed("折り返しON")

        // OFF(横スクロール)に切替 → 行は引き続き表示(クラッシュしない)
        compose.onNodeWithText("折り返しON").performClick()
        waitUntilDisplayed("折り返しOFF")
        compose.onNodeWithText("val wrapcheck = 123").assertIsDisplayed()

        // ON に戻せる
        compose.onNodeWithText("折り返しOFF").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("折り返しON").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
