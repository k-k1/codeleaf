package com.k1.gitreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    status: UiStatus,
    defaultTheme: ThemeMode = ThemeMode.SYSTEM,
    onBack: () -> Unit,
    onSubmit: (NewRepo) -> Unit,
) {
    var host by remember { mutableStateOf(GitHost.GITHUB) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) } // 手動編集後は自動補完しない
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(defaultTheme) }

    val usernameRequired = host == GitHost.BITBUCKET
    val canSubmit = url.isNotBlank() && token.isNotBlank() &&
        (!usernameRequired || username.isNotBlank()) && !status.busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("リポジトリを追加") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text("ホスト")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GitHost.entries.forEachIndexed { i, h ->
                    SegmentedButton(
                        selected = host == h,
                        onClick = { host = h },
                        shape = SegmentedButtonDefaults.itemShape(i, GitHost.entries.size),
                    ) { Text(h.name.lowercase()) }
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (!nameEdited) name = repoNameFromUrl(it) // 未編集なら表示名を自動補完
                },
                label = { Text("URL (https://...)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameEdited = true },
                label = { Text("表示名 (任意・URLから自動入力)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text(if (usernameRequired) "ユーザー名 (Bitbucket: Atlassianメール・必須)" else "ユーザー名 (任意)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("トークン (PAT / API token)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = branch, onValueChange = { branch = it },
                label = { Text("ブランチ (任意・空なら既定ブランチ)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("テーマ")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = theme == t,
                        onClick = { theme = t },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(t.name.lowercase()) }
                }
            }

            Button(
                onClick = {
                    onSubmit(
                        NewRepo(
                            name = name,
                            url = url.trim(),
                            host = host,
                            username = username.trim(),
                            token = token,
                            branch = branch.trim().ifBlank { null },
                            themeMode = theme,
                        ),
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (status.busy) "clone 中..." else "保存・clone") }
        }
    }
}

/**
 * git URL からリポジトリ名を推定する。末尾スラッシュ・.git・クエリ/フラグメントを除去し、
 * 最後のパスセグメントを返す。GitHub/Bitbucket の https URL を想定。
 */
internal fun repoNameFromUrl(url: String): String {
    val cleaned = url.trim()
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
    if (cleaned.isEmpty()) return ""
    return cleaned.substringAfterLast('/').removeSuffix(".git")
}
