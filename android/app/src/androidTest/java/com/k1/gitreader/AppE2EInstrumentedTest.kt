package com.k1.gitreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()
    private lateinit var srcRepo: File

    @Before
    fun setUp() {
        // 既存リポジトリを一掃して決定論的にする(前回の失敗実行の残骸対策)。
        val repo = app.container.repoRepository
        runBlocking { repo.observeRepos().first().forEach { repo.delete(it) } }

        // clone 元のローカル git リポジトリを作る(README.md + docs/guide.md)。
        srcRepo = File(app.cacheDir, "e2e-src").apply { deleteRecursively(); mkdirs() }
        Git.init().setInitialBranch("main").setDirectory(srcRepo).call().use { git ->
            File(srcRepo, "README.md").writeText("# Title\n\n[guide](docs/guide.md)\n")
            File(srcRepo, "docs").mkdirs()
            File(srcRepo, "docs/guide.md").writeText("# Guide\n")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("init")
                .setAuthor("t", "t@example.com")
                .setCommitter("t", "t@example.com")
                .call()
        }
    }

    @After
    fun tearDown() {
        val repo = app.container.repoRepository
        runBlocking { repo.observeRepos().first().forEach { repo.delete(it) } }
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

        // フォーム入力(URL=ローカルパス, token=ダミー: file clone では未使用だが UI 必須)。
        // 表示名は URL から自動補完される(末尾セグメント = "e2e-src")。
        setField("https", srcRepo.absolutePath) // URL 欄(ラベル "URL (https://...)")を一意に特定
        setField("トークン", "x")
        compose.waitForIdle()

        compose.onNodeWithText("保存・clone").performScrollTo().performClick()

        // clone 完了 → List に戻る(タイトル "git-reader" が再表示)
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("git-reader").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("e2e-src").assertIsDisplayed()

        // カードを開く → ブラウザに README.md が並ぶ
        compose.onNodeWithText("e2e-src").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("README.md").fetchSemanticsNodes().isNotEmpty()
        }

        // README.md を開く → Markdown ビューア(下部に整形/Raw トグル)
        compose.onNodeWithText("README.md").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("整形").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("整形").assertIsDisplayed()
        compose.onNodeWithText("Raw").assertIsDisplayed()
    }
}
