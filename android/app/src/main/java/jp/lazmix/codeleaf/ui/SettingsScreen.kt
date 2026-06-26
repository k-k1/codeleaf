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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import jp.lazmix.codeleaf.BuildConfig
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.data.AppSettings
import jp.lazmix.codeleaf.data.FileNameDisplay
import jp.lazmix.codeleaf.data.FontScale
import jp.lazmix.codeleaf.data.IconSet
import jp.lazmix.codeleaf.data.LinkOpenMode
import jp.lazmix.codeleaf.data.TableMode
import jp.lazmix.codeleaf.data.db.ThemeMode

@Composable
private fun themeLabel(m: ThemeMode) = stringResource(
    when (m) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

// アイコンセットは固有名(ブランド)なので翻訳しない。
private fun iconSetLabel(s: IconSet) = when (s) {
    IconSet.DEVICON -> "Devicon"
    IconSet.MATERIAL -> "Material"
    IconSet.VSCODE -> "VS Code"
    IconSet.SETI -> "Seti"
}

@Composable
private fun fontLabel(f: FontScale) = stringResource(
    when (f) {
        FontScale.SMALL -> R.string.font_small
        FontScale.MEDIUM -> R.string.font_medium
        FontScale.LARGE -> R.string.font_large
    },
)

/**
 * UI 言語の選択肢。SYSTEM は端末言語に追従(空ロケール)、JA/EN は明示指定。
 * 永続化は AppCompatDelegate(API33+ は OS、未満は appcompat バックポート)が担うため
 * SettingsStore には保持しない。言語名は各言語自身で表示するのが通例なので JA/EN は固定表記。
 */
// endonym = 各言語の自称表記(ピッカーに表示。SYSTEM だけ null でリソース解決)。
// 言語追加はこの enum に1行足すだけ(ピッカーは entries を回す)＋ values-XX/ ＋ locales_config。
private enum class UiLanguage(val tag: String?, val endonym: String?) {
    SYSTEM(null, null),
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    KOREAN("ko", "한국어"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    VIETNAMESE("vi", "Tiếng Việt"),
}

/** 現在の適用ロケールから選択中の UiLanguage を判定する。zh は地域/字種で簡体/繁体を分ける。 */
private fun currentUiLanguage(): UiLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return UiLanguage.SYSTEM
    val tag = locales[0]?.toLanguageTag()?.lowercase() ?: return UiLanguage.SYSTEM
    return when {
        tag.startsWith("ja") -> UiLanguage.JAPANESE
        tag.startsWith("es") -> UiLanguage.SPANISH
        tag.startsWith("ko") -> UiLanguage.KOREAN
        tag.startsWith("zh") && (tag.contains("hant") || tag.contains("tw") || tag.contains("hk") || tag.contains("mo")) -> UiLanguage.CHINESE_TRADITIONAL
        tag.startsWith("zh") -> UiLanguage.CHINESE_SIMPLIFIED
        tag.startsWith("vi") -> UiLanguage.VIETNAMESE
        tag.startsWith("en") -> UiLanguage.ENGLISH
        else -> UiLanguage.SYSTEM
    }
}

/** 言語を適用する。空タグ=システム既定。呼び出し後 appcompat が Activity を再生成する。 */
private fun applyUiLanguage(lang: UiLanguage) {
    val list = lang.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(list)
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
        // testTag でラベル別に特定可能にする(設定スイッチはセマンティクス上フラットに並び順依存になるため)。
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("settingSwitch:$title"),
        )
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
    onSetShowCommitInfo: (Boolean) -> Unit,
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
                title = { Text(stringResource(R.string.settings_title)) },
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
            SettingsSection(stringResource(R.string.settings_section_display)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_theme_desc),
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
                    Text(stringResource(R.string.settings_font_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_font_desc),
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
                    Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // 選択中ロケールは AppCompatDelegate から都度読む(切替時に Activity 再生成→再 compose で反映)。
                    // 言語が増えるとセグメントは横に収まらないため、標準のドロップダウンフィールドで選ばせる。
                    // ExposedDropdownMenu はアンカー(フィールド)幅にメニューが揃うので、左寄せの細い一覧にならない。
                    val current = currentUiLanguage()
                    val systemLabel = stringResource(R.string.language_system)
                    DropdownSelectField(value = current.endonym ?: systemLabel) { dismiss ->
                        UiLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.endonym ?: systemLabel) },
                                onClick = { dismiss(); applyUiLanguage(lang) },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(stringResource(R.string.settings_section_filelist)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_iconset_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_iconset_desc),
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
                    Text(stringResource(R.string.settings_filename_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_filename_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val items = listOf(
                            FileNameDisplay.WRAP to stringResource(R.string.filename_wrap),
                            FileNameDisplay.MIDDLE_ELLIPSIS to stringResource(R.string.filename_middle),
                            FileNameDisplay.END_ELLIPSIS to stringResource(R.string.filename_end),
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
                    title = stringResource(R.string.settings_collapse_title),
                    description = stringResource(R.string.settings_collapse_desc),
                    checked = settings.collapseFolders,
                    onCheckedChange = onSetCollapseFolders,
                )

                SwitchSetting(
                    title = stringResource(R.string.settings_commitinfo_title),
                    description = stringResource(R.string.settings_commitinfo_desc),
                    checked = settings.showCommitInfo,
                    onCheckedChange = onSetShowCommitInfo,
                )
            }

            HorizontalDivider()

            SettingsSection(stringResource(R.string.settings_section_repo)) {
                SwitchSetting(
                    title = stringResource(R.string.settings_restore_title),
                    description = stringResource(R.string.settings_restore_desc),
                    checked = settings.restoreLastPosition,
                    onCheckedChange = onSetRestoreLastPosition,
                )
            }

            HorizontalDivider()

            SettingsSection(stringResource(R.string.settings_section_viewer)) {
                SwitchSetting(
                    title = stringResource(R.string.settings_wrap_title),
                    description = stringResource(R.string.settings_wrap_desc),
                    checked = settings.wrapByDefault,
                    onCheckedChange = onSetWrapByDefault,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_diffwrap_title),
                    description = stringResource(R.string.settings_diffwrap_desc),
                    checked = settings.diffWrap,
                    onCheckedChange = onSetDiffWrap,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_linenum_title),
                    description = stringResource(R.string.settings_linenum_desc),
                    checked = settings.showLineNumbers,
                    onCheckedChange = onSetShowLineNumbers,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_selecttext_title),
                    description = stringResource(R.string.settings_selecttext_desc),
                    checked = settings.selectByDefault,
                    onCheckedChange = onSetSelectByDefault,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_sticky_title),
                    description = stringResource(R.string.settings_sticky_desc),
                    checked = settings.stickyHeadings,
                    onCheckedChange = onSetStickyHeadings,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_table_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_table_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val items = listOf(
                            TableMode.INLINE to stringResource(R.string.table_inline),
                            TableMode.SCROLLABLE to stringResource(R.string.table_scrollable),
                        )
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

            SettingsSection(stringResource(R.string.settings_section_links)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_link_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_link_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val items = listOf(
                            LinkOpenMode.IN_APP to stringResource(R.string.link_inapp),
                            LinkOpenMode.BROWSER to stringResource(R.string.link_browser),
                        )
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

            SettingsSection(stringResource(R.string.settings_section_data)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_cache_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_cache_desc, repoCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = repoCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_clearcache)) }
                }
            }

            HorizontalDivider()

            SettingsSection(stringResource(R.string.settings_section_about)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_version_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "CodeLeaf ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.settings_build_line,
                            BuildConfig.BUILD_TIME,
                            BuildConfig.GIT_SHA,
                            BuildConfig.BUILD_TYPE,
                        ),
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
                    Text(stringResource(R.string.settings_licenses))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clearcache)) },
            text = { Text(stringResource(R.string.settings_clearcache_confirm, repoCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearCache()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
