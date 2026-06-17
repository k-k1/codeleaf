package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.data.db.RepoColor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * リポジトリ編集画面: カード色の変更と削除を検証する(ドラッグ並べ替えは手動確認)。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class RepoEditE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        app.cleanRepos()
        srcRepo = app.createSrcRepo("edit-src", mapOf("README.md" to "# x\n"))
        app.addFixtureRepo("edit-fixture", srcRepo)
    }

    @Test
    fun editScreen_changeColor_andDelete() {
        // 一覧 → 編集
        compose.onNodeWithContentDescription("リポジトリを編集").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("リポジトリを編集").fetchSemanticsNodes().isNotEmpty()
        }

        // 色を青に変更 → 永続化を確認
        compose.onNodeWithContentDescription("色:BLUE").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { app.container.repoRepository.observeRepos().first() }
                .firstOrNull()?.colorTag == RepoColor.BLUE
        }

        // 削除 → 確認ダイアログ → 削除
        compose.onNodeWithContentDescription("削除").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("削除").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("削除").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.repoRepository.observeRepos().first().isEmpty() }
        }
    }
}
