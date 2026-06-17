package jp.lazmix.codeleaf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatTest {

    @Test fun humanSize_formats() {
        assertEquals("512 B", humanSize(512))
        assertEquals("1 KB", humanSize(1024))
        assertEquals("1.5 KB", humanSize(1536))
        assertEquals("1 MB", humanSize(1024L * 1024))
        assertEquals("2.5 MB", humanSize((2.5 * 1024 * 1024).toLong()))
    }
}
