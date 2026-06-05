package com.k1.gitreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffRowsTest {

    @Test
    fun hidesNoiseAndClassifiesLines() {
        val diff = """
            diff --git a/foo.kt b/foo.kt
            index abc1234..def5678 100644
            --- a/foo.kt
            +++ b/foo.kt
            @@ -1,3 +1,3 @@ class Foo
             ctx
            -old
            +new
        """.trimIndent()

        val rows = parseDiffRows(diff)
        // index/---/+++ は畳まれ、ヘッダ・ハンク・本文3行のみ。行番号は @@ -1,3 +1,3 起点。
        assertEquals(5, rows.size)
        assertEquals(DiffRow.FileHeader("foo.kt"), rows[0])
        assertTrue(rows[1] is DiffRow.Hunk)
        assertEquals(DiffRow.Line(" ctx", ' ', 1), rows[2])
        assertEquals(DiffRow.Line("-old", '-', 2), rows[3]) // 旧側 2行目
        assertEquals(DiffRow.Line("+new", '+', 2), rows[4]) // 新側 2行目
    }

    @Test
    fun renameShowsBothPaths() {
        val diff = """
            diff --git a/old.kt b/new.kt
            similarity index 95%
            rename from old.kt
            rename to new.kt
        """.trimIndent()

        val rows = parseDiffRows(diff)
        assertEquals(1, rows.size)
        assertEquals(DiffRow.FileHeader("old.kt → new.kt"), rows[0])
    }

    @Test
    fun deletedSourceLineStartingWithDashesIsContentNotMeta() {
        // 本文として削除された "-- foo"(diff 行 "--- foo")はメタではなく本文行。
        val diff = """
            diff --git a/a.md b/a.md
            @@ -1,1 +0,0 @@
            --- foo
        """.trimIndent()

        val rows = parseDiffRows(diff)
        assertTrue(rows.last() is DiffRow.Line && (rows.last() as DiffRow.Line).kind == '-')
    }

    @Test
    fun unquotesNonAsciiPath() {
        // git は非ASCIIパスを8進エスケープ＋引用符で quote する。"日本語.md" を復元。
        val line = "diff --git \"a/\\346\\227\\245\\346\\234\\254\\350\\252\\236.md\" " +
            "\"b/\\346\\227\\245\\346\\234\\254\\350\\252\\236.md\""
        val rows = parseDiffRows(line)
        assertEquals(DiffRow.FileHeader("日本語.md"), rows[0])
    }

    @Test
    fun lineNumbersAdvancePerHunk() {
        val diff = """
            diff --git a/x b/x
            @@ -10,2 +20,3 @@
             keep
            +added
             tail
        """.trimIndent()
        val lines = parseDiffRows(diff).filterIsInstance<DiffRow.Line>()
        assertEquals(listOf(20, 21, 22), lines.map { it.lineNo })
    }
}
