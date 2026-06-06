package jp.lazmix.codeleaf.data

import android.content.Context
import jp.lazmix.codeleaf.data.db.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Markdown/コード本文のフォント倍率。 */
enum class FontScale(val scale: Float) { SMALL(0.85f), MEDIUM(1.0f), LARGE(1.3f) }

/** 外部リンク(http/https)の開き方。BROWSER=外部ブラウザ、IN_APP=アプリ内(Custom Tabs)。 */
enum class LinkOpenMode { BROWSER, IN_APP }

/** Markdown テーブルの表示形式。INLINE=Markwon 既定の本文埋込、SCROLLABLE=横スクロール＋ヘッダ固定。 */
enum class TableMode { INLINE, SCROLLABLE }

/** ファイル一覧のアイコンセット。dir は assets 配下のフォルダ名(<dir>/<種別キー>.svg)。 */
enum class IconSet(val dir: String) {
    DEVICON("devicon"),
    MATERIAL("material"),
    VSCODE("vscode_icons"),
    SETI("seti"),
}

/** アプリ全体のデフォルト設定。 */
data class AppSettings(
    val defaultTheme: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.MEDIUM,
    val wrapByDefault: Boolean = true,
    /** diff 表示の折り返し。ファイル閲覧(wrapByDefault)とは別管理で永続化する。 */
    val diffWrap: Boolean = true,
    val linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    val showLineNumbers: Boolean = false,
    val tableMode: TableMode = TableMode.INLINE,
    val stickyHeadings: Boolean = true,
    val iconSet: IconSet = IconSet.MATERIAL,
    /** リポ一覧で選択中のグループ。空=すべて表示。 */
    val selectedGroup: String = "",
    /** 定義済みグループ名(表示順)。空グループも保持できるよう各リポの groupName とは別に持つ。 */
    val groups: List<String> = emptyList(),
)

/**
 * アプリ全体設定を SharedPreferences に保存する。現在値は StateFlow で公開し、
 * 変更時に即時反映する(暗号化不要な非機微設定のため平文)。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings = AppSettings(
        defaultTheme = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        fontScale = enumOrDefault(prefs.getString(KEY_FONT, null), FontScale.MEDIUM),
        wrapByDefault = prefs.getBoolean(KEY_WRAP, true),
        diffWrap = prefs.getBoolean(KEY_DIFFWRAP, true),
        linkOpenMode = enumOrDefault(prefs.getString(KEY_LINK, null), LinkOpenMode.IN_APP),
        showLineNumbers = prefs.getBoolean(KEY_LINENUM, false),
        tableMode = enumOrDefault(prefs.getString(KEY_TABLE, null), TableMode.INLINE),
        stickyHeadings = prefs.getBoolean(KEY_STICKY, true),
        iconSet = enumOrDefault(prefs.getString(KEY_ICONSET, null), IconSet.MATERIAL),
        selectedGroup = prefs.getString(KEY_GROUP, "") ?: "",
        groups = prefs.getString(KEY_GROUPS, null)
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    )

    fun setDefaultTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _settings.value = _settings.value.copy(defaultTheme = mode)
    }

    fun setFontScale(scale: FontScale) {
        prefs.edit().putString(KEY_FONT, scale.name).apply()
        _settings.value = _settings.value.copy(fontScale = scale)
    }

    fun setWrapByDefault(wrap: Boolean) {
        prefs.edit().putBoolean(KEY_WRAP, wrap).apply()
        _settings.value = _settings.value.copy(wrapByDefault = wrap)
    }

    fun setDiffWrap(wrap: Boolean) {
        prefs.edit().putBoolean(KEY_DIFFWRAP, wrap).apply()
        _settings.value = _settings.value.copy(diffWrap = wrap)
    }

    fun setLinkOpenMode(mode: LinkOpenMode) {
        prefs.edit().putString(KEY_LINK, mode.name).apply()
        _settings.value = _settings.value.copy(linkOpenMode = mode)
    }

    fun setShowLineNumbers(show: Boolean) {
        prefs.edit().putBoolean(KEY_LINENUM, show).apply()
        _settings.value = _settings.value.copy(showLineNumbers = show)
    }

    fun setTableMode(mode: TableMode) {
        prefs.edit().putString(KEY_TABLE, mode.name).apply()
        _settings.value = _settings.value.copy(tableMode = mode)
    }

    fun setStickyHeadings(on: Boolean) {
        prefs.edit().putBoolean(KEY_STICKY, on).apply()
        _settings.value = _settings.value.copy(stickyHeadings = on)
    }

    fun setIconSet(set: IconSet) {
        prefs.edit().putString(KEY_ICONSET, set.name).apply()
        _settings.value = _settings.value.copy(iconSet = set)
    }

    fun setSelectedGroup(group: String) {
        prefs.edit().putString(KEY_GROUP, group).apply()
        _settings.value = _settings.value.copy(selectedGroup = group)
    }

    fun setGroups(groups: List<String>) {
        val cleaned = groups.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString(KEY_GROUPS, cleaned.joinToString("\n")).apply()
        _settings.value = _settings.value.copy(groups = cleaned)
    }

    private companion object {
        const val KEY_THEME = "default_theme"
        const val KEY_FONT = "font_scale"
        const val KEY_WRAP = "wrap_by_default"
        const val KEY_DIFFWRAP = "diff_wrap"
        const val KEY_LINK = "link_open_mode"
        const val KEY_LINENUM = "show_line_numbers"
        const val KEY_TABLE = "table_mode"
        const val KEY_STICKY = "sticky_headings"
        const val KEY_ICONSET = "icon_set"
        const val KEY_GROUP = "selected_group"
        const val KEY_GROUPS = "groups"

        inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }
}
