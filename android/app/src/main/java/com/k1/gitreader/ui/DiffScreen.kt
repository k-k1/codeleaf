package com.k1.gitreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    sha: String,
    loadDiff: suspend () -> String,
    onBack: () -> Unit,
) {
    var diff by remember(sha) { mutableStateOf<String?>(null) }
    var error by remember(sha) { mutableStateOf<String?>(null) }

    LaunchedEffect(sha) {
        error = null
        diff = runCatching { loadDiff() }.getOrElse { error = it.message; "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("diff ${sha.take(7)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val text = diff
            when {
                error != null -> Text("diff取得失敗: $error", Modifier.padding(16.dp))
                text == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                text.isBlank() -> Text("差分なし", Modifier.padding(16.dp))
                else -> DiffText(text, Modifier.fillMaxSize())
            }
        }
    }
}

/** 整形済み diff の1行。ノイズ(diff --git/index/---/+++)は除き、種別ごとに描き分ける。 */
sealed interface DiffRow {
    /** ファイル境界。path は変更後パス(リネームは "旧 → 新")。 */
    data class FileHeader(val path: String) : DiffRow
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
            line.startsWith("diff --git") -> { rows.add(DiffRow.FileHeader(diffHeaderPath(line))); inHunk = false }
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

/** "diff --git a/foo b/foo" からパスを取り出す(リネームは "旧 → 新")。非ASCIIの git quote を復元。 */
internal fun diffHeaderPath(line: String): String {
    val rest = line.removePrefix("diff --git ").trim()
    val (a, b) = if (rest.startsWith("\"")) {
        // 両パスが引用符: "a/..." "b/..."
        val (q1, end1) = readQuoted(rest, 0)
        var k = end1
        while (k < rest.length && rest[k] == ' ') k++
        val q2 = if (k < rest.length && rest[k] == '"') readQuoted(rest, k).first else rest.substring(k)
        gitUnquotePath(q1).removePrefix("a/") to gitUnquotePath(q2).removePrefix("b/")
    } else {
        val sep = rest.indexOf(" b/")
        if (sep < 0) return rest
        rest.substring(0, sep).removePrefix("a/") to rest.substring(sep + 3)
    }
    return if (a == b) b else "$a → $b"
}

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

/** 整形済み diff を表示する(DiffScreen / コミット詳細で共有)。長い行は自動改行・行番号付き。 */
@Composable
fun DiffText(diff: String, modifier: Modifier = Modifier) {
    val rows = remember(diff) { parseDiffRows(diff) }
    val base = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hunkBg = MaterialTheme.colorScheme.surfaceVariant
    val headerBg = MaterialTheme.colorScheme.secondaryContainer
    val headerFg = MaterialTheme.colorScheme.onSecondaryContainer
    val addBg = Color(0xFF2E7D32).copy(alpha = 0.16f)
    val delBg = Color(0xFFC62828).copy(alpha = 0.16f)
    // 行番号ガターの幅は最大桁数から決める。
    val maxNo = remember(rows) { rows.maxOfOrNull { (it as? DiffRow.Line)?.lineNo ?: 0 } ?: 0 }
    val gutterChars = maxOf(2, maxNo.toString().length)
    val gutterWidth = (gutterChars * 8 + 12).dp

    Column(modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { row ->
            when (row) {
                is DiffRow.FileHeader -> Text(
                    text = row.path,
                    color = headerFg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth().background(headerBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                is DiffRow.Hunk -> Text(
                    text = row.text,
                    color = muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth().background(hunkBg)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                )
                is DiffRow.Line -> Row(
                    Modifier.fillMaxWidth()
                        .background(when (row.kind) { '+' -> addBg; '-' -> delBg; else -> Color.Transparent })
                        .padding(vertical = 1.dp),
                ) {
                    Text(
                        text = row.lineNo?.toString().orEmpty(),
                        color = muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(gutterWidth).padding(end = 6.dp),
                    )
                    Text(
                        text = row.text.ifEmpty { " " },
                        color = base,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        softWrap = true,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
