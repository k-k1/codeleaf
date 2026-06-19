package jp.lazmix.codeleaf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.NavPosition
import jp.lazmix.codeleaf.data.OpenFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * リポを切り替えて開き直したとき、そのリポで最後にいたフォルダが復元されることを検証する。
 * compact(portrait・1ペイン)前提: リポ切替は ≡ ドロワー(= openRepo 経由)で行う。
 * システム戻りで一覧へ抜けると位置はその都度巻き戻る仕様なので、ここでは使わない。
 */
@RunWith(AndroidJUnit4::class)
class RepoSwitchRestoreE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // 既定(復元 ON)に戻す。SharedPreferences はテスト間で残るため明示リセットで決定論化。
        app.container.settingsStore.setRestoreLastPosition(true)
        // alpha: ルートに folder「sub」と top.txt。sub の中に inside.txt。
        val alpha = app.createSrcRepo(
            "alpha-src",
            mapOf("sub/inside.txt" to "inside_marker\n", "top.txt" to "top_marker\n"),
        )
        val beta = app.createSrcRepo("beta-src", mapOf("bonly.txt" to "beta_marker\n"))
        app.addFixtureRepo("alpha-repo", alpha)
        app.addFixtureRepo("beta-repo", beta)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** ≡ ドロワーを開いて別リポを選ぶ（タイトルは現在リポ名なので、相手名はドロワーで一意）。 */
    private fun switchTo(repoName: String) {
        compose.onNodeWithContentDescription("リポ一覧").performClick()
        compose.onNodeWithText(repoName).performClick()
    }

    private fun repoId(name: String): Long = runBlocking {
        app.container.repoRepository.observeRepos().first().first { it.name == name }.id
    }

    /** タイトルのリポ名タップが ≡ と同等にドロワーを開き、別リポへ切替できる。 */
    @Test
    fun tappingTitleRepoNameOpensDrawer() {
        // alpha を開く(compact なので Browser に入り、タイトルにリポ名が出る)。
        compose.onNodeWithText("alpha-repo").performClick()
        waitFor("top.txt")

        // タイトルのリポ名をタップ → ドロワーが開く。相手名(beta)はドロワー内で一意。
        // (タイトル自身も同名だが testTag で掴むので曖昧化しない。)
        compose.onNodeWithTag("browserTitleRepoName").performClick()
        compose.onNodeWithText("beta-repo").performClick()

        // beta の中身が見え、alpha のルートファイルは見えない。
        waitFor("bonly.txt")
        waitGone("top.txt")
    }

    @Test
    fun switchingBackRestoresLastFolder() {
        // alpha を開いてフォルダ sub へ潜る。
        compose.onNodeWithText("alpha-repo").performClick()
        waitFor("sub")
        compose.onNodeWithText("sub").performClick()
        waitFor("inside.txt")
        waitGone("top.txt") // sub の中なのでルートの top.txt は見えない

        // beta へ切替 → alpha へ戻す。
        switchTo("beta-repo")
        waitFor("bonly.txt")
        switchTo("alpha-repo")

        // alpha は最後にいた sub に復元される(inside.txt が見え、ルートの top.txt は見えない)。
        waitFor("inside.txt")
        waitGone("top.txt")
    }

    /** 開いていたファイルが継続保存される（detailStack → NavPositionStore）。 */
    @Test
    fun openingFileIsPersisted() {
        compose.onNodeWithText("alpha-repo").performClick()
        waitFor("top.txt")
        compose.onNodeWithText("top.txt").performClick()
        waitFor("top_marker") // ビューアに本文

        val id = repoId("alpha-repo")
        compose.waitUntil(10_000) {
            app.container.navPositionStore.get(id)?.files?.any { it.path == "top.txt" } == true
        }
    }

    /** 保存済み位置にファイルがあれば、リポを開いた瞬間にそのファイルが復元される（openRepo → pushDetail）。 */
    @Test
    fun reopeningRestoresOpenFile() {
        val id = repoId("alpha-repo")
        // 「ルートで top.txt を開いていた」状態を仕込む（アプリ再起動後の復元と同じ経路）。
        app.container.navPositionStore.save(
            id,
            NavPosition(chain = listOf(""), files = listOf(OpenFile("top.txt", null)), focus = false),
        )
        compose.onNodeWithText("alpha-repo").performClick()
        // 一覧から開いた直後にビューアが復元され、ファイル本文が出る。
        waitFor("top_marker")
    }

    /** 設定 OFF のときは保存位置を無視し、常にトップ・ファイル未オープンで開く。 */
    @Test
    fun restoreDisabled_opensRootWithoutFile() {
        app.container.settingsStore.setRestoreLastPosition(false)
        val id = repoId("alpha-repo")
        // sub/inside.txt を開いていた状態を仕込んでも、OFF なら使われない。
        app.container.navPositionStore.save(
            id,
            NavPosition(listOf("", "sub"), listOf(OpenFile("sub/inside.txt", null)), false),
        )
        compose.onNodeWithText("alpha-repo").performClick()
        // ルートで開く(top.txt と sub が見える)。復元先の本文は出ない。
        waitFor("top.txt")
        waitFor("sub")
        waitGone("inside_marker")
    }
}
