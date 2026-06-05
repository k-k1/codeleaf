package jp.lazmix.codeleaf

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.lazmix.codeleaf.render.CodeHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android ランタイム(ART)上で Prism4j のシンタックスハイライトが機能することを検証する。
 * kapt(prism4j-bundler)が生成する GrammarLocatorDef のロード・文法解決・テーマ適用が
 * 実機/エミュ上で通り、実際に色付き span が付与されることを確認する。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`（ネットワーク不要）。
 */
@RunWith(AndroidJUnit4::class)
class SyntaxHighlightInstrumentedTest {

    private val kotlinCode = """
        fun main() {
            val answer = 42 // the answer
            println("hello")
        }
    """.trimIndent()

    @Test
    fun highlight_kotlin_appliesColorSpans_onDevice() {
        val result = CodeHighlight.highlight("kotlin", kotlinCode, dark = true)
        assertTrue("Spanned が返る(=Prism4j が文法を解決)", result is Spanned)
        val spans = (result as Spanned).getSpans(0, result.length, ForegroundColorSpan::class.java)
        assertTrue("色付き span が 1 つ以上付与される", spans.isNotEmpty())
        assertEquals("テキスト内容は保持される", kotlinCode, result.toString())
    }

    @Test
    fun highlight_lightTheme_alsoAppliesSpans_onDevice() {
        val result = CodeHighlight.highlight("kotlin", kotlinCode, dark = false)
        assertTrue(result is Spanned)
        val spans = (result as Spanned).getSpans(0, result.length, ForegroundColorSpan::class.java)
        assertTrue("ライトテーマでも span が付与される", spans.isNotEmpty())
    }

    @Test
    fun highlight_unknownLanguage_returnsPlainText_onDevice() {
        // 未対応言語(null)はクラッシュせず素のテキストを返す
        val result = CodeHighlight.highlight(null, kotlinCode, dark = true)
        assertEquals(kotlinCode, result.toString())
    }

    @Test
    fun languageForFile_mapsByExtension() {
        assertEquals("kotlin", CodeHighlight.languageForFile("Main.kt"))
        assertEquals("java", CodeHighlight.languageForFile("App.java"))
        assertEquals("python", CodeHighlight.languageForFile("script.py"))
        assertEquals("markup", CodeHighlight.languageForFile("layout.xml"))
        assertEquals("json", CodeHighlight.languageForFile("data.json"))
        assertNull("未対応拡張子は null", CodeHighlight.languageForFile("notes.unknownext"))
        assertNull("拡張子なしは null", CodeHighlight.languageForFile("Dockerfile"))
    }
}
