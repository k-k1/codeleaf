package jp.lazmix.codeleaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * 新規ハイライト言語(手書き文法の bash/typescript/rust)を実機の Compose 描画パスで開き、
 * クラッシュせずコード行が表示されることを検証する。CodeGrammarLocator → AnnotatedString 変換が
 * 実機で破綻しないことの担保(色そのものは TextView/AnnotatedString のため assert しない)。
 * ネットワーク非依存(ローカル git リポを file パスで clone)。
 */
@RunWith(AndroidJUnit4::class)
class HighlightLanguagesE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CodeLeafApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        // 各ファイルの検証行はインデントなし(=行テキストがそのまま semantics になる)。
        val src = app.createSrcRepo(
            "hl-src",
            mapOf(
                "sample.rs" to "fn main() {}\nconst CODELEAF_RUST: u32 = 42;\n",
                "sample.ts" to "interface CodeLeaf {}\nconst CODELEAF_TS: number = 7;\n",
                "build.sh" to "#!/usr/bin/env bash\necho CODELEAF_BASH_MARKER\n",
            ),
        )
        app.addFixtureRepo("hl-fixture", src)
        compose.onNodeWithText("hl-fixture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("sample.rs").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openAndAssertLine(fileName: String, codeLine: String) {
        compose.onNodeWithText(fileName).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(codeLine).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(codeLine).assertIsDisplayed()
    }

    @Test
    fun rustFile_rendersHighlightedLine() {
        openAndAssertLine("sample.rs", "const CODELEAF_RUST: u32 = 42;")
    }

    @Test
    fun typescriptFile_rendersHighlightedLine() {
        openAndAssertLine("sample.ts", "const CODELEAF_TS: number = 7;")
    }

    @Test
    fun bashFile_rendersHighlightedLine() {
        openAndAssertLine("build.sh", "echo CODELEAF_BASH_MARKER")
    }
}
