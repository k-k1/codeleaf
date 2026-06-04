package com.k1.gitreader.data

import android.content.Context
import com.k1.gitreader.data.db.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Markdown/コード本文のフォント倍率。 */
enum class FontScale(val scale: Float) { SMALL(0.85f), MEDIUM(1.0f), LARGE(1.3f) }

/** アプリ全体のデフォルト設定。リポ未指定時の既定テーマ・本文フォント倍率・コード折り返し既定。 */
data class AppSettings(
    val defaultTheme: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.MEDIUM,
    val wrapByDefault: Boolean = true,
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

    private companion object {
        const val KEY_THEME = "default_theme"
        const val KEY_FONT = "font_scale"
        const val KEY_WRAP = "wrap_by_default"

        inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }
}
