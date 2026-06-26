package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.git.BranchInfo

/** ブランチ選択シート（直近コミット順・フィルタ・現在ブランチ表示）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchSheet(
    currentBranch: String,
    loadBranches: suspend () -> List<BranchInfo>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var branches by remember { mutableStateOf<List<BranchInfo>?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        branches = runCatching { loadBranches() }.getOrDefault(emptyList())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("ブランチを選択", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("フィルタ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            val list = branches
            if (list == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val filtered = list.filter { it.name.contains(filter, ignoreCase = true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.name }) { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(b.name) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(if (b.name == currentBranch) "●" else "○")
                            Text(
                                b.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val context = LocalContext.current
                            Text(
                                relativeTimeMillis(b.committedAt.toEpochMilli(), context),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

