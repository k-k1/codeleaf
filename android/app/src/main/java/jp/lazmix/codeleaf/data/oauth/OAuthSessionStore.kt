package jp.lazmix.codeleaf.data.oauth

import android.content.Context

/**
 * 認可開始〜redirect 受信の間だけ state/redirectUri を保持する。
 * state は短命の CSRF 値なので平文 SharedPreferences で可。プロセス再生成耐性のため永続化する。
 */
class OAuthSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("oauth_session", Context.MODE_PRIVATE)

    fun begin(state: String, redirectUri: String) {
        prefs.edit().putString(KEY_STATE, state).putString(KEY_REDIRECT, redirectUri).apply()
    }

    /** 進行中の (state, redirectUri)。無ければ null（= セッション切れ）。 */
    fun pending(): Pair<String, String>? {
        val state = prefs.getString(KEY_STATE, null) ?: return null
        val redirect = prefs.getString(KEY_REDIRECT, null) ?: return null
        return state to redirect
    }

    fun clear() {
        prefs.edit().remove(KEY_STATE).remove(KEY_REDIRECT).apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_REDIRECT = "redirect_uri"
    }
}
