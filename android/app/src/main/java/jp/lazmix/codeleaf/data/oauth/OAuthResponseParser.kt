package jp.lazmix.codeleaf.data.oauth

import org.json.JSONObject

/**
 * トークンエンドポイント応答(status, body)を [OAuthTokens] に変換する純粋関数。
 * 2xx 以外は OAuthError へ。Bitbucket は 400 + `{"error":"invalid_grant",...}` で
 * refresh token 失効を返すため InvalidGrant に振り分ける。
 */
fun parseTokenResponse(status: Int, body: String): Result<OAuthTokens> {
    if (status !in 200..299) {
        val errCode = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
        return Result.failure(
            OAuthException(
                if (errCode == "invalid_grant") OAuthError.InvalidGrant(body)
                else OAuthError.Http(status, body),
            ),
        )
    }
    return runCatching {
        val o = JSONObject(body)
        OAuthTokens(
            accessToken = o.getString("access_token"),
            refreshToken = o.optString("refresh_token", "").ifEmpty { null },
            expiresInSec = o.optLong("expires_in", 3600L),
            scopes = o.optString("scopes", "").ifEmpty { null },
        )
    }.recoverCatching { throw OAuthException(OAuthError.Parse(it)) }
}
