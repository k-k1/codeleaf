package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.lazmix.codeleaf.CodeLeafApplication
import jp.lazmix.codeleaf.git.CommitInfo
import jp.lazmix.codeleaf.git.DiffFileSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ファイル数がこれ以下なら初期状態で全展開、超えたら全折りたたみ（大量ファイルでの一斉整形を避ける）。 */
private const val EXPAND_ALL_THRESHOLD = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    commit: CommitInfo,
    loadDiff: suspend () -> String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    /** submodule(gitlink)変更を範囲表示するための解決関数。非 null=submodule 経路。 */
    loadSubmoduleChange: (suspend () -> jp.lazmix.codeleaf.git.SubmoduleChange?)? = null,
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
            FileDiffPane(commit, loadDiff, onOpenFile, loadSubmoduleChange)
        }
    }
}

/** diff 描画で使う色をまとめて1回だけ解決する（行ごとの再計算を避ける）。 */
private class DiffPalette(
    val base: Color,
    val muted: Color,
    val hunkBg: Color,
    val headerBg: Color,
    val headerFg: Color,
    val addBg: Color,
    val delBg: Color,
)

@Composable
private fun rememberDiffPalette(): DiffPalette {
    val base = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hunkBg = MaterialTheme.colorScheme.surfaceVariant
    val headerBg = MaterialTheme.colorScheme.secondaryContainer
    val headerFg = MaterialTheme.colorScheme.onSecondaryContainer
    return remember(base, muted, hunkBg, headerBg, headerFg) {
        DiffPalette(
            base, muted, hunkBg, headerBg, headerFg,
            addBg = Color(0xFF2E7D32).copy(alpha = 0.16f),
            delBg = Color(0xFFC62828).copy(alpha = 0.16f),
        )
    }
}

/** 行番号ガター幅を最大行番号の桁数から決める。 */
private fun gutterWidthFor(rows: List<DiffRow>): Dp {
    val maxNo = rows.maxOfOrNull { (it as? DiffRow.Line)?.lineNo ?: 0 } ?: 0
    val chars = maxOf(2, maxNo.toString().length)
    return (chars * 8 + 12).dp
}

/**
 * 整形済み diff 文字列を表示する（単一ファイル diff: 履歴/ファイル diff ペインで使用）。
 * パースはオフメインスレッドで行い、描画は LazyColumn で可視行だけを構成する（大量行でも固まらない）。
 */
@Composable
fun DiffView(diff: String, modifier: Modifier = Modifier, onOpenFile: (String) -> Unit = {}) {
    // パースは Default ディスパッチャで（数万行でもメインスレッドを塞がない）。完了まで null。
    val files by produceState<List<DiffFile>?>(initialValue = null, diff) {
        value = withContext(Dispatchers.Default) { groupDiffByFile(parseDiffRows(diff)) }
    }
    val f = files
    if (f == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val collapsed = remember(diff) { mutableStateMapOf<Int, Boolean>() }
    DiffLazyContent(
        modifier = modifier,
        fileCount = f.size,
        isCollapsed = { collapsed[it] == true },
        setCollapsed = { i, v -> collapsed[i] = v },
        headerOf = { f[it].header },
        onOpenFile = onOpenFile,
    ) { i, wrap, rowMod, palette ->
        val rows = f[i].rows
        diffBodyItems("f$i", rows, gutterWidthFor(rows), wrap, rowMod, palette)
    }
}

/**
 * コミット全体の diff を「ファイル一覧→展開時に各ファイルを遅延整形」で表示する。
 * 一度に全ファイルを整形しないため、大量ファイル/巨大 diff でも固まらない（本命の設計）。
 */
@Composable
fun CommitDiffView(
    files: List<DiffFileSummary>,
    loadFileDiff: suspend (DiffFileSummary) -> String,
    modifier: Modifier = Modifier,
    onOpenFile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 初期展開: 少ファイルは全展開して先読み、多ければ全折りたたみ（構築時に確定＝初回フレームから正しい状態）。
    val initiallyExpanded = files.size <= EXPAND_ALL_THRESHOLD
    val collapsed = remember(files) {
        mutableStateMapOf<Int, Boolean>().apply { files.indices.forEach { put(it, !initiallyExpanded) } }
    }
    // 各ファイル本文のパース結果。未格納=未取得 / null=取得中 / 値=取得済み。
    val rowsMap = remember(files) { mutableStateMapOf<Int, List<DiffRow>?>() }

    fun load(i: Int) {
        if (rowsMap.containsKey(i)) return
        rowsMap[i] = null
        scope.launch {
            val text = runCatching { loadFileDiff(files[i]) }.getOrElse { "取得失敗: ${it.message}" }
            val rows = withContext(Dispatchers.Default) { parseDiffRows(text) }
            rowsMap[i] = rows
        }
    }

    // 全展開時は本文を先読み。
    LaunchedEffect(files) {
        if (initiallyExpanded) files.indices.forEach { load(it) }
    }

    DiffLazyContent(
        modifier = modifier,
        fileCount = files.size,
        isCollapsed = { collapsed[it] == true },
        setCollapsed = { i, v ->
            collapsed[i] = v
            if (!v) load(i)
        },
        headerOf = { DiffRow.FileHeader(files[it].displayPath, files[it].openPath) },
        onOpenFile = onOpenFile,
    ) { i, wrap, rowMod, palette ->
        when (val rows = rowsMap[i]) {
            null -> item(key = "load$i") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.CenterStart) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                }
            }
            else -> diffBodyItems("f$i", rows, gutterWidthFor(rows), wrap, rowMod, palette)
        }
    }
}

/**
 * ファイル単位の折りたたみ・折り返し切替・横スクロールを備えた diff の枠組み。
 * 本文行は [bodyItems] が LazyColumn のスコープに積む（即時パース版 / 遅延整形版で中身を差し替える）。
 */
@Composable
private fun DiffLazyContent(
    modifier: Modifier,
    fileCount: Int,
    isCollapsed: (Int) -> Boolean,
    setCollapsed: (Int, Boolean) -> Unit,
    headerOf: (Int) -> DiffRow.FileHeader?,
    onOpenFile: (String) -> Unit,
    bodyItems: LazyListScope.(fileIndex: Int, wrap: Boolean, rowMod: Modifier, palette: DiffPalette) -> Unit,
) {
    val palette = rememberDiffPalette()
    val context = LocalContext.current
    val settingsStore = remember { (context.applicationContext as CodeLeafApplication).container.settingsStore }
    // 折り返しは diff 専用設定として永続化（ファイル閲覧の wrapByDefault とは別管理）。既定 ON。
    var wrap by remember { mutableStateOf(settingsStore.settings.value.diffWrap) }
    val hScroll = rememberScrollState()

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val maxW = maxWidth
            // 折り返しON=全幅 / OFF=各行を横スクロール可・最低でもビューポート幅（追加緑/削除赤の帯を右端まで）。
            val rowMod = if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(hScroll).widthIn(min = maxW)
            LazyColumn(Modifier.fillMaxSize()) {
                for (i in 0 until fileCount) {
                    val collapsed = isCollapsed(i)
                    headerOf(i)?.let { h ->
                        item(key = "h$i") {
                            DiffFileHeaderRow(h, collapsed, wrap, palette, { setCollapsed(i, !collapsed) }, onOpenFile)
                        }
                    }
                    if (!collapsed) bodyItems(i, wrap, rowMod, palette)
                }
                item(key = "tail") { Spacer(Modifier.height(12.dp)) }
            }
        }
        // 下部バー: 左=全ファイルの折りたたみ一括操作（2ファイル以上）/ 右=折り返し切替。
        SlimBottomBar {
            if (fileCount > 1) {
                val allCollapsed = (0 until fileCount).all { isCollapsed(it) }
                TextButton(onClick = { (0 until fileCount).forEach { setCollapsed(it, !allCollapsed) } }) {
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

/** ファイル境界バー（折りたたみトグル＋ファイルを開くボタン）。 */
@Composable
private fun DiffFileHeaderRow(
    header: DiffRow.FileHeader,
    collapsed: Boolean,
    wrap: Boolean,
    p: DiffPalette,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.headerBg)
            .clickable { onToggle() }
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = if (collapsed) "展開" else "折りたたむ",
            tint = p.headerFg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            header.displayPath,
            color = p.headerFg,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            softWrap = wrap,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(30.dp).clickable { onOpenFile(header.newPath) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "ファイルを開く",
                tint = p.headerFg,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 1ファイル分の本文行（ハンク見出し＋差分行）を LazyColumn に積む。 */
private fun LazyListScope.diffBodyItems(
    keyPrefix: String,
    rows: List<DiffRow>,
    gutterWidth: Dp,
    wrap: Boolean,
    rowMod: Modifier,
    p: DiffPalette,
) {
    items(rows.size, key = { "$keyPrefix:$it" }) { idx ->
        when (val row = rows[idx]) {
            is DiffRow.FileHeader -> Unit // ヘッダは別途描画済み
            is DiffRow.Hunk -> Text(
                text = row.text,
                color = p.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                softWrap = wrap,
                maxLines = if (wrap) Int.MAX_VALUE else 1,
                modifier = rowMod
                    .background(p.hunkBg)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
            is DiffRow.Line -> Row(
                rowMod
                    .background(when (row.kind) { '+' -> p.addBg; '-' -> p.delBg; else -> Color.Transparent })
                    .padding(vertical = 1.dp),
            ) {
                Text(
                    text = row.lineNo?.toString().orEmpty(),
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(gutterWidth).padding(end = 6.dp),
                )
                Text(
                    text = row.text.ifEmpty { " " },
                    color = p.base,
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
