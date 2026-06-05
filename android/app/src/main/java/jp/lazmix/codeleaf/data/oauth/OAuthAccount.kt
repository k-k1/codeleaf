package jp.lazmix.codeleaf.data.oauth

import jp.lazmix.codeleaf.data.db.AuthType
import jp.lazmix.codeleaf.data.db.GitHost
import org.json.JSONObject

/**
 * 暗号化保存する OAuth 資格情報（TokenStore に JSON 1 文字列で格納）。
 * expiresAtEpochMs は「受信時刻 + expires_in*1000」の絶対時刻。
 */
data class OAuthAccount(
    val provider: String, // "BITBUCKET"（将来 GITHUB 拡張余地）
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
    val scopes: String?,
) {
    fun toJson(): String = JSONObject().apply {
        put("provider", provider)
        put("accessToken", accessToken)
        put("refreshToken", refreshToken ?: JSONObject.NULL)
        put("expiresAtEpochMs", expiresAtEpochMs)
        put("scopes", scopes ?: JSONObject.NULL)
    }.toString()

    companion object {
        fun fromJson(s: String): OAuthAccount {
            val o = JSONObject(s)
            return OAuthAccount(
                provider = o.getString("provider"),
                accessToken = o.getString("accessToken"),
                refreshToken = o.optStringOrNull("refreshToken"),
                expiresAtEpochMs = o.getLong("expiresAtEpochMs"),
                scopes = o.optStringOrNull("scopes"),
            )
        }

        /** 交換結果＋受信時刻から保存モデルを作る。 */
        fun fromTokens(provider: String, tokens: OAuthTokens, nowMs: Long): OAuthAccount =
            OAuthAccount(
                provider = provider,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAtEpochMs = nowMs + tokens.expiresInSec * 1000,
                scopes = tokens.scopes,
            )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, null)?.takeIf { it.isNotEmpty() }

/** access token が期限切れ間近（残り < margin）かを判定する純粋関数。 */
fun needsRefresh(expiresAtEpochMs: Long, nowMs: Long, marginMs: Long = 120_000): Boolean =
    expiresAtEpochMs - nowMs < marginMs

/**
 * HTTPS Basic 認証の username をホストと認証種別から決める純粋関数。
 * - OAUTH + BITBUCKET → "x-token-auth"（Bitbucket の OAuth トークン規約）
 * - OAUTH + GITHUB    → "x-access-token"（将来用）
 * - TOKEN             → 手入力 username（空なら null→JgitClient 側で x-access-token 補完）
 */
fun gitUsernameFor(host: GitHost, authType: AuthType, manualUsername: String): String? =
    when (authType) {
        AuthType.OAUTH -> when (host) {
            GitHost.BITBUCKET -> "x-token-auth"
            GitHost.GITHUB -> "x-access-token"
        }
        AuthType.TOKEN -> manualUsername.takeIf { it.isNotBlank() }
    }
