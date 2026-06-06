package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.MemoEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoFormatTest {

    private fun entry(
        file: String,
        start: Int,
        end: Int,
        quote: String,
        comment: String,
    ) = MemoEntry(
        id = 0, memoId = 1, filePath = file,
        lineStart = start, lineEnd = end, quote = quote, comment = comment, createdAt = 0,
    )

    @Test
    fun lineRange_singleVsMulti() {
        assertEquals("L42", MemoFormat.lineRange(42, 42))
        assertEquals("L42-43", MemoFormat.lineRange(42, 43))
        // start>=end は単一行表記に丸める(防御的)。
        assertEquals("L5", MemoFormat.lineRange(5, 3))
    }

    @Test
    fun entry_quotesEachLineAndAppendsComment() {
        val text = MemoFormat.entry("my-app", entry("src/Main.kt", 42, 43, "val x = compute()\nreturn x", "ここ重い"))
        assertEquals(
            """
            [my-app] src/Main.kt L42-43
            > val x = compute()
            > return x
            ここ重い
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun entry_withoutComment_omitsCommentLine() {
        val text = MemoFormat.entry("r", entry("a.txt", 1, 1, "hello", "  "))
        assertEquals("[r] a.txt L1\n> hello", text)
    }

    @Test
    fun memo_joinsEntriesWithHeadingAndBlankLines() {
        val text = MemoFormat.memo(
            "my-app",
            "レビュー",
            listOf(
                entry("src/Main.kt", 42, 42, "val x = 1", "重い"),
                entry("README.md", 1, 1, "# My App", "古い"),
            ),
        )
        assertEquals(
            """
            # レビュー (my-app)

            [my-app] src/Main.kt L42
            > val x = 1
            重い

            [my-app] README.md L1
            > # My App
            古い
            """.trimIndent(),
            text,
        )
    }
}
