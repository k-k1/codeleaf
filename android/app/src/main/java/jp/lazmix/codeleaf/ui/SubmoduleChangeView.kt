package jp.lazmix.codeleaf.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.git.SubmoduleChange
import jp.lazmix.codeleaf.git.SubmoduleChangeDirection
import jp.lazmix.codeleaf.git.SubmoduleCommit

/**
 * submodule(gitlink)変更を「old→new とその間のコミット列」として表示する。
 * 生の `Subproject commit <hash>` の代わりに、submodule 側のコミットメッセージ・著者・時刻を出す。
 * direction=UNRESOLVED のときは呼び出し側が生 diff にフォールバックするので、ここには来ない想定。
 */
@Composable
fun SubmoduleChangeContent(change: SubmoduleChange, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth()
                .background(cs.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    change.path,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    chipLabel(change, context),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                rangeLabel(change, context),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSecondaryContainer,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            if (change.commits.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.submodule_no_diff_commits),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(change.commits) { c ->
                SubCommitRow(c, boundary = false)
                HorizontalDivider()
            }
            if (change.truncated) {
                item {
                    Text(
                        stringResource(R.string.submodule_more_omitted),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            change.boundary?.let { b ->
                item {
                    SubCommitRow(b, boundary = true)
                    HorizontalDivider()
                }
            }
        }
    }
}

/** チップ風ラベル: 方向＋件数。 */
private fun chipLabel(change: SubmoduleChange, context: Context): String {
    val n = change.commits.size
    return when (change.direction) {
        SubmoduleChangeDirection.FORWARD -> context.getString(R.string.submodule_n_forward, n)
        SubmoduleChangeDirection.BACKWARD -> context.getString(R.string.submodule_n_backward, n)
        SubmoduleChangeDirection.DIVERGED -> context.getString(R.string.submodule_n_diverged, n)
        SubmoduleChangeDirection.ADD -> context.getString(R.string.submodule_add)
        SubmoduleChangeDirection.REMOVE -> context.getString(R.string.submodule_remove)
        SubmoduleChangeDirection.UNRESOLVED -> ""
    }
}

private fun rangeLabel(change: SubmoduleChange, context: Context): String {
    val old = change.oldSha?.let { shortSha(it) }
    val new = change.newSha?.let { shortSha(it) }
    return when (change.direction) {
        SubmoduleChangeDirection.ADD -> context.getString(R.string.submodule_new_added, new ?: "—")
        SubmoduleChangeDirection.REMOVE -> context.getString(R.string.submodule_removed_arrow, old ?: "—")
        else -> "${old ?: "—"} → ${new ?: "—"}"
    }
}

@Composable
private fun SubCommitRow(c: SubmoduleCommit, boundary: Boolean) {
    val cs = MaterialTheme.colorScheme
    val titleColor = if (boundary) cs.onSurfaceVariant else cs.onSurface
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            if (boundary) stringResource(R.string.submodule_boundary, c.shortMessage) else c.shortMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val context = LocalContext.current
        Text(
            "${shortSha(c.sha)} · ${c.author} · ${relativeTimeMillis(c.at.toEpochMilli(), context)}",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
    }
}
