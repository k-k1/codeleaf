package jp.lazmix.codeleaf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** MarkdownRenderer.extractFrontmatter の JVM 単体テスト。 */
class FrontmatterTest {

    @Test
    fun parsesKeyValueAndStripsFromBody() {
        val md = "---\ntitle: My Doc\ndate: 2026-01-01\n---\n# Heading\n\nbody\n"
        val (fm, body) = MarkdownRenderer.extractFrontmatter(md)
        assertEquals(
            listOf(
                FrontmatterEntry("title", "My Doc"),
                FrontmatterEntry("date", "2026-01-01"),
            ),
            fm,
        )
        assertEquals("# Heading\n\nbody\n", body)
    }

    @Test
    fun mergesBlockListIntoValue() {
        val md = "---\ntags:\n  - a\n  - b\n---\nbody"
        val (fm, body) = MarkdownRenderer.extractFrontmatter(md)
        assertEquals(listOf(FrontmatterEntry("tags", "a, b")), fm)
        assertEquals("body", body)
    }

    @Test
    fun unquotesScalarValues() {
        val md = "---\ntitle: \"Quoted Title\"\nname: 'single'\n---\nx"
        val (fm, _) = MarkdownRenderer.extractFrontmatter(md)
        assertEquals(
            listOf(
                FrontmatterEntry("title", "Quoted Title"),
                FrontmatterEntry("name", "single"),
            ),
            fm,
        )
    }

    @Test
    fun acceptsDotDotDotClosing() {
        val md = "---\nkey: v\n...\nbody"
        val (fm, body) = MarkdownRenderer.extractFrontmatter(md)
        assertEquals(listOf(FrontmatterEntry("key", "v")), fm)
        assertEquals("body", body)
    }

    @Test
    fun noFrontmatterWhenNotAtStart() {
        val md = "# Heading\n\n---\nkey: v\n---\n"
        val (fm, body) = MarkdownRenderer.extractFrontmatter(md)
        assertNull(fm)
        assertEquals(md, body)
    }

    @Test
    fun noFrontmatterWhenUnterminated() {
        val md = "---\nkey: v\nno closing fence\n# body"
        val (fm, body) = MarkdownRenderer.extractFrontmatter(md)
        assertNull(fm)
        assertEquals(md, body)
    }

    @Test
    fun plainHorizontalRuleIsNotFrontmatter() {
        // 先頭が --- でも閉じが無ければ通常の Markdown（水平線）として扱う
        val md = "regular text\n---\nmore"
        val (fm, _) = MarkdownRenderer.extractFrontmatter(md)
        assertNull(fm)
    }
}
