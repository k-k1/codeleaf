package jp.lazmix.codeleaf.ui

import jp.lazmix.codeleaf.data.Eol
import jp.lazmix.codeleaf.data.FileInfo
import jp.lazmix.codeleaf.data.FileKind
import jp.lazmix.codeleaf.data.TextMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileMetaLineTest {

    private val empty = ByteArray(0)

    @Test fun text_showsEncodingBomEolSize() {
        val info = FileInfo(
            kind = FileKind.Text,
            size = 1536,
            head = empty,
            text = TextMeta("UTF-8", "UTF-8", hasBom = false, eol = Eol.LF),
        )
        assertEquals("UTF-8 ・ BOMなし ・ LF ・ 1.5 KB", fileMetaLine(info))
    }

    @Test fun text_withBom() {
        val info = FileInfo(
            kind = FileKind.Text,
            size = 100,
            head = empty,
            text = TextMeta("Shift_JIS", "Shift_JIS", hasBom = false, eol = Eol.CRLF),
        )
        assertEquals("Shift_JIS ・ BOMなし ・ CRLF ・ 100 B", fileMetaLine(info))
    }

    @Test fun image_showsFormatDimsSize() {
        val info = FileInfo(
            kind = FileKind.Image("png"),
            size = 12_288,
            head = empty,
            imageWidth = 240,
            imageHeight = 160,
        )
        assertEquals("PNG ・ 240×160 ・ 12 KB", fileMetaLine(info))
    }

    @Test fun image_withoutDims_omitsThem() {
        val info = FileInfo(kind = FileKind.Image("svg"), size = 2048, head = empty)
        assertEquals("SVG ・ 2 KB", fileMetaLine(info))
    }

    @Test fun binary_hasNoBar() {
        val info = FileInfo(kind = FileKind.Binary("PDF 文書"), size = 4096, head = empty)
        assertNull(fileMetaLine(info))
    }
}
