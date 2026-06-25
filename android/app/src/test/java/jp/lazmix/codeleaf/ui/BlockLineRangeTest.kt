package jp.lazmix.codeleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 整形 Markdown ブロック → ソース行範囲(0始まり)の対応付け。 */
class BlockLineRangeTest {

    private val doc = "# Title\n\nfirst para\nstill first\n\n## Sub\n\nsecond para\n"
    // 行: 0:# Title 1:(空) 2:first para 3:still first 4:(空) 5:## Sub 6:(空) 7:second para 8:(空)

    @Test
    fun singleLineBlock() {
        assertEquals(0..0, blockLineRange(doc, "# Title"))
        assertEquals(5..5, blockLineRange(doc, "## Sub\n"))
    }

    @Test
    fun multiLineBlock() {
        assertEquals(2..3, blockLineRange(doc, "first para\nstill first"))
    }

    @Test
    fun trailingNewlinesAreTrimmed() {
        assertEquals(7..7, blockLineRange(doc, "\nsecond para\n\n"))
    }

    @Test
    fun missingOrEmptyReturnsNull() {
        assertNull(blockLineRange(doc, "not present"))
        assertNull(blockLineRange(doc, "\n\n"))
    }

    // --- touchedSourceLine: 長押しした表示行 → ブロック内の単一ソース行 ---

    @Test
    fun touched_picksSpecificLineWithinBlock() {
        // ブロックは 2..3 行だが、長押し表示行 "still first" は 3 行目だけを指す。
        assertEquals(3..3, touchedSourceLine(doc, "first para\nstill first", "still first"))
        assertEquals(2..2, touchedSourceLine(doc, "first para\nstill first", "first para"))
    }

    @Test
    fun touched_ignoresMarkupAndWhitespace() {
        // 表示行は記号が剥がれている。ソース行 "- **重要** な点" に対し表示は "重要 な点"。
        val d = "- 普通の項目\n- **重要** な点\n- 最後\n"
        assertEquals(1..1, touchedSourceLine(d, d, "重要 な点"))
        // 行頭の箇条書き記号やリンク記法も無視して一致。
        val d2 = "see [docs](x.md) here\nother line\n"
        assertEquals(0..0, touchedSourceLine(d2, d2, "see docs here"))
    }

    @Test
    fun touched_wrappedFragmentMatchesItsSourceLine() {
        // 折り返しで表示行が長いソース行の断片でも、その行に含まれれば一致。
        val d = "alpha beta gamma delta epsilon\nnext\n"
        assertEquals(0..0, touchedSourceLine(d, d, "gamma delta"))
    }

    @Test
    fun touched_emptyOrNoMatchReturnsNull() {
        assertNull(touchedSourceLine(doc, "first para\nstill first", ""))
        assertNull(touchedSourceLine(doc, "first para\nstill first", "   "))
        assertNull(touchedSourceLine(doc, "first para\nstill first", "nowhere"))
    }
}
