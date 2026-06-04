package com.k1.gitreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.SearchHit
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repoName: String,
    onSearch: suspend (String) -> List<SearchHit>,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // 入力が落ち着いてから(300ms デバウンス)検索する。query 変化で前回はキャンセルされる。
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searching = false
        } else {
            searching = true
            delay(300)
            results = onSearch(query)
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("検索: $repoName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ファイル内を全文検索") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                query.isBlank() -> Unit
                !searching && results.isEmpty() ->
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
                                        .clickable { onOpenFile(path) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            items(hits, key = { "${path}:${it.line}" }) { hit ->
                                Row2(hit, onClick = { onOpenFile(path) })
                            }
                            item(key = "d:$path") { HorizontalDivider() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Row2(hit: SearchHit, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 28.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            "L${hit.line}: ${hit.text}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
