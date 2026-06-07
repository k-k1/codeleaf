package jp.lazmix.codeleaf.data

import org.mozilla.universalchardet.UniversalDetector

/**
 * ビューアが扱うファイル種別。テキストは本文を文字列として読み、画像は描画し、
 * それ以外のバイナリは file(1) 風の概要だけを表示する(本文は読まない)。
 */
sealed interface FileKind {
    /** テキスト(コード/Markdown 含む)。 */
    data object Text : FileKind

    /** 画像。[format] は表示用の小文字フォーマット名(png/jpeg/svg 等)。 */
    data class Image(val format: String) : FileKind

    /** PDF。PdfRenderer でページを描画する。 */
    data object Pdf : FileKind

    /** 画像/PDF 以外のバイナリ。[typeLabel] は file(1) 風の種別名(「ELF 実行ファイル」等)。 */
    data class Binary(val typeLabel: String) : FileKind
}

/** 改行コード。[label] は上部メタバーの表示用。 */
enum class Eol(val label: String) {
    LF("LF"), CRLF("CRLF"), CR("CR"), MIXED("混在"), NONE("改行なし")
}

/** 本文読み込み結果。[truncated] はサイズ上限で先頭だけ読んだ(末尾を切った)ことを示す。 */
data class TextLoad(val text: String, val truncated: Boolean)

/** テキストファイルのメタ情報(上部バー表示・本文の再デコードに使う)。 */
data class TextMeta(
    /** 表示用のエンコード名(UTF-8 / Shift_JIS / EUC-JP / ASCII / 不明 等)。 */
    val encodingLabel: String,
    /** 本文デコードに使う Charset 名。null なら UTF-8 でフォールバック。 */
    val charsetName: String?,
    /** 先頭に BOM があるか。 */
    val hasBom: Boolean,
    val eol: Eol,
)

/**
 * ファイルの先頭バイトとサイズから判定した種別。[head] は判定に使った先頭バイト
 * (バイナリの 16 進プレビューにも使う)。[text] はテキストのときのメタ情報、
 * [imageWidth]/[imageHeight] はラスタ画像の寸法(取得できたときのみ)。
 */
class FileInfo(
    val kind: FileKind,
    val size: Long,
    val head: ByteArray,
    val text: TextMeta? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
)

/** ファイル名・先頭バイト・サイズからファイル種別を判定する(純粋関数・テスト可能)。 */
object FileClassifier {

    /** 種別判定に十分な先頭バイト数。これだけ読めば magic / NUL 判定が成り立つ。 */
    const val PROBE_BYTES = 8192

    fun classify(name: String, head: ByteArray, size: Long): FileKind {
        imageFormat(name, head)?.let { return FileKind.Image(it) }
        if (head.startsWith(0x25, 0x50, 0x44, 0x46)) return FileKind.Pdf // %PDF
        if (isBinary(head)) return FileKind.Binary(magicLabel(name, head))
        return FileKind.Text
    }

    /** 先頭バイトからテキストのエンコード(BOM→自動判定)と改行コードを推定する。 */
    fun textMeta(head: ByteArray): TextMeta {
        val (label, charset, bom) = detectEncoding(head)
        return TextMeta(label, charset, bom, detectEol(head))
    }

    /** (表示ラベル, Charset 名, BOM 有無)。BOM 優先・無ければ universalchardet で推定。 */
    private fun detectEncoding(h: ByteArray): Triple<String, String?, Boolean> {
        when {
            h.startsWith(0xEF, 0xBB, 0xBF) -> return Triple("UTF-8", "UTF-8", true)
            h.startsWith(0xFF, 0xFE, 0x00, 0x00) -> return Triple("UTF-32LE", "UTF-32LE", true)
            h.startsWith(0x00, 0x00, 0xFE, 0xFF) -> return Triple("UTF-32BE", "UTF-32BE", true)
            h.startsWith(0xFF, 0xFE) -> return Triple("UTF-16LE", "UTF-16LE", true)
            h.startsWith(0xFE, 0xFF) -> return Triple("UTF-16BE", "UTF-16BE", true)
        }
        val detected = runCatching {
            UniversalDetector(null).run {
                handleData(h, 0, h.size)
                dataEnd()
                detectedCharset.also { reset() }
            }
        }.getOrNull()
        if (!detected.isNullOrBlank()) {
            val label = if (detected.equals("US-ASCII", true)) "ASCII" else detected
            return Triple(label, detected, false)
        }
        // 判定不能。純 ASCII なら ASCII、そうでなければ不明(本文は UTF-8 で読む)。
        return if (h.all { it >= 0 }) Triple("ASCII", "US-ASCII", false) else Triple("不明", null, false)
    }

    /** 先頭バイトを走査して改行コードを判定する。複数種が混在すれば MIXED。 */
    private fun detectEol(h: ByteArray): Eol {
        var crlf = 0
        var cr = 0
        var lf = 0
        var i = 0
        while (i < h.size) {
            val b = h[i]
            if (b == 0x0D.toByte()) {
                if (i + 1 < h.size && h[i + 1] == 0x0A.toByte()) {
                    crlf++; i += 2; continue
                }
                cr++
            } else if (b == 0x0A.toByte()) {
                lf++
            }
            i++
        }
        val kinds = listOf(crlf, cr, lf).count { it > 0 }
        return when {
            kinds == 0 -> Eol.NONE
            kinds > 1 -> Eol.MIXED
            crlf > 0 -> Eol.CRLF
            cr > 0 -> Eol.CR
            else -> Eol.LF
        }
    }

    /** 画像なら小文字フォーマット名、そうでなければ null。magic 優先・無ければ拡張子。 */
    private fun imageFormat(name: String, h: ByteArray): String? {
        when {
            h.startsWith(0x89, 0x50, 0x4E, 0x47) -> return "png"
            h.startsWith(0xFF, 0xD8, 0xFF) -> return "jpeg"
            h.startsWith(0x47, 0x49, 0x46, 0x38) -> return "gif"
            h.startsWith(0x00, 0x00, 0x01, 0x00) -> return "ico"
            isRiff(h, "WEBP") -> return "webp"
        }
        // SVG はテキスト(XML)なので magic では拾えない。拡張子で判定し描画に回す。
        return when (ext(name)) {
            "png" -> "png"
            "jpg", "jpeg" -> "jpeg"
            "gif" -> "gif"
            "webp" -> "webp"
            "bmp" -> "bmp"
            "ico" -> "ico"
            "svg" -> "svg"
            "heic", "heif" -> "heif"
            "avif" -> "avif"
            else -> null
        }
    }

    /** 先頭バイトに NUL を含めばバイナリとみなす(loadSearchCorpus と同じ素朴な判定)。 */
    private fun isBinary(h: ByteArray): Boolean = h.any { it == 0.toByte() }

    /** magic / 拡張子から file(1) 風の種別ラベルを返す。判別不能は「バイナリ」。 */
    private fun magicLabel(name: String, h: ByteArray): String = when {
        h.startsWith(0x50, 0x4B, 0x03, 0x04) ||
            h.startsWith(0x50, 0x4B, 0x05, 0x06) ||
            h.startsWith(0x50, 0x4B, 0x07, 0x08) -> zipLabel(name)
        h.startsWith(0x1F, 0x8B) -> "gzip 圧縮"
        h.startsWith(0x42, 0x5A, 0x68) -> "bzip2 圧縮" // BZh
        h.startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> "xz 圧縮"
        h.startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "7z アーカイブ"
        h.startsWith(0x52, 0x61, 0x72, 0x21) -> "RAR アーカイブ" // Rar!
        h.startsWith(0x7F, 0x45, 0x4C, 0x46) -> "ELF 実行ファイル"
        h.startsWith(0xCA, 0xFE, 0xBA, 0xBE) -> "Java クラスファイル"
        h.startsWith(0xFE, 0xED, 0xFA, 0xCE) ||
            h.startsWith(0xFE, 0xED, 0xFA, 0xCF) ||
            h.startsWith(0xCF, 0xFA, 0xED, 0xFE) -> "Mach-O バイナリ"
        h.startsWith(0x4D, 0x5A) -> "Windows 実行ファイル" // MZ
        h.startsWith(0x00, 0x61, 0x73, 0x6D) -> "WebAssembly"
        startsWithAscii(h, "SQLite format 3") -> "SQLite データベース"
        startsWithAscii(h, "ID3") -> "MP3 音声"
        startsWithAscii(h, "OggS") -> "Ogg メディア"
        startsWithAscii(h, "fLaC") -> "FLAC 音声"
        isRiff(h, "WAVE") -> "WAV 音声"
        isRiff(h, "AVI ") -> "AVI 動画"
        hasAsciiAt(h, 4, "ftyp") -> "MP4/動画"
        startsWithAscii(h, "OTTO") || startsWithAscii(h, "wOFF") ||
            startsWithAscii(h, "wOF2") || h.startsWith(0x00, 0x01, 0x00, 0x00) -> "フォント"
        else -> "バイナリ"
    }

    private fun zipLabel(name: String): String = when (ext(name)) {
        "jar" -> "JAR アーカイブ"
        "apk" -> "APK パッケージ"
        "aar" -> "AAR ライブラリ"
        "docx", "xlsx", "pptx" -> "Office 文書"
        "odt", "ods", "odp" -> "OpenDocument 文書"
        "epub" -> "EPUB 書籍"
        else -> "ZIP アーカイブ"
    }

    private fun ext(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean {
        if (size < bytes.size) return false
        for (i in bytes.indices) if (this[i] != bytes[i].toByte()) return false
        return true
    }

    private fun startsWithAscii(h: ByteArray, s: String): Boolean = hasAsciiAt(h, 0, s)

    private fun hasAsciiAt(h: ByteArray, offset: Int, s: String): Boolean {
        if (h.size < offset + s.length) return false
        for (i in s.indices) if (h[offset + i] != s[i].code.toByte()) return false
        return true
    }

    /** RIFF コンテナ(先頭 "RIFF"、offset 8 に [form] 識別子)か。 */
    private fun isRiff(h: ByteArray, form: String): Boolean =
        hasAsciiAt(h, 0, "RIFF") && hasAsciiAt(h, 8, form)
}

/** バイト数を人が読める単位に整形する(1024 進・小数1桁)。 */
fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    val rounded = (v * 10).toLong() / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}" else "$rounded"
    return "$text ${units[i]}"
}
