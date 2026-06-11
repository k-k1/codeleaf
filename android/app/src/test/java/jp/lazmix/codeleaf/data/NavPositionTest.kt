package jp.lazmix.codeleaf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavPositionTest {

    @Test
    fun roundTripWithFilesAndFocus() {
        val pos = NavPosition(
            chain = listOf("", "src", "src/main"),
            files = listOf(OpenFile("README.md", null), OpenFile("src/main/A.kt", 42)),
            focus = true,
        )
        assertEquals(pos, NavPosition.fromJson(pos.toJson()))
    }

    @Test
    fun roundTripRootOnlyNoFiles() {
        val pos = NavPosition(chain = listOf(""), files = emptyList(), focus = false)
        val back = NavPosition.fromJson(pos.toJson())
        assertEquals(pos, back)
        assertNull(back.files.firstOrNull())
    }

    @Test
    fun lineNullVsValuePreserved() {
        val pos = NavPosition(listOf(""), listOf(OpenFile("a", null), OpenFile("b", 0)), false)
        val back = NavPosition.fromJson(pos.toJson())
        assertNull(back.files[0].line)
        assertEquals(0, back.files[1].line)
    }

    @Test(expected = org.json.JSONException::class)
    fun corruptJsonThrows() {
        // 呼び側(NavPositionStore.get)は runCatching で握り潰す前提。
        NavPosition.fromJson("not json")
    }
}
