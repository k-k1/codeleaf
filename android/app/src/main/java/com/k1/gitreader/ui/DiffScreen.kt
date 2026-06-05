package com.k1.gitreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
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
    /** 本文行。kind: '+'追加 / '-'削除 / ' '文脈。 */
    data class Line(val text: String, val kind: Char) : DiffRow
}

/** unified diff を表示用の行リストに変換する純粋関数。ヘッダのコマンド行/メタ行は畳む。 */
fun parseDiffRows(diff: String): List<DiffRow> {
    val rows = ArrayList<DiffRow>()
    var inHunk = false // @@ を見たらその後の -/+ は本文。リセットは次ファイルで。
    for (line in diff.lineSequence()) {
        when {
            line.startsWith("diff --git") -> { rows.add(DiffRow.FileHeader(diffHeaderPath(line))); inHunk = false }
            line.startsWith("@@") -> { rows.add(DiffRow.Hunk(line)); inHunk = true }
            !inHunk && isDiffMeta(line) -> Unit // index/---/+++/mode/rename 等は隠す
            line.startsWith("\\ No newline") -> Unit
            else -> {
                val c = line.firstOrNull()
                rows.add(DiffRow.Line(line, if (c == '+' || c == '-') c else ' '))
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

/** "diff --git a/foo b/foo" からパスを取り出す(リネームは "a → b")。 */
private fun diffHeaderPath(line: String): String {
    val rest = line.removePrefix("diff --git ").trim()
    val sep = rest.indexOf(" b/")
    if (sep < 0) return rest
    val a = rest.substring(0, sep).removePrefix("a/")
    val b = rest.substring(sep + 3)
    return if (a == b) b else "$a → $b"
}

/** 整形済み diff を表示する(DiffScreen / コミット詳細で共有)。長い行は自動改行。 */
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
                is DiffRow.Line -> Text(
                    text = row.text.ifEmpty { " " },
                    color = base,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            when (row.kind) { '+' -> addBg; '-' -> delBg; else -> Color.Transparent },
                        )
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
