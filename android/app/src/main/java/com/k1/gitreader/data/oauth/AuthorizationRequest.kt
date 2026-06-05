package com.k1.gitreader.data.oauth

import java.security.SecureRandom
import java.util.Base64

/** 認可リクエスト。url を Custom Tabs で開き、state は redirect 照合に使う。 */
data class AuthorizationRequest(val url: String, val state: String, val redirectUri: String)

/** redirect の解析結果。 */
sealed interface RedirectResult {
    data class Success(val code: String) : RedirectResult
    data object StateMismatch : RedirectResult        // CSRF: state 不一致
    data class AuthError(val error: String) : RedirectResult // access_denied 等
    data object Malformed : RedirectResult            // code も error も無い
}

/** CSRF 用の URL-safe なランダム state を生成する。 */
fun newState(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(24)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Bitbucket Cloud OAuth 2.0 認可エンドポイントとの純粋なやり取り。
 * 認可URL組み立てと redirect クエリの解析のみ（HTTP・Android Uri 非依存でテスト可能）。
 */
object BitbucketAuthorization {
    const val AUTHORIZE_URL = "https://bitbucket.org/site/oauth2/authorize"
    const val TOKEN_URL = "https://bitbucket.org/site/oauth2/access_token"

    /** scope は consumer 側設定(repository:read)に従うため URL には付けない。 */
    fun build(clientId: String, redirectUri: String, state: String): AuthorizationRequest {
        val url = buildString {
            append(AUTHORIZE_URL)
            append("?client_id=").append(enc(clientId))
            append("&response_type=code")
            append("&state=").append(enc(state))
            append("&redirect_uri=").append(enc(redirectUri))
        }
        return AuthorizationRequest(url, state, redirectUri)
    }

    /** redirect の query パラメータ(decode 済み Map)を解析する。state 照合を含む。 */
    fun parseRedirect(params: Map<String, String>, expectedState: String): RedirectResult {
        params["error"]?.let { return RedirectResult.AuthError(it) }
        val code = params["code"] ?: return RedirectResult.Malformed
        if (params["state"] != expectedState) return RedirectResult.StateMismatch
        return RedirectResult.Success(code)
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
