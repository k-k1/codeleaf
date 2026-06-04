package com.k1.gitreader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                else -> Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    text.lineSequence().forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            color = colorForLine(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private fun colorForLine(line: String): Color = when {
    line.startsWith("@@") -> Color(0xFF1E88E5)
    line.startsWith("+++") || line.startsWith("---") -> Color(0xFF9E9E9E)
    line.startsWith("diff ") || line.startsWith("index ") -> Color(0xFF9E9E9E)
    line.startsWith("+") -> Color(0xFF2E7D32)
    line.startsWith("-") -> Color(0xFFC62828)
    else -> Color.Unspecified
}
