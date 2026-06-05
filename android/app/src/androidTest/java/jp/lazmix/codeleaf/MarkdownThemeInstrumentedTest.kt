package jp.lazmix.codeleaf

import android.text.Spanned
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.lazmix.codeleaf.render.MarkdownColors
import jp.lazmix.codeleaf.render.MarkdownRenderer
import io.noties.markwon.core.spans.LinkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Markwon のリンク配色が MarkdownColors(テーマ連動値)で適用されることを ART 上で検証する。
 * レンダリング結果の LinkSpan を取り出し、updateDrawState で塗られる色が指定値と一致することを確認。
 *
 * 実行: `./gradlew pixel6Api35DebugAndroidTest`（ネットワーク不要）。
 */
@RunWith(AndroidJUnit4::class)
class MarkdownThemeInstrumentedTest {

    @Test
    fun linkColor_followsProvidedTheme_onDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val linkColor = 0xFF3366CC.toInt()
        val colors = MarkdownColors(
            link = linkColor,
            inlineCodeBg = 0xFF222222.toInt(),
            inlineCodeText = 0xFFDDDDDD.toInt(),
            blockQuoteBar = 0xFF888888.toInt(),
            divider = 0xFF444444.toInt(),
        )
        val markwon = MarkdownRenderer.create(ctx, dark = true, linkResolver = null, colors = colors)

        val spanned = markwon.toMarkdown("[link](https://example.com)") as Spanned
        val links = spanned.getSpans(0, spanned.length, LinkSpan::class.java)
        assertTrue("LinkSpan が生成される", links.isNotEmpty())

        val paint = TextPaint()
        links.first().updateDrawState(paint)
        assertEquals("リンク色がテーマ指定値で塗られる", linkColor, paint.color)
    }
}
