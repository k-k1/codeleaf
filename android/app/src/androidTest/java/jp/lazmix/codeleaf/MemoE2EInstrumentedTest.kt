package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * メモ機能の E2E: コード行を長押し → 既存メモ帳へ保存 → メモ画面で引用が見えることを検証する。
 * 入力タイプを避けるため、メモ帳は @Before で1つ用意し追加シートの既定選択に乗せる。
 */
@RunWith(AndroidJUnit4::class)
class MemoE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.createSrcRepo(
            "memo-src",
            mapOf("code.kt" to "package x\nval memo_target = 7\nfun main() {}\n"),
        )
        app.addFixtureRepo("memo-fixture", src)
        // 追加シートの既定選択になる既存メモ帳を用意。
        runBlocking {
            val repo = app.container.repoRepository.observeRepos().first().first { it.name == "memo-fixture" }
            app.container.memoRepository.createMemo(repo.id, "レビュー")
        }

        compose.onNodeWithText("memo-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("code.kt").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("code.kt").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("val memo_target = 7").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun longPressLine_savesEntry_visibleInMemo() {
        // 行を長押し → 追加シート。
        compose.onNodeWithText("val memo_target = 7").performTouchInput { longClick() }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isNotEmpty()
        }
        // 既定の既存メモ帳「レビュー」へ保存(コメントなし・引用は自動)。
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("メモを追加").fetchSemanticsNodes().isEmpty()
        }

        // ⋮ → メモ で一覧へ。レビュー帳が 1 件になっている。
        compose.onNodeWithContentDescription("メニュー").performClick()
        compose.onNodeWithText("メモ").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("レビュー").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1 件").assertIsDisplayed()

        // 帳を開く → エントリの引用が見える。
        compose.onNodeWithText("レビュー").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("val memo_target = 7").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("code.kt L2").assertIsDisplayed()
    }
}
