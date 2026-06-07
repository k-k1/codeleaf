package jp.lazmix.codeleaf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileClassifierTest {

    private fun bytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    private fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

    @Test fun plainText_isText() {
        assertEquals(FileKind.Text, FileClassifier.classify("a.txt", ascii("hello\nworld"), 11))
    }

    @Test fun textWithNul_isBinary() {
        val k = FileClassifier.classify("a.dat", bytes('h'.code, 0x00, 'i'.code), 3)
        assertEquals(FileKind.Binary("バイナリ"), k)
    }

    @Test fun pngMagic_isImage() {
        val k = FileClassifier.classify("noext", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), 8)
        assertEquals(FileKind.Image("png"), k)
    }

    @Test fun jpegMagic_isImage() {
        assertEquals(FileKind.Image("jpeg"), FileClassifier.classify("x", bytes(0xFF, 0xD8, 0xFF, 0xE0), 4))
    }

    @Test fun svgByExtension_isImage() {
        // SVG はテキストだが拡張子で画像扱いにして描画へ回す。
        assertEquals(FileKind.Image("svg"), FileClassifier.classify("logo.svg", ascii("<svg></svg>"), 11))
    }

    @Test fun pdf_isPdfKind() {
        val head = ascii("%PDF-1.7") + bytes(0x00)
        assertEquals(FileKind.Pdf, FileClassifier.classify("doc.pdf", head, 100))
    }

    @Test fun zipJarExtension_labelsJar() {
        val head = bytes(0x50, 0x4B, 0x03, 0x04, 0x00)
        assertEquals(FileKind.Binary("JAR アーカイブ"), FileClassifier.classify("lib.jar", head, 100))
    }

    @Test fun zipPlain_labelsZip() {
        val head = bytes(0x50, 0x4B, 0x03, 0x04, 0x00)
        assertEquals(FileKind.Binary("ZIP アーカイブ"), FileClassifier.classify("a.zip", head, 100))
    }

    @Test fun elf_isLabeled() {
        val head = bytes(0x7F, 0x45, 0x4C, 0x46, 0x00)
        assertEquals(FileKind.Binary("ELF 実行ファイル"), FileClassifier.classify("a.out", head, 100))
    }

    @Test fun gzip_isLabeled() {
        val head = bytes(0x1F, 0x8B, 0x08, 0x00)
        assertEquals(FileKind.Binary("gzip 圧縮"), FileClassifier.classify("a.gz", head, 100))
    }

    @Test fun humanSize_formats() {
        assertEquals("512 B", humanSize(512))
        assertEquals("1 KB", humanSize(1024))
        assertEquals("1.5 KB", humanSize(1536))
        assertEquals("1 MB", humanSize(1024L * 1024))
        assertEquals("2.5 MB", humanSize((2.5 * 1024 * 1024).toLong()))
    }

    // --- textMeta: 改行コード ---

    @Test fun eol_lf() {
        assertEquals(Eol.LF, FileClassifier.textMeta(ascii("a\nb\nc")).eol)
    }

    @Test fun eol_crlf() {
        assertEquals(Eol.CRLF, FileClassifier.textMeta(ascii("a\r\nb\r\n")).eol)
    }

    @Test fun eol_cr() {
        assertEquals(Eol.CR, FileClassifier.textMeta(ascii("a\rb\r")).eol)
    }

    @Test fun eol_mixed() {
        assertEquals(Eol.MIXED, FileClassifier.textMeta(ascii("a\r\nb\nc")).eol)
    }

    @Test fun eol_none() {
        assertEquals(Eol.NONE, FileClassifier.textMeta(ascii("single line")).eol)
    }

    // --- textMeta: エンコード ---

    @Test fun encoding_utf8Bom() {
        val m = FileClassifier.textMeta(bytes(0xEF, 0xBB, 0xBF, 'h'.code, 'i'.code))
        assertEquals("UTF-8", m.encodingLabel)
        assertEquals(true, m.hasBom)
    }

    @Test fun encoding_ascii() {
        val m = FileClassifier.textMeta(ascii("plain ascii content here\n"))
        assertEquals("ASCII", m.encodingLabel)
        assertEquals(false, m.hasBom)
    }

    @Test fun encoding_shiftJis() {
        // 十分な長さの日本語(Shift_JIS)なら自動判定が効く。
        val text = "これはシフトジスで保存された日本語のテキストです。文字コード判定の確認に使います。".repeat(2)
        val m = FileClassifier.textMeta(text.toByteArray(charset("Shift_JIS")))
        assertEquals("SHIFT_JIS", m.charsetName?.uppercase())
    }
}
