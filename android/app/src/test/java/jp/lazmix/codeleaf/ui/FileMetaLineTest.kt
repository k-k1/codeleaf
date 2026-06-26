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

    // i18n 後の fileMetaLine はロケール依存ラベルを引数で受ける。
    // テストは ja 相当のラベルを供給して従来の期待値(日本語)を維持する。
    private fun line(info: FileInfo): String? =
        fileMetaLine(info, encodingUnknown = "不明", noBom = "BOMなし") { eol ->
            when (eol) {
                Eol.LF -> "LF"
                Eol.CRLF -> "CRLF"
                Eol.CR -> "CR"
                Eol.MIXED -> "混在"
                Eol.NONE -> "改行なし"
            }
        }

    @Test fun text_showsEncodingBomEolSize() {
        val info = FileInfo(
            kind = FileKind.Text,
            size = 1536,
            head = empty,
            text = TextMeta("UTF-8", "UTF-8", hasBom = false, eol = Eol.LF),
        )
        assertEquals("UTF-8 ・ BOMなし ・ LF ・ 1.5 KB", line(info))
    }

    @Test fun text_withBom() {
        val info = FileInfo(
            kind = FileKind.Text,
            size = 100,
            head = empty,
            text = TextMeta("Shift_JIS", "Shift_JIS", hasBom = false, eol = Eol.CRLF),
        )
        assertEquals("Shift_JIS ・ BOMなし ・ CRLF ・ 100 B", line(info))
    }

    @Test fun text_unknownEncoding_usesUnknownLabel() {
        // charsetName が null のとき encodingLabel ではなく「不明」ラベルを出す。
        val info = FileInfo(
            kind = FileKind.Text,
            size = 10,
            head = empty,
            text = TextMeta("", null, hasBom = false, eol = Eol.LF),
        )
        assertEquals("不明 ・ BOMなし ・ LF ・ 10 B", line(info))
    }

    @Test fun image_showsFormatDimsSize() {
        val info = FileInfo(
            kind = FileKind.Image("png"),
            size = 12_288,
            head = empty,
            imageWidth = 240,
            imageHeight = 160,
        )
        assertEquals("PNG ・ 240×160 ・ 12 KB", line(info))
    }

    @Test fun image_withoutDims_omitsThem() {
        val info = FileInfo(kind = FileKind.Image("svg"), size = 2048, head = empty)
        assertEquals("SVG ・ 2 KB", line(info))
    }

    @Test fun pdf_showsPdfAndSize() {
        val info = FileInfo(kind = FileKind.Pdf, size = 4096, head = empty)
        assertEquals("PDF ・ 4 KB", line(info))
    }

    @Test fun binary_hasNoBar() {
        val info = FileInfo(kind = FileKind.Binary("elf"), size = 4096, head = empty)
        assertNull(line(info))
    }
}
