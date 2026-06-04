package com.k1.gitreader.ui

import com.k1.gitreader.render.Heading
import com.k1.gitreader.render.MdSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** computeHeadingStack(スティッキー見出しの祖先パス算出)の JVM 単体テスト。 */
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

    private fun stack(scrollY: Int) =
        computeHeadingStack(sections, tops, scrollY).map { it.second }

    @Test
    fun atTop_noStack() {
        assertEquals(emptyList<Heading>(), stack(0))
    }

    @Test
    fun insideC_showsA_B_C() {
        // C(top=300) を過ぎた位置 → A>B>C
        assertEquals(listOf(Heading(1, "A"), Heading(2, "B"), Heading(3, "C")), stack(350))
    }

    @Test
    fun insideD_popsCandB_showsA_D() {
        // D(##,top=400) を過ぎる → B,C は pop され A>D
        assertEquals(listOf(Heading(1, "A"), Heading(2, "D")), stack(450))
    }

    @Test
    fun insideE_popsAll_showsE() {
        // E(#,top=500) を過ぎる → A も pop され E のみ
        assertEquals(listOf(Heading(1, "E")), stack(550))
    }

    @Test
    fun headingExactlyAtTop_notYetStuck() {
        // top==scrollY は「まだインライン表示」扱いでスタックに入らない
        assertEquals(listOf(Heading(1, "A")), stack(200)) // B(top=200)は未, A(100)のみ
    }

    // --- スティッキー見出しタップの飛び先(top+1)が、間に深い兄弟があっても正しい祖先パスになる ---

    private fun stackAfterJumpTo(idx: Int) =
        computeHeadingStack(sections, tops, (tops[idx]!!) + 1).map { it.second }

    @Test
    fun jumpToHeading_landsItAsLastPinned_withCorrectAncestors() {
        // D(##, idx=4) をタップ → top+1 へ飛ぶ。間の C(###) は pop され A>D で D が最下段
        assertEquals(listOf(Heading(1, "A"), Heading(2, "D")), stackAfterJumpTo(4))
        // C(### idx=3) をタップ → A>B>C
        assertEquals(
            listOf(Heading(1, "A"), Heading(2, "B"), Heading(3, "C")),
            stackAfterJumpTo(3),
        )
        // A(# idx=1) をタップ → A のみ(祖先なし)
        assertEquals(listOf(Heading(1, "A")), stackAfterJumpTo(1))
    }
}
