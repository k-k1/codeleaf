package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.data.SearchHit
import jp.lazmix.codeleaf.data.TextFile
import jp.lazmix.codeleaf.data.searchCorpus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repoName: String,
    loadCorpus: suspend () -> List<TextFile>,
    onOpenFile: (path: String, line: Int) -> Unit,
    onBack: () -> Unit,
) {
    var corpus by remember { mutableStateOf<List<TextFile>?>(null) }
    var query by remember { mutableStateOf("") }
    var pathFilter by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    // 検索画面に入ったら本文を一度だけメモリへ読み込む（以降はメモリ内で増分検索）。
    LaunchedEffect(Unit) { corpus = loadCorpus() }

    // クエリ/絞り込み/正規表現トグル/コーパスの変化で増分検索（120ms デバウンス、別スレッド実行）。
    LaunchedEffect(query, pathFilter, regex, corpus) {
        val c = corpus
        if (c == null || query.isBlank()) {
            results = emptyList()
            error = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(120)
        val outcome = withContext(Dispatchers.Default) { searchCorpus(c, query, regex, pathFilter) }
        results = outcome.hits
        error = outcome.error
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("検索: $repoName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (regex) "正規表現で検索 (空白でAND)" else "ファイル内を全文検索 (空白でAND)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = regex,
                    onClick = { regex = !regex },
                    label = { Text(".*") },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedTextField(
                value = pathFilter,
                onValueChange = { pathFilter = it },
                label = { Text("パス/拡張子で絞り込み (任意, 例: .md / docs/)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            when {
                corpus == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                searching -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                error != null ->
                    Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                query.isBlank() -> Unit
                corpus != null && !searching && results.isEmpty() ->
                    Text("一致なし", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    val byFile = remember(results) { results.groupBy { it.relPath } }
                    Text(
                        "${results.size} 件 / ${byFile.size} ファイル",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        byFile.forEach { (path, hits) ->
                            item(key = "h:$path") {
                                Text(
                                    "📄 $path",
                                    Modifier.fillMaxWidth()
                                        .clickable { onOpenFile(path, hits.first().line) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            items(hits, key = { "${path}:${it.line}" }) { hit ->
                                Text(
                                    "L${hit.line}: ${hit.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onOpenFile(path, hit.line) }
                                        .padding(start = 28.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                                )
                            }
                            item(key = "d:$path") { HorizontalDivider() }
                        }
                    }
                }
            }
        }
    }
}
