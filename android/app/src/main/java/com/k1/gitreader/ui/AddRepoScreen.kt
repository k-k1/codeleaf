package com.k1.gitreader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.k1.gitreader.data.NewRepo
import com.k1.gitreader.data.db.AuthType
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.ThemeMode
import com.k1.gitreader.data.oauth.OAuthAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 認証方法（アコーディオンの選択肢）。 */
private enum class AuthMethod { OAUTH, TOKEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    status: UiStatus,
    defaultTheme: ThemeMode = ThemeMode.SYSTEM,
    onBack: () -> Unit,
    onSubmit: (NewRepo) -> Unit,
    bitbucketOAuthAvailable: Boolean = false,
    onStartBitbucketOAuth: () -> Unit = {},
    oauthResult: Flow<Result<OAuthAccount>> = emptyFlow(),
) {
    var host by remember { mutableStateOf(GitHost.GITHUB) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) } // 手動編集後は自動補完しない
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(defaultTheme) }
    var oauth by remember { mutableStateOf<OAuthAccount?>(null) }
    var oauthError by remember { mutableStateOf<String?>(null) }
    // 認証方法。OAuth が使える Bitbucket では既定 OAUTH、それ以外は TOKEN。
    var authMethod by remember { mutableStateOf(AuthMethod.OAUTH) }

    // redirect Activity が交換した OAuth 結果を受け取る。
    LaunchedEffect(Unit) {
        oauthResult.collect { result ->
            result.fold(
                onSuccess = { oauth = it; oauthError = null },
                onFailure = { oauthError = it.message ?: "ログインに失敗しました" },
            )
        }
    }

    // OAuth を選べる(=アコーディオン表示する)のは Bitbucket かつ OAuth 設定済みのときだけ。
    val showAccordion = host == GitHost.BITBUCKET && bitbucketOAuthAvailable
    val effectiveMethod = if (showAccordion) authMethod else AuthMethod.TOKEN
    val usernameRequired = host == GitHost.BITBUCKET && effectiveMethod == AuthMethod.TOKEN
    val canSubmit = url.isNotBlank() && !status.busy && when (effectiveMethod) {
        AuthMethod.OAUTH -> oauth != null
        AuthMethod.TOKEN -> token.isNotBlank() && (!usernameRequired || username.isNotBlank())
    }

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
                        onClick = {
                            host = h
                            // ホストを離れたら OAuth ログイン状態を破棄（host とトークンの不整合を防ぐ）
                            if (h != GitHost.BITBUCKET) { oauth = null; oauthError = null }
                            // Bitbucket(OAuth 可)に入ったら既定 OAuth、それ以外はトークン。
                            authMethod = if (h == GitHost.BITBUCKET && bitbucketOAuthAvailable) {
                                AuthMethod.OAUTH
                            } else {
                                AuthMethod.TOKEN
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, GitHost.entries.size),
                    ) { Text(h.name.lowercase()) }
                }
            }

            // 認証方法（ホストのタブ直下・上部に配置）。Bitbucket かつ OAuth 設定済みのときだけ
            // アコーディオンで OAuth / トークンを選ばせる。それ以外はトークン入力のみ。
            Text("認証方法")
            if (showAccordion) {
                AuthMethodAccordion(
                    method = authMethod,
                    onSelect = { authMethod = it },
                    oauthContent = {
                        OAuthPanel(loggedIn = oauth != null, error = oauthError, onStart = onStartBitbucketOAuth)
                    },
                    tokenContent = {
                        ManualAuthFields(
                            username = username, onUsername = { username = it },
                            token = token, onToken = { token = it },
                            usernameRequired = true, // token 方式の Bitbucket は username 必須
                        )
                    },
                )
            } else {
                if (host == GitHost.BITBUCKET && !bitbucketOAuthAvailable) {
                    Text(
                        "OAuth ログインは未設定です（local.properties に client_id/secret を設定すると有効）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ManualAuthFields(
                    username = username, onUsername = { username = it },
                    token = token, onToken = { token = it },
                    usernameRequired = usernameRequired,
                )
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
                    val acct = oauth
                    onSubmit(
                        if (effectiveMethod == AuthMethod.OAUTH && acct != null) {
                            NewRepo(
                                name = name,
                                url = url.trim(),
                                host = host,
                                username = "",
                                token = "",
                                branch = null,
                                themeMode = theme,
                                authType = AuthType.OAUTH,
                                oauth = acct,
                            )
                        } else {
                            NewRepo(
                                name = name,
                                url = url.trim(),
                                host = host,
                                username = username.trim(),
                                token = token,
                                branch = null, // 既定ブランチを使用(後でブランチ切替で変更可)
                                themeMode = theme,
                            )
                        },
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (status.busy) "clone 中..." else "保存・clone") }
        }
    }
}

/** 認証方法アコーディオン。選んだ項目だけ展開し、他は折りたたむ（単一展開）。 */
@Composable
private fun AuthMethodAccordion(
    method: AuthMethod,
    onSelect: (AuthMethod) -> Unit,
    oauthContent: @Composable () -> Unit,
    tokenContent: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        AccordionItem(
            title = "Bitbucket でログイン（OAuth）",
            selected = method == AuthMethod.OAUTH,
            onClick = { onSelect(AuthMethod.OAUTH) },
            content = oauthContent,
        )
        HorizontalDivider()
        AccordionItem(
            title = "トークンを入力",
            selected = method == AuthMethod.TOKEN,
            onClick = { onSelect(AuthMethod.TOKEN) },
            content = tokenContent,
        )
    }
}

@Composable
private fun AccordionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(title, Modifier.weight(1f))
            Icon(
                if (selected) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        if (selected) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/** OAuth パネル（未ログインはボタン、ログイン済みは状態＋再ログイン）。 */
@Composable
private fun OAuthPanel(loggedIn: Boolean, error: String?, onStart: () -> Unit) {
    if (!loggedIn) {
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Bitbucket でログイン")
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓ ログイン済み", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onStart) { Text("再ログイン") }
        }
    }
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** 手入力の username/token フィールド（TOKEN 認証）。 */
@Composable
private fun ManualAuthFields(
    username: String,
    onUsername: (String) -> Unit,
    token: String,
    onToken: (String) -> Unit,
    usernameRequired: Boolean,
) {
    OutlinedTextField(
        value = username, onValueChange = onUsername,
        label = { Text(if (usernameRequired) "ユーザー名 (Bitbucket: Atlassianメール・必須)" else "ユーザー名 (任意)") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = token, onValueChange = onToken,
        label = { Text("トークン (PAT / API token)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
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
