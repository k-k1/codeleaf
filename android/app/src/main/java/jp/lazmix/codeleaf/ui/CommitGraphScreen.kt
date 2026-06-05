package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitGraphScreen(
    repoName: String,
    loadGraph: suspend () -> List<GraphCommit>,
    onBack: () -> Unit,
    selectedSha: String? = null,
    onSelectCommit: (GraphCommit) -> Unit = {},
) {
    var commits by remember { mutableStateOf<List<GraphCommit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        error = null
        commits = runCatching { loadGraph() }.getOrElse { error = it.message; emptyList() }
    }

    val rows = remember(commits) { commits?.let { GitGraphLayout.layout(it) } ?: emptyList() }
    val laneCount = remember(rows) { GitGraphLayout.laneCount(rows) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("コミットグラフ: $repoName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                commits == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                error != null -> Text("読み込み失敗: $error", Modifier.padding(16.dp))
                rows.isEmpty() -> Text("コミットがありません", Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.commit.sha }) { row ->
                        GraphCommitRow(
                            row = row,
                            laneCount = laneCount,
                            selected = row.commit.sha == selectedSha,
                            onClick = { onSelectCommit(row.commit) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphCommitRow(row: GraphRow, laneCount: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        GraphCell(row, laneCount, Modifier.width(LANE_WIDTH * laneCount).fillMaxHeight())
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${row.commit.author} · ${relativeTimeMillis(row.commit.committedAt.toEpochMilli())} · ${row.commit.sha.take(7)}",
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
private fun GraphCell(row: GraphRow, laneCount: Int, modifier: Modifier) {
    Canvas(modifier) {
        val laneW = LANE_WIDTH.toPx()
        val r = NODE_RADIUS.toPx()
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
        // ノード
        drawCircle(laneColor(row.nodeLane), radius = r, center = Offset(laneX(row.nodeLane), centerY))
    }
}
