package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 登録 → clone → 一覧 → ブラウズ → Markdown ビューア到達 を実 UI で通す E2E。
 * ネットワーク非依存にするため、端末上に作ったローカル git リポジトリを file パスで clone する。
 *
 * Markdown 本文は AndroidView(TextView) 内で Compose セマンティクスから見えないため、
 * ビューア到達はタイトルと「整形/Raw」トグルの存在で確認する(相対リンク遷移ロジックは
 * RepoLinkResolverTest で別途担保)。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`（ネットワーク不要）。
 */
@RunWith(AndroidJUnit4::class)
class AppE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()
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
    }

    @After
    fun tearDown() {
        app.cleanRepos()
        if (::srcRepo.isInitialized) srcRepo.deleteRecursively()
    }

    private fun setField(labelSubstring: String, text: String) {
        compose.onNode(hasSetTextAction() and hasText(labelSubstring, substring = true))
            .performScrollTo()
            .performTextInput(text)
    }

    @Test
    fun register_clone_browse_openMarkdown() {
        // List 画面 → 追加 FAB
        compose.onNodeWithContentDescription("リポジトリを追加").performClick()

        // OAuth 設定済みビルド(local.properties に client_id あり)では GitHub 既定が OAuth になり
        // URL 手入力欄が隠れる。アコーディオンの「トークンを入力」を選んで URL 欄を出す
        // (OAuth 未設定ビルドではアコーディオン自体が無いので、その時は素通り)。
        if (compose.onAllNodesWithText("トークンを入力").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("トークンを入力").performClick()
        }

        // フォーム入力(URL=ローカルパス, token=ダミー: file clone では未使用だが UI 必須)。
        // 表示名は URL から自動補完される(末尾セグメント = "e2e-src")。
        setField("https", srcRepo.absolutePath) // URL 欄(ラベル "URL (https://...)")を一意に特定
        setField("トークン", "x")
        compose.waitForIdle()

        compose.onNodeWithText("保存・clone").performScrollTo().performClick()

        // 送信と同時に List へ戻り、e2e-src は最初「clone 中」パネルで現れる。
        // clone 完了 = READY カードになると「同期」ボタンが出る。これをもって完了を待つ。
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithContentDescription("同期").fetchSemanticsNodes().isNotEmpty()
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
