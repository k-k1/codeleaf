package com.k1.gitreader.ui

import com.k1.gitreader.render.Heading
import com.k1.gitreader.render.MdSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** stickyHeadingStack(スティッキー見出しの祖先パス算出)の JVM 単体テスト。 */
class HeadingStackTest {

    // 0:(前文) 1:#A 2:##B 3:###C 4:##D 5:#E
    private val sections = listOf(
        MdSection(null, "intro"),
        MdSection(Heading(1, "A"), "# A"),
        MdSection(Heading(2, "B"), "## B"),
        MdSection(Heading(3, "C"), "### C"),
        MdSection(Heading(2, "D"), "## D"),
        MdSection(Heading(1, "E"), "# E"),
    )
    private val tops = mapOf(0 to 0, 1 to 100, 2 to 200, 3 to 300, 4 to 400, 5 to 500)

    // rowHeight=0 にするとしきい値=scrollY(=見出しが画面上端に達したら昇格)で祖先パス算出を素に検証できる。
    private fun stack(scrollY: Int, rowHeight: Int = 0) =
        stickyHeadingStack(sections, tops, scrollY) { rowHeight }.map { it.second }

    @Test
    fun atTop_noStack() {
        assertEquals(emptyList<Heading>(), stack(0))
    }

    @Test
    fun insideC_showsA_B_C() {
        assertEquals(listOf(Heading(1, "A"), Heading(2, "B"), Heading(3, "C")), stack(350))
    }

    @Test
    fun insideD_popsCandB_showsA_D() {
        assertEquals(listOf(Heading(1, "A"), Heading(2, "D")), stack(450))
    }

    @Test
    fun insideE_popsAll_showsE() {
        assertEquals(listOf(Heading(1, "E")), stack(550))
    }

    @Test
    fun headingExactlyAtTop_notYetStuck() {
        // top==scrollY+barHeight(=scrollY) は未昇格(break)
        assertEquals(listOf(Heading(1, "A")), stack(200))
    }

    @Test
    fun barBottomThreshold_promotesEarlierWithRowHeight() {
        // rowHeight=50: scrollY=160 で A 昇格(bar50)→ B は top200<160+50=210 で昇格。rowHeight=0 なら A のみ。
        assertEquals(listOf(Heading(1, "A")), stack(160, rowHeight = 0))
        assertEquals(listOf(Heading(1, "A"), Heading(2, "B")), stack(160, rowHeight = 50))
    }

    // --- タップ飛び先(top+1)が、間に深い兄弟があっても正しい祖先パスになる(rowHeight=0で素に検証) ---
    private fun stackAfterJumpTo(idx: Int) =
        stickyHeadingStack(sections, tops, tops[idx]!! + 1) { 0 }.map { it.second }

    @Test
    fun jumpToHeading_landsItAsLastPinned_withCorrectAncestors() {
        assertEquals(listOf(Heading(1, "A"), Heading(2, "D")), stackAfterJumpTo(4))
        assertEquals(listOf(Heading(1, "A"), Heading(2, "B"), Heading(3, "C")), stackAfterJumpTo(3))
        assertEquals(listOf(Heading(1, "A")), stackAfterJumpTo(1))
    }
}
