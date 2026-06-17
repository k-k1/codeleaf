package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.lazmix.codeleaf.CodeLeafApplication
import jp.lazmix.codeleaf.git.CommitInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    commit: CommitInfo,
    loadDiff: suspend () -> String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit = {},
) {
    Scaffold(
        // 本文(DiffView)が自前の下部バーで navigationBars を padding するため、Scaffold 側からは除外。
        contentWindowInsets = contentInsetsExcludingNavBar,
        topBar = {
            TopAppBar(
                title = { Text("diff ${shortSha(commit.sha)}") },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { padding ->
        // コミットメッセージ見出し＋ファイル diff(2/3ペインの右と共通の FileDiffPane)。
        Box(Modifier.fillMaxSize().padding(padding)) {
            FileDiffPane(commit, loadDiff, onOpenFile)
        }
    }
}

/** 整形済み diff を表示する(DiffScreen / コミット詳細 / ファイル履歴で共有)。
 *  ファイル毎に折りたたみ可・行番号付き。下部バーの「折り返しON/OFF」で長行の折り返し/横スクロールを切替。 */
@Composable
fun DiffView(diff: String, modifier: Modifier = Modifier, onOpenFile: (String) -> Unit = {}) {
    val rows = remember(diff) { parseDiffRows(diff) }
    val files = remember(rows) { groupDiffByFile(rows) }
    val collapsed = remember(diff) { mutableStateMapOf<Int, Boolean>() }
    // 折り返しは diff 専用設定として永続化(ファイル閲覧の wrapByDefault とは別管理)。既定 ON。
    val context = LocalContext.current
    val settingsStore = remember { (context.applicationContext as CodeLeafApplication).container.settingsStore }
    var wrap by remember { mutableStateOf(settingsStore.settings.value.diffWrap) }
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
    val hScroll = rememberScrollState()

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f)) {
            // 折り返しOFF時も追加緑/削除赤の帯がペイン右端まで届くよう、各行は最低でもビューポート幅を確保する
            // (内容がそれより長ければ内容幅まで広がり横スクロール可)。折り返しONは従来どおり全幅。
            val rowMod = if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = maxWidth)
            // wrap=false のときは本文全体を横スクロール可に(CodeView と同じ挙動)。
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .let { if (wrap) it else it.horizontalScroll(hScroll) },
            ) {
                files.forEachIndexed { i, file ->
                    val isCollapsed = collapsed[i] == true
                    file.header?.let { h ->
                        Row(
                            rowMod
                                .background(headerBg)
                                .clickable { collapsed[i] = !isCollapsed }
                                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCollapsed) "展開" else "折りたたむ",
                                tint = headerFg,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                h.displayPath,
                                color = headerFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                softWrap = wrap,
                                maxLines = if (wrap) Int.MAX_VALUE else 1,
                                // 折り返しON(既定): 名前を伸ばしてボタンをバー右端へ寄せる。
                                modifier = if (wrap) Modifier.weight(1f) else Modifier,
                            )
                            Spacer(Modifier.width(4.dp))
                            // ファイルを Viewer で開く(独立クリック・親の折りたたみは発火しない)。バー右寄せ。
                            Box(
                                Modifier.size(30.dp).clickable { onOpenFile(h.newPath) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "ファイルを開く",
                                    tint = headerFg,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (!isCollapsed) {
                        file.rows.forEach { row ->
                            when (row) {
                                is DiffRow.FileHeader -> Unit // ヘッダは上で描画済み
                                is DiffRow.Hunk -> Text(
                                    text = row.text,
                                    color = muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    softWrap = wrap,
                                    maxLines = if (wrap) Int.MAX_VALUE else 1,
                                    modifier = rowMod
                                        .background(hunkBg)
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                )
                                is DiffRow.Line -> Row(
                                    rowMod
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
                                        softWrap = wrap,
                                        maxLines = if (wrap) Int.MAX_VALUE else 1,
                                        modifier = (if (wrap) Modifier.weight(1f) else Modifier).padding(end = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        // 下部バー: 左=全ファイルの折りたたみ一括操作(2ファイル以上のとき)/ 右=折り返し切替(Viewer と同様)。
        SlimBottomBar {
            if (files.size > 1) {
                // 1つでも展開中なら「すべて折りたたむ」、全て畳んでいれば「すべて展開」。
                val allCollapsed = files.indices.all { collapsed[it] == true }
                TextButton(onClick = { files.indices.forEach { collapsed[it] = !allCollapsed } }) {
                    Icon(
                        if (allCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (allCollapsed) "すべて展開" else "すべて折りたたむ")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { wrap = !wrap; settingsStore.setDiffWrap(wrap) },
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(if (wrap) "折り返しON" else "折り返しOFF") }
        }
    }
}
