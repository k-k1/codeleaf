package jp.lazmix.codeleaf.ui

/** 整形済み diff の1行。ノイズ(diff --git/index/---/+++)は除き、種別ごとに描き分ける。 */
sealed interface DiffRow {
    /** ファイル境界。displayPath は表示用(リネームは "旧 → 新")、newPath は開く対象=変更後/b 側パス。 */
    data class FileHeader(val displayPath: String, val newPath: String) : DiffRow
    /** ハンク見出し(@@ -a,b +c,d @@ ...)。 */
    data class Hunk(val text: String) : DiffRow
    /** 本文行。kind: '+'追加 / '-'削除 / ' '文脈。lineNo はその版での行番号(無いとき null)。 */
    data class Line(val text: String, val kind: Char, val lineNo: Int?) : DiffRow
}

private val HUNK_RE = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

/** unified diff を表示用の行リストに変換する純粋関数。ヘッダ/メタ行は畳み、本文に行番号を付ける。 */
fun parseDiffRows(diff: String): List<DiffRow> {
    val rows = ArrayList<DiffRow>()
    var inHunk = false // @@ を見たらその後の -/+ は本文。リセットは次ファイルで。
    var oldNo = 0
    var newNo = 0
    for (line in diff.lineSequence()) {
        when {
            line.startsWith("diff --git") -> {
                rows.add(DiffRow.FileHeader(diffHeaderPath(line), diffHeaderNewPath(line)))
                inHunk = false
            }
            line.startsWith("@@") -> {
                rows.add(DiffRow.Hunk(line))
                inHunk = true
                HUNK_RE.find(line)?.let { m ->
                    oldNo = m.groupValues[1].toInt()
                    newNo = m.groupValues[2].toInt()
                }
            }
            !inHunk && isDiffMeta(line) -> Unit // index/---/+++/mode/rename 等は隠す
            line.startsWith("\\ No newline") -> Unit
            else -> {
                when (line.firstOrNull()) {
                    '+' -> { rows.add(DiffRow.Line(line, '+', newNo)); newNo++ }
                    '-' -> { rows.add(DiffRow.Line(line, '-', oldNo)); oldNo++ }
                    else -> { rows.add(DiffRow.Line(line, ' ', if (inHunk) newNo else null)); if (inHunk) { oldNo++; newNo++ } }
                }
            }
        }
    }
    return rows
}

private fun isDiffMeta(line: String): Boolean =
    line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ") ||
        line.startsWith("old mode") || line.startsWith("new mode") ||
        line.startsWith("new file mode") || line.startsWith("deleted file mode") ||
        line.startsWith("similarity index") || line.startsWith("dissimilarity") ||
        line.startsWith("rename ") || line.startsWith("copy ")

/** "diff --git a/foo b/foo" の (旧パス, 新パス)。非ASCIIの git quote を復元。解析不能なら (rest, rest)。 */
private fun diffHeaderAB(line: String): Pair<String, String> {
    val rest = line.removePrefix("diff --git ").trim()
    return if (rest.startsWith("\"")) {
        // 両パスが引用符: "a/..." "b/..."
        val (q1, end1) = readQuoted(rest, 0)
        var k = end1
        while (k < rest.length && rest[k] == ' ') k++
        val q2 = if (k < rest.length && rest[k] == '"') readQuoted(rest, k).first else rest.substring(k)
        gitUnquotePath(q1).removePrefix("a/") to gitUnquotePath(q2).removePrefix("b/")
    } else {
        val sep = rest.indexOf(" b/")
        if (sep < 0) return rest to rest
        rest.substring(0, sep).removePrefix("a/") to rest.substring(sep + 3)
    }
}

/** ヘッダ表示用パス(リネームは "旧 → 新")。 */
internal fun diffHeaderPath(line: String): String {
    val (a, b) = diffHeaderAB(line)
    return if (a == b) b else "$a → $b"
}

/** 開く対象パス(=変更後/b 側)。add/delete でも実パスが入る。 */
internal fun diffHeaderNewPath(line: String): String = diffHeaderAB(line).second

/** s[start]=='"' から閉じ引用符まで(引用符含む)を返す。戻り(部分文字列, 閉じ引用符直後index)。 */
private fun readQuoted(s: String, start: Int): Pair<String, Int> {
    var i = start + 1
    while (i < s.length) {
        when (s[i]) {
            '\\' -> i += 2
            '"' -> return s.substring(start, i + 1) to (i + 1)
            else -> i++
        }
    }
    return s.substring(start) to s.length
}

/**
 * git が quote したパス("\346\227\245..." 形式)を復元する。引用符が無ければそのまま返す。
 * C系エスケープ(\\ \" \t \n \r)と8進エスケープ(\nnn)を解釈し、バイト列を UTF-8 として復号する。
 */
internal fun gitUnquotePath(s: String): String {
    if (s.length < 2 || s[0] != '"' || s[s.length - 1] != '"') return s
    val body = s.substring(1, s.length - 1)
    val bytes = ArrayList<Byte>(body.length)
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c == '\\' && i + 1 < body.length) {
            when (val n = body[i + 1]) {
                '\\' -> { bytes.add('\\'.code.toByte()); i += 2 }
                '"' -> { bytes.add('"'.code.toByte()); i += 2 }
                't' -> { bytes.add('\t'.code.toByte()); i += 2 }
                'n' -> { bytes.add('\n'.code.toByte()); i += 2 }
                'r' -> { bytes.add('\r'.code.toByte()); i += 2 }
                in '0'..'7' -> {
                    var j = i + 1
                    var oct = 0
                    var cnt = 0
                    while (j < body.length && cnt < 3 && body[j] in '0'..'7') {
                        oct = oct * 8 + (body[j] - '0'); j++; cnt++
                    }
                    bytes.add(oct.toByte()); i = j
                }
                else -> { bytes.add(n.code.toByte()); i += 2 }
            }
        } else {
            bytes.add(c.code.toByte()); i++
        }
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

/** 1ファイル分の diff(ヘッダ＋本文行)。 */
data class DiffFile(val header: DiffRow.FileHeader?, val rows: List<DiffRow>)

/** 行リストをファイル単位(FileHeader 区切り)にまとめる純粋関数。 */
fun groupDiffByFile(rows: List<DiffRow>): List<DiffFile> {
    val files = ArrayList<DiffFile>()
    var header: DiffRow.FileHeader? = null
    var body = ArrayList<DiffRow>()
    fun flush() { if (header != null || body.isNotEmpty()) files.add(DiffFile(header, body)) }
    for (r in rows) {
        if (r is DiffRow.FileHeader) { flush(); header = r; body = ArrayList() } else body.add(r)
    }
    flush()
    return files
}
