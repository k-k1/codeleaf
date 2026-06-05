package jp.lazmix.codeleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** activeTocIndex(目次の現在位置判定)の JVM 単体テスト。 */
class ActiveTocIndexTest {

    private val indices = listOf(0, 1, 2)
    private val tops = mapOf(0 to 0, 1 to 100, 2 to 300)

    @Test
    fun atTop_firstHeadingActive() {
        assertEquals(0, activeTocIndex(indices, tops, 0))
        assertEquals(0, activeTocIndex(indices, tops, 50))
    }

    @Test
    fun atOrPastSecondHeading_secondActive() {
        assertEquals(1, activeTocIndex(indices, tops, 100))
        assertEquals(1, activeTocIndex(indices, tops, 250))
    }

    @Test
    fun atLastHeading_lastActive() {
        assertEquals(2, activeTocIndex(indices, tops, 300))
        assertEquals(2, activeTocIndex(indices, tops, 9999))
    }

    @Test
    fun missingPositionsAreSkipped() {
        // section 1 の Y が未記録なら 0 のまま(次に top<=scrollY を満たすまで更新しない)
        val partial = mapOf(0 to 0, 2 to 300)
        assertEquals(0, activeTocIndex(indices, partial, 150))
        assertEquals(2, activeTocIndex(indices, partial, 300))
    }

    @Test
    fun emptyReturnsMinusOne() {
        assertEquals(-1, activeTocIndex(emptyList(), emptyMap(), 0))
    }
}
