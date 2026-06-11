package jp.lazmix.codeleaf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.jgit.api.Git
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * diff ヘッダの「ファイルを開く」アイコンを検証する（portrait/compact）。
 * c1: a.txt=alpha_one, b.txt=beta_gone / c2: a.txt=alpha_two に変更・b.txt 削除。
 * 一覧 → コミットグラフ → c2 → diff から:
 *  - a.txt（現存）→ 現物 Viewer に現内容 alpha_two
 *  - b.txt（c2 で削除）→ 履歴 Viewer に親の内容 beta_gone ＋ メタバーに「…時点」
 */
@RunWith(AndroidJUnit4::class)
class DiffOpenFileE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.gitRepo("diffopen-src") { git, dir ->
            File(dir, "a.txt").writeText("alpha_one\n")
            File(dir, "b.txt").writeText("beta_gone\n")
            git.commitAll("c1")
            File(dir, "a.txt").writeText("alpha_two\n")
            git.add().addFilepattern("a.txt").call()
            git.rm().addFilepattern("b.txt").call()
            git.commit().setMessage("c2").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
        }
        app.addFixtureRepo("diffopen-repo", src)
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 一覧の repo カードのグラフアイコン → コミット c2 → diff を出す。 */
    private fun openC2Diff() {
        compose.onNodeWithContentDescription("コミットグラフ").performClick()
        waitFor("c2")
        compose.onNodeWithText("c2").performClick()
        // diff ロード完了まで（ファイルを開くアイコンが2つ＝a.txt/b.txt）。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("ファイルを開く").fetchSemanticsNodes().size >= 2
        }
    }

    @Test
    fun openIcon_existingFile_opensCurrentViewer() {
        openC2Diff()
        // a.txt = 先頭（パス昇順）。現存するので現物 Viewer = HEAD の内容 alpha_two。
        compose.onAllNodesWithContentDescription("ファイルを開く")[0].performClick()
        waitFor("alpha_two")
        // diff を抜けてビューアにいる（開くアイコンは消える）。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("ファイルを開く").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun openIcon_deletedFile_opensHistoricalViewer() {
        openC2Diff()
        // b.txt = 2番目。c2 で削除 → 親 c1 の内容 beta_gone を履歴表示。
        compose.onAllNodesWithContentDescription("ファイルを開く")[1].performClick()
        waitFor("beta_gone")
        // メタバーに「…時点」（過去コミット表示の明示）。
        waitFor("時点", substring = true)
    }
}
