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
}
