package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 一覧 → ブラウズ → Markdown ビューア到達 を実 UI で通す E2E。
 * ネットワーク・登録フォーム非依存にするため、端末上に作ったローカル git リポジトリを
 * [addFixtureRepo](file:// clone)で直接登録する(登録フォームの URL 検証は別問題なので踏まない)。
 *
 * Markdown 本文は AndroidView(TextView) 内で Compose セマンティクスから見えないため、
 * ビューア到達はタイトルと「整形/Raw」トグルの存在で確認する(相対リンク遷移ロジックは
 * RepoLinkResolverTest で別途担保)。
 */
@RunWith(AndroidJUnit4::class)
class AppE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        // 既存リポを一掃して決定論化(前回の失敗実行の残骸対策)。clone 元は README.md + docs/guide.md。
        app.cleanRepos()
        srcRepo = app.createSrcRepo(
            "e2e-src",
            mapOf(
                "README.md" to "# Title\n\n[guide](docs/guide.md)\n",
                "docs/guide.md" to "# Guide\n",
            ),
        )
        app.addFixtureRepo("e2e-src", srcRepo)
    }

    @After
    fun tearDown() {
        app.cleanRepos()
        if (::srcRepo.isInitialized) srcRepo.deleteRecursively()
    }

    @Test
    fun browse_openMarkdown() {
        // 登録済みリポが List に並ぶ
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("e2e-src").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("e2e-src").assertIsDisplayed()

        // カードを開く → ブラウザに README.md が並ぶ
        compose.onNodeWithText("e2e-src").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }

        // README.md を開く → Markdown ビューア到達を ⋮ メニュー(履歴/Raw で表示)で確認
        compose.onNodeWithText("README.md").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("メニュー").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("履歴").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("履歴").assertIsDisplayed()
        compose.onNodeWithText("Raw で表示").assertIsDisplayed()
    }
}
