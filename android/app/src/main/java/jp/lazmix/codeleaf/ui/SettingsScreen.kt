package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.lazmix.codeleaf.data.AppSettings
import jp.lazmix.codeleaf.data.FontScale
import jp.lazmix.codeleaf.data.IconSet
import jp.lazmix.codeleaf.data.LinkOpenMode
import jp.lazmix.codeleaf.data.TableMode
import jp.lazmix.codeleaf.data.db.ThemeMode

private fun themeLabel(m: ThemeMode) = when (m) {
    ThemeMode.SYSTEM -> "システム"
    ThemeMode.LIGHT -> "ライト"
    ThemeMode.DARK -> "ダーク"
}

private fun iconSetLabel(s: IconSet) = when (s) {
    IconSet.DEVICON -> "Devicon"
    IconSet.MATERIAL -> "Material"
    IconSet.VSCODE -> "VS Code"
    IconSet.SETI -> "Seti"
}

private fun fontLabel(f: FontScale) = when (f) {
    FontScale.SMALL -> "小"
    FontScale.MEDIUM -> "中"
    FontScale.LARGE -> "大"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repoCount: Int,
    onSetTheme: (ThemeMode) -> Unit,
    onSetFontScale: (FontScale) -> Unit,
    onSetWrapByDefault: (Boolean) -> Unit,
    onSetLinkOpenMode: (LinkOpenMode) -> Unit,
    onSetShowLineNumbers: (Boolean) -> Unit,
    onSetTableMode: (TableMode) -> Unit,
    onSetStickyHeadings: (Boolean) -> Unit,
    onSetIconSet: (IconSet) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("デフォルトテーマ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "一覧・設定画面の配色と、リポジトリ追加時の初期テーマに使われます。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = settings.defaultTheme == m,
                            onClick = { onSetTheme(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(themeLabel(m)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("フォントサイズ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Markdown 整形表示とコード表示の本文サイズに反映されます。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FontScale.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = settings.fontScale == f,
                            onClick = { onSetFontScale(f) },
                            shape = SegmentedButtonDefaults.itemShape(i, FontScale.entries.size),
                        ) { Text(fontLabel(f)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ファイルアイコン", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ファイル一覧の拡張子アイコンの見た目。Material/VS Code はフルカラー、Seti は単色グリフ。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    IconSet.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = settings.iconSet == s,
                            onClick = { onSetIconSet(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, IconSet.entries.size),
                        ) { Text(iconSetLabel(s)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("外部リンクの開き方", style = MaterialTheme.typography.titleMedium)
                Text(
                    "http/https リンクをアプリ内ブラウザ(Custom Tabs)か外部ブラウザのどちらで開くか。相対リンクは常にアプリ内遷移。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val items = listOf(LinkOpenMode.IN_APP to "アプリ内", LinkOpenMode.BROWSER to "外部ブラウザ")
                    items.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.linkOpenMode == mode,
                            onClick = { onSetLinkOpenMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, items.size),
                        ) { Text(label) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("コードの折り返し（既定）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "コード/Raw 表示を開いたときの初期状態。OFF は横スクロール。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.wrapByDefault, onCheckedChange = onSetWrapByDefault)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("行番号を表示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "コード/テキスト/Raw 表示で各行に行番号を付ける。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.showLineNumbers, onCheckedChange = onSetShowLineNumbers)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("見出しを上部に固定", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Markdown 整形表示で、現在地の見出し(h1>h2>h3…)を上部にスティッキー表示。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.stickyHeadings, onCheckedChange = onSetStickyHeadings)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Markdown テーブルの表示", style = MaterialTheme.typography.titleMedium)
                Text(
                    "インライン=本文に折り返し埋込（既定）。横スクロール=ヘッダ固定＋横スクロールの表。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val items = listOf(TableMode.INLINE to "インライン", TableMode.SCROLLABLE to "横スクロール")
                    items.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.tableMode == mode,
                            onClick = { onSetTableMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, items.size),
                        ) { Text(label) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("キャッシュ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "登録中のリポジトリ: ${repoCount} 件（clone データ・保存トークンを含む）",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = repoCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("キャッシュを全削除") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("キャッシュを全削除") },
            text = { Text("登録中の ${repoCount} 件のリポジトリ（clone データと保存トークン）をすべて削除します。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearCache()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("キャンセル") }
            },
        )
    }
}
