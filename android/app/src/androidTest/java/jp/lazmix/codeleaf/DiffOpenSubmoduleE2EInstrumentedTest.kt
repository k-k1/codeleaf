package jp.lazmix.codeleaf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * diff の submodule 変更で「ファイルを開く」をタップしても EISDIR にならず、
 * そのフォルダをブラウザで開きファイル未選択になることを検証する（portrait/compact）。
 */
@RunWith(AndroidJUnit4::class)
class DiffOpenSubmoduleE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val sub = app.createSrcRepo("sm-sub", mapOf("LIB.md" to "# lib\n"))
        val parent = app.gitRepo("sm-parent") { git, dir ->
            File(dir, "README.md").writeText("# parent\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("init").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
            git.submoduleAdd().setURI(sub.toURI().toString()).setPath("lib").call().close()
            git.commit().setMessage("add submodule").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
        }
        app.addFixtureRepo("sm-repo", parent)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun openSubmoduleFromDiff_opensFolderNoFile_noEisdir() {
        compose.onNodeWithContentDescription("コミットグラフ").performClick()
        waitFor("add submodule")
        compose.onNodeWithText("add submodule").performClick()
        // 差分の2ファイル(.gitmodules, lib)の開くアイコンが揃うまで。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("ファイルを開く").fetchSemanticsNodes().size >= 2
        }
        // パス昇順で [0]=.gitmodules [1]=lib(submodule)。submodule を開く。
        compose.onAllNodesWithContentDescription("ファイルを開く")[1].performClick()
        // EISDIR にならず、submodule フォルダをブラウザで開く(中身 LIB.md が一覧に出る)。
        waitFor("LIB.md")
        // ビューアは開かない=「読み込み失敗」エラーは出ない。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("読み込み失敗", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }
}
