package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import jp.lazmix.codeleaf.BuildConfig
import jp.lazmix.codeleaf.data.AppSettings
import jp.lazmix.codeleaf.data.FileNameDisplay
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

/** 設定の1セクション。見出し(primary色)＋配下項目を一定間隔で並べる。 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** タイトル＋説明＋オン/オフスイッチの1行設定。 */
@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repoCount: Int,
    onSetTheme: (ThemeMode) -> Unit,
    onSetFontScale: (FontScale) -> Unit,
    onSetWrapByDefault: (Boolean) -> Unit,
    onSetDiffWrap: (Boolean) -> Unit,
    onSetLinkOpenMode: (LinkOpenMode) -> Unit,
    onSetShowLineNumbers: (Boolean) -> Unit,
    onSetTableMode: (TableMode) -> Unit,
    onSetStickyHeadings: (Boolean) -> Unit,
    onSetCollapseFolders: (Boolean) -> Unit,
    onSetFileNameDisplay: (FileNameDisplay) -> Unit,
    onSetIconSet: (IconSet) -> Unit,
    onSetRestoreLastPosition: (Boolean) -> Unit,
    onSetSelectByDefault: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("表示・テーマ") {
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
            }

            HorizontalDivider()

            SettingsSection("ファイル一覧") {
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
                    Text("ファイル名の表示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "長い名前の扱い。折り返し=全文を複数行。中央省略=先頭と末尾を残す。末尾省略=末尾を…。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val items = listOf(
                            FileNameDisplay.WRAP to "折り返し",
                            FileNameDisplay.MIDDLE_ELLIPSIS to "中央省略",
                            FileNameDisplay.END_ELLIPSIS to "末尾省略",
                        )
                        items.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.fileNameDisplay == mode,
                                onClick = { onSetFileNameDisplay(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, items.size),
                            ) { Text(label) }
                        }
                    }
                }

                SwitchSetting(
                    title = "単一フォルダを畳む",
                    description = "中身が1つの子フォルダだけの階層を src/main/java のようにまとめ、辿る手間を省く。",
                    checked = settings.collapseFolders,
                    onCheckedChange = onSetCollapseFolders,
                )
            }

            HorizontalDivider()

            SettingsSection("リポジトリ") {
                SwitchSetting(
                    title = "前回の位置を復元",
                    description = "リポを開いたとき、前回いたフォルダと開いていたファイルを復元します。" +
                        "OFF なら常にトップ・ファイルを開いていない状態で開きます。",
                    checked = settings.restoreLastPosition,
                    onCheckedChange = onSetRestoreLastPosition,
                )
            }

            HorizontalDivider()

            SettingsSection("ビューア") {
                SwitchSetting(
                    title = "コードの折り返し（既定）",
                    description = "コード/Raw 表示を開いたときの初期状態。OFF は横スクロール。",
                    checked = settings.wrapByDefault,
                    onCheckedChange = onSetWrapByDefault,
                )
                SwitchSetting(
                    title = "diff の折り返し（既定）",
                    description = "差分・コミット・履歴の diff 表示を開いたときの初期状態。OFF は横スクロール。",
                    checked = settings.diffWrap,
                    onCheckedChange = onSetDiffWrap,
                )
                SwitchSetting(
                    title = "行番号を表示",
                    description = "コード/テキスト/Raw 表示で各行に行番号を付ける。",
                    checked = settings.showLineNumbers,
                    onCheckedChange = onSetShowLineNumbers,
                )
                SwitchSetting(
                    title = "テキスト選択を既定で有効",
                    description = "本文を選択してコピーできる状態でビューアを開く。各ビューアの ⋮ でも個別に切替できます。" +
                        "ON の間は長押しメモ追加が無効になります。",
                    checked = settings.selectByDefault,
                    onCheckedChange = onSetSelectByDefault,
                )
                SwitchSetting(
                    title = "見出しを上部に固定",
                    description = "Markdown 整形表示で、現在地の見出し(h1>h2>h3…)を上部にスティッキー表示。",
                    checked = settings.stickyHeadings,
                    onCheckedChange = onSetStickyHeadings,
                )

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
            }

            HorizontalDivider()

            SettingsSection("リンク") {
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
            }

            HorizontalDivider()

            SettingsSection("データ") {
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

            HorizontalDivider()

            SettingsSection("このアプリ") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("バージョン", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "CodeLeaf ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "ビルド: ${BuildConfig.BUILD_TIME} · ${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_TYPE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "ID: ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onLicenses, modifier = Modifier.fillMaxWidth()) {
                    Text("オープンソースライセンス")
                }
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
