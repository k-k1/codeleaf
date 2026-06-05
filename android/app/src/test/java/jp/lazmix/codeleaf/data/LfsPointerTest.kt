package jp.lazmix.codeleaf.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LfsPointerTest {

    private val pointer = """
        version https://git-lfs.github.com/spec/v1
        oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393
        size 12345
    """.trimIndent()

    @Test
    fun detectsLfsPointerByHead() {
        assertTrue(isLfsPointerHead(pointer))
    }

    @Test
    fun rejectsOrdinaryText() {
        assertFalse(isLfsPointerHead("# README\n\njust a normal markdown file"))
        assertFalse(isLfsPointerHead("version 1.2.3"))
        assertFalse(isLfsPointerHead(""))
    }
}
