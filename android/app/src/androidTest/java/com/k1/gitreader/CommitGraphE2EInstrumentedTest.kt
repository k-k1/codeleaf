package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        val repo = app.container.repoRepository
        runBlocking {
            repo.observeRepos().first().forEach { repo.delete(it) }
            srcRepo = File(app.cacheDir, "graph-src").apply { deleteRecursively(); mkdirs() }
            Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
                File(srcRepo, "a.txt").writeText("a\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("commitA").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
                git.branchCreate().setName("feature").call()
                git.checkout().setName("feature").call()
                File(srcRepo, "b.txt").writeText("b\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("commitB").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
                git.checkout().setName("main").call()
                File(srcRepo, "c.txt").writeText("c\n")
                git.add().addFilepattern(".").call()
                git.commit().setMessage("commitC").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
                git.merge()
                    .include(git.repository.resolve("feature"))
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .setMessage("mergeM")
                    .call()
            }
            repo.addAndClone(
                NewRepo(
                    name = "graph-fixture",
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
