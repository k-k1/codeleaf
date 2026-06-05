package jp.lazmix.codeleaf.data.oauth

import java.io.IOException
import java.util.Base64

/**
 * Bitbucket Cloud のトークン交換実装。client_secret を Basic 認証で送る。
 *
 * client_secret 同梱の唯一の利用点。将来「バックエンド代行」へ移す場合は
 * この [OAuthTokenExchanger] 実装を差し替える（呼び出し側は無改修）。
 */
class BitbucketOAuthTokenExchanger(
    private val clientId: String,
    private val clientSecret: String,
    private val http: TokenHttp = HttpUrlConnectionTokenHttp(),
) : OAuthTokenExchanger {

    override fun exchangeCode(code: String, redirectUri: String): Result<OAuthTokens> = post(
        mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
        ),
    )

    override fun refresh(refreshToken: String): Result<OAuthTokens> = post(
        mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        ),
    )

    private fun post(form: Map<String, String>): Result<OAuthTokens> =
        try {
            val res = http.postForm(BitbucketAuthorization.TOKEN_URL, basicAuth(), form)
            parseTokenResponse(res.status, res.body)
        } catch (e: IOException) {
            Result.failure(OAuthException(OAuthError.Network(e)))
        }

    private fun basicAuth(): String =
        Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8))
}
