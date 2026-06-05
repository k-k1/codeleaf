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
        // index/---/+++ は畳まれ、ヘッダ・ハンク・本文3行のみ。
        assertEquals(5, rows.size)
        assertEquals(DiffRow.FileHeader("foo.kt"), rows[0])
        assertTrue(rows[1] is DiffRow.Hunk)
        assertEquals(DiffRow.Line(" ctx", ' '), rows[2])
        assertEquals(DiffRow.Line("-old", '-'), rows[3])
        assertEquals(DiffRow.Line("+new", '+'), rows[4])
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
        assertEquals(DiffRow.Line("--- foo", '-'), rows.last())
    }
}
