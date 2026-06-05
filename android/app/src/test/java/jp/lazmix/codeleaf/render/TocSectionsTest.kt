package jp.lazmix.codeleaf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** MarkdownRenderer.splitIntoSections(目次用の見出し分割)の JVM 単体テスト。 */
class TocSectionsTest {

    @Test
    fun splitsAtHeadingsWithPreamble() {
        val md = "intro text\n# A\nbody a\n## B\nbody b\n"
        val secs = MarkdownRenderer.splitIntoSections(md)
        assertEquals(3, secs.size)
        assertNull(secs[0].heading) // 前文
        assertEquals(Heading(1, "A"), secs[1].heading)
        assertEquals(Heading(2, "B"), secs[2].heading)
    }

    @Test
    fun ignoresHeadingInsideCodeFence() {
        val md = "# Real\n```\n# not a heading\n```\ntail"
        val headings = MarkdownRenderer.splitIntoSections(md).mapNotNull { it.heading }
        assertEquals(listOf(Heading(1, "Real")), headings)
    }

    @Test
    fun stripsTrailingHashes() {
        val md = "## Title ##\n"
        val h = MarkdownRenderer.splitIntoSections(md).first { it.heading != null }.heading
        assertEquals(Heading(2, "Title"), h)
    }

    @Test
    fun noHeadings_isSingleSection() {
        val secs = MarkdownRenderer.splitIntoSections("just text\nmore")
        assertEquals(1, secs.size)
        assertNull(secs[0].heading)
    }

    @Test
    fun sectionMarkdownIncludesHeadingLine() {
        val secs = MarkdownRenderer.splitIntoSections("# A\nbody")
        assertEquals(Heading(1, "A"), secs[0].heading)
        assertEquals(true, secs[0].markdown.startsWith("# A"))
    }
}
