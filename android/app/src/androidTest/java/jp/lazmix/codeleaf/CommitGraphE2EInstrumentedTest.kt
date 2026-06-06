package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.jgit.api.MergeCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * コミットグラフ画面の E2E: ブランチ + マージのあるリポを生成し、グラフ表示でコミットと
 * ref が見えることを検証する。ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class CommitGraphE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
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

    @Test
    fun commitGraph_showsCommitsAndRefs() {
        compose.onNodeWithText("graph-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("a.txt").fetchSemanticsNodes().isNotEmpty()
        }
        // ブラウザのオーバーフロー → コミットグラフ
        compose.onNodeWithContentDescription("メニュー").performClick()
        compose.onNodeWithText("コミットグラフ").performClick()

        // マージ・各ブランチのコミットが並ぶ
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("mergeM").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("mergeM").assertIsDisplayed()
        compose.onNodeWithText("commitB").assertIsDisplayed()
        compose.onNodeWithText("commitC").assertIsDisplayed()
        compose.onNodeWithText("commitA").assertIsDisplayed()
    }
}
