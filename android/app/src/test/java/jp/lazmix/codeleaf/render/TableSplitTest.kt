package jp.lazmix.codeleaf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** splitBlocks(extractTables=true) の GFM テーブル抽出の JVM 単体テスト。 */
class TableSplitTest {

    @Test
    fun extractsTableBetweenText() {
        val md = "前文\n\n| h1 | h2 |\n| --- | --- |\n| a | b |\n| c | d |\n\n後文\n"
        val blocks = MarkdownRenderer.splitBlocks(md, extractTables = true)
        val table = blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("h1", "h2"), table.header)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), table.rows)
        // 前後はテキストとして残る
        assertTrue(blocks.any { it is MdBlock.Text && it.markdown.contains("前文") })
        assertTrue(blocks.any { it is MdBlock.Text && it.markdown.contains("後文") })
    }

    @Test
    fun noOuterPipes() {
        val md = "h1 | h2\n--- | ---\na | b\n"
        val table = MarkdownRenderer.splitBlocks(md, extractTables = true)
            .filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("h1", "h2"), table.header)
        assertEquals(listOf(listOf("a", "b")), table.rows)
    }

    @Test
    fun inlineModeKeepsTableAsText() {
        val md = "| h1 | h2 |\n| --- | --- |\n| a | b |\n"
        val blocks = MarkdownRenderer.splitBlocks(md, extractTables = false)
        assertTrue(blocks.all { it is MdBlock.Text })
        assertTrue(blocks.none { it is MdBlock.Table })
    }

    @Test
    fun pipeInCodeFenceIsNotTable() {
        val md = "```\n| not | a | table |\n| --- | --- | --- |\n```\n"
        val blocks = MarkdownRenderer.splitBlocks(md, extractTables = true)
        assertTrue("コードフェンス内の|表はテーブル化しない", blocks.none { it is MdBlock.Table })
    }

    @Test
    fun plainPipeLineWithoutDelimiterIsNotTable() {
        val md = "a | b と書いただけの行\nもう一行\n"
        val blocks = MarkdownRenderer.splitBlocks(md, extractTables = true)
        assertTrue(blocks.none { it is MdBlock.Table })
    }
}
