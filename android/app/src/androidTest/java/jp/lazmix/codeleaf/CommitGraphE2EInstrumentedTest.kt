package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.jgit.api.MergeCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * コミットグラフ画面の E2E: ブランチ + マージのあるリポを生成し、グラフ表示・コミット長押しでの
 * ブランチ切替を検証する。ネットワーク非依存(ローカル git リポを file パスで clone)。
 *
 * グラフへは**一覧のリポカードのグラフアイコンから直接**入る。ブラウザ経由だと 1/2 ペインの
 * ModalNavigationDrawer(中身=RepoListScreen)が画面外に常時 compose され、同じ contentDescription
 * "コミットグラフ" のアイコンが二重に見えて曖昧になるため(一覧画面にドロワーは無く一意)。
 */
@RunWith(AndroidJUnit4::class)
class CommitGraphE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        app.cleanRepos()
        srcRepo = app.gitRepo("graph-src") { git, dir ->
            File(dir, "a.txt").writeText("a\n"); git.commitAll("commitA")
            git.branchCreate().setName("feature").call()
            git.checkout().setName("feature").call()
            File(dir, "b.txt").writeText("b\n"); git.commitAll("commitB")
            git.checkout().setName("main").call()
            File(dir, "c.txt").writeText("c\n"); git.commitAll("commitC")
            git.merge()
                .include(git.repository.resolve("feature"))
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .setMessage("mergeM")
                .call()
        }
        app.addFixtureRepo("graph-fixture", srcRepo)
    }

    /** 一覧のリポカード上のグラフアイコンからコミットグラフを開き、コミットが出るまで待つ。 */
    private fun openCommitGraph() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("graph-fixture").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("コミットグラフ").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("commitA").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun commitGraph_showsCommitsAndRefs() {
        openCommitGraph()
        compose.onNodeWithText("mergeM").assertIsDisplayed()
        compose.onNodeWithText("commitB").assertIsDisplayed()
        compose.onNodeWithText("commitC").assertIsDisplayed()
        compose.onNodeWithText("commitA").assertIsDisplayed()
    }

    @Test
    fun longPress_onBranchCommit_switchesBranch() {
        openCommitGraph()

        // feature が指す commitB を長押し → 切替メニュー。
        compose.onNodeWithText("commitB").performTouchInput { longClick() }
        compose.onNodeWithText("ブランチを切り替え").assertIsDisplayed()
        // メニュー項目の「feature」(チップと同名なので最後のノード=メニュー項目)をタップ。
        compose.onAllNodesWithText("feature").onLast().performClick()

        // メニューが閉じ、切替(同期)完了でグラフが再読込されるまで待つ。完了の安定なサインは
        // 上部バーのブランチ表記が "feature" になること(= "feature" がチップ＋サブタイトルで2箇所に)。
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("ブランチを切り替え").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("feature").fetchSemanticsNodes().size >= 2
        }

        // 再度 commitB を長押しすると、feature が現在ブランチとして表示される。
        compose.onNodeWithText("commitB").performTouchInput { longClick() }
        compose.onNodeWithText("feature（現在のブランチ）").assertIsDisplayed()
    }
}
