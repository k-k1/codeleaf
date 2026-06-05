package com.k1.gitreader.data.oauth

/**
 * OAuth トークンの交換結果。refresh grant では refreshToken が返らない実装もあるため nullable。
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
    val scopes: String?,
)

/** OAuth 失敗の種別。InvalidGrant は refresh token の失効/revoke を表し再ログインが要る。 */
sealed interface OAuthError {
    data class Network(val cause: Throwable) : OAuthError
    data class Http(val status: Int, val body: String) : OAuthError
    data class InvalidGrant(val body: String) : OAuthError
    data class Parse(val cause: Throwable) : OAuthError
}

/** [OAuthError] を運ぶ例外（Result.failure に載せる）。 */
class OAuthException(val error: OAuthError) : Exception(
    when (error) {
        is OAuthError.Network -> "ネットワークエラー: ${error.cause.message}"
        is OAuthError.Http -> "認証サーバエラー (HTTP ${error.status})"
        is OAuthError.InvalidGrant -> "再認証が必要です"
        is OAuthError.Parse -> "認証応答を解釈できません"
    },
)

/**
 * 認可コード↔トークン交換 と refresh のみを担う抽象。
 *
 * このアプリで client_secret を端末同梱している部分を 1 箇所に隔離する目的を持つ。
 * 将来「バックエンド代行」(secret をサーバ側に置く) へ移すときは、この実装だけ差し替える。
 * 実装はブロッキング I/O（呼び出し側で Dispatchers.IO に載せる）。
 */
interface OAuthTokenExchanger {
    fun exchangeCode(code: String, redirectUri: String): Result<OAuthTokens>
    fun refresh(refreshToken: String): Result<OAuthTokens>
}
