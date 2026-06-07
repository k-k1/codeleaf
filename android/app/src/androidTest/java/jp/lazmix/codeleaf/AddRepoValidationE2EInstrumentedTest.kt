package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * リポ追加の URL バリデーション E2E。不正な形式の URL を入れるとフィールド下にインラインエラーが出て
 * 「保存・clone」ボタンが無効化されることを検証する(ネットワーク非依存)。
 */
@RunWith(AndroidJUnit4::class)
class AddRepoValidationE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
    }

    @Test
    fun invalidUrl_showsInlineErrorAndDisablesSave() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("リポジトリを追加").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("リポジトリを追加").onFirst().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("URL (https://...)").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("URL (https://...)").performTextInput("justtext")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("形式が正しくありません", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("保存・clone").assertIsNotEnabled()
    }
}
