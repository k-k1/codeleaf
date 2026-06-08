package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.git.GraphCommit
import jp.lazmix.codeleaf.render.GitGraphLayout
import jp.lazmix.codeleaf.render.GraphRow

private val LANE_COLORS = listOf(
    Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFFFFA726), Color(0xFF26C6DA), Color(0xFFEC407A), Color(0xFF9CCC65),
)

private fun laneColor(lane: Int) = LANE_COLORS[(lane.coerceAtLeast(0)) % LANE_COLORS.size]

private val ROW_HEIGHT = 56.dp
private val LANE_WIDTH = 18.dp
private val NODE_RADIUS = 5.dp
private val LINE_WIDTH = 2.dp
/** グラフ列が占めてよいペイン幅の上限割合(残りはメッセージ列)。lane が多いと lane 幅を縮める。 */
private const val GRAPH_MAX_FRACTION = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitGraphScreen(
    repoName: String,
    branch: String,
    loadGraph: suspend () -> List<GraphCommit>,
    onBack: () -> Unit,
    selectedSha: String? = null,
    onSelectCommit: (GraphCommit) -> Unit = {},
    /** 引っ張って更新(同期)する処理。null なら pull-to-refresh を出さない。 */
    onSync: (suspend () -> Unit)? = null,
    /** 上部バー下に引くリポ色の下線(ブラウザと統一)。 */
    accentColor: Color = Color.Transparent,
    /** 多ペイン(右端が仕切り線)なら本文の End システムバー余白を落として右の無駄空白を消す。 */
    multiPane: Boolean = false,
) {
    var commits by remember { mutableStateOf<List<GraphCommit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        error = null
        commits = runCatching { loadGraph() }.getOrElse { error = it.message; emptyList() }
    }

    val rows = remember(commits) { commits?.let { GitGraphLayout.layout(it) } ?: emptyList() }
    val laneCount = remember(rows) { GitGraphLayout.laneCount(rows) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    // 「コミットグラフ」表記は冗長なので、グラフアイコン＋リポ名＋ブランチで表す。
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_graph),
                                contentDescription = "コミットグラフ",
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(repoName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    branch,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        BackButton(onBack)
                    },
                )
                // ブラウザと同じリポ色の下線。
                HorizontalDivider(thickness = 3.dp, color = accentColor)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // 多ペインのグラフ列は右端が仕切り線なので End システムバー余白を落とす(横向きの右無駄空白対策)。
        contentWindowInsets = if (multiPane) {
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
            )
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SyncRefreshBox(
                snackbar = snackbar,
                onSync = onSync,
                // 同期後はコミットが増減しうるので再読込(rows/laneCount は commits から再算出)。
                onReload = { commits = runCatching { loadGraph() }.getOrElse { error = it.message; emptyList() } },
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // ブランチ(lane)が多いとグラフ列が広がりメッセージ列を潰すので、グラフ列は
                    // ペイン幅の最大 GRAPH_MAX_FRACTION に抑え、収まらなければ lane 幅を縮める。
                    val laneW = minOf(LANE_WIDTH, (maxWidth * GRAPH_MAX_FRACTION) / laneCount.coerceAtLeast(1))
                    when {
                        commits == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                        rows.isEmpty() -> Text("コミットがありません", Modifier.padding(16.dp))
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(rows, key = { it.commit.sha }) { row ->
                                GraphCommitRow(
                                    row = row,
                                    laneCount = laneCount,
                                    laneW = laneW,
                                    selected = row.commit.sha == selectedSha,
                                    onClick = { onSelectCommit(row.commit) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphCommitRow(row: GraphRow, laneCount: Int, laneW: Dp, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        GraphCell(row, laneCount, laneW, Modifier.width(laneW * laneCount).fillMaxHeight())
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.commit.refs.forEach { ref ->
                    RefChip(ref)
                }
                Text(
                    row.commit.shortMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    // 現ブランチ非到達(他ブランチ専用/未取り込み)は減光して区別する。
                    color = if (row.commit.inCurrentBranch) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${row.commit.author} · ${relativeTimeMillis(row.commit.committedAt.toEpochMilli())} · ${shortSha(row.commit.sha)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RefChip(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun GraphCell(row: GraphRow, laneCount: Int, laneWidth: Dp, modifier: Modifier) {
    // 中空ノードの内側を塗って下のレーン線が透けないようにする色。
    val nodeFill = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val laneW = laneWidth.toPx()
        // lane 幅を縮めたときはノードもはみ出さないよう半径を抑える。
        val r = minOf(NODE_RADIUS.toPx(), laneW * 0.4f)
        val sw = LINE_WIDTH.toPx()
        val centerY = size.height / 2f
        fun laneX(i: Int) = laneW / 2f + i * laneW

        // 上半分: 入力レーン → ノード/通過
        row.lanesAbove.forEachIndexed { j, sha ->
            if (sha == null) return@forEachIndexed
            val color = laneColor(j)
            if (sha == row.commit.sha) {
                drawLine(color, Offset(laneX(j), 0f), Offset(laneX(row.nodeLane), centerY), sw)
            } else {
                drawLine(color, Offset(laneX(j), 0f), Offset(laneX(j), centerY), sw)
            }
        }
        // 下半分: ノード/通過 → 出力レーン
        row.lanesBelow.forEachIndexed { j, sha ->
            if (sha == null) return@forEachIndexed
            val color = laneColor(j)
            val passThrough = row.lanesAbove.getOrNull(j) == sha && j != row.nodeLane
            if (passThrough) {
                drawLine(color, Offset(laneX(j), centerY), Offset(laneX(j), size.height), sw)
            } else {
                drawLine(color, Offset(laneX(row.nodeLane), centerY), Offset(laneX(j), size.height), sw)
            }
        }
        // ノード: 反映済みは塗りつぶし、現ブランチ非到達は中空リングで区別する。
        val nodeCenter = Offset(laneX(row.nodeLane), centerY)
        if (row.commit.inCurrentBranch) {
            drawCircle(laneColor(row.nodeLane), radius = r, center = nodeCenter)
        } else {
            drawCircle(nodeFill, radius = r, center = nodeCenter)
            drawCircle(laneColor(row.nodeLane), radius = r, center = nodeCenter, style = Stroke(width = sw))
        }
    }
}
