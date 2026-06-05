package jp.lazmix.codeleaf.data.oauth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bitbucket OAuth フローの高レベル調停。認可URL生成・state照合・交換・refresh をまとめる。
 * トークン交換そのものは [OAuthTokenExchanger]（差し替え点）に委譲する。
 */
class BitbucketOAuthService(
    private val exchanger: OAuthTokenExchanger,
    private val session: OAuthSessionStore,
    private val clientId: String,
    private val redirectUri: String = DEFAULT_REDIRECT_URI,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 認可を開始。state を保存し、Custom Tabs で開く URL を返す。 */
    fun startAuthorization(): AuthorizationRequest {
        val req = BitbucketAuthorization.build(clientId, redirectUri, newState())
        session.begin(req.state, req.redirectUri)
        return req
    }

    /** redirect の query を受け、state 照合→コード交換→[OAuthAccount] を返す。 */
    suspend fun completeAuthorization(query: Map<String, String>): Result<OAuthAccount> =
        withContext(ioDispatcher) {
            val pending = session.pending()
                ?: return@withContext Result.failure(IllegalStateException("ログインセッションが切れています"))
            val (expectedState, redirect) = pending
            when (val r = BitbucketAuthorization.parseRedirect(query, expectedState)) {
                is RedirectResult.Success -> {
                    val result = exchanger.exchangeCode(r.code, redirect)
                        .map { OAuthAccount.fromTokens(PROVIDER, it, now()) }
                    session.clear()
                    result
                }
                RedirectResult.StateMismatch -> {
                    session.clear()
                    Result.failure(SecurityException("不正な応答です(state 不一致)"))
                }
                is RedirectResult.AuthError -> {
                    session.clear()
                    Result.failure(IllegalStateException("ログインがキャンセルされました(${r.error})"))
                }
                RedirectResult.Malformed -> {
                    session.clear()
                    Result.failure(IllegalStateException("ログインに失敗しました"))
                }
            }
        }

    /** refresh token で更新。新しい refresh token が無ければ既存を維持する。 */
    suspend fun refresh(account: OAuthAccount): Result<OAuthAccount> = withContext(ioDispatcher) {
        val rt = account.refreshToken
            ?: return@withContext Result.failure(OAuthException(OAuthError.InvalidGrant("no refresh token")))
        exchanger.refresh(rt).map { tokens ->
            OAuthAccount.fromTokens(PROVIDER, tokens, now())
                .copy(refreshToken = tokens.refreshToken ?: rt)
        }
    }

    companion object {
        const val DEFAULT_REDIRECT_URI = "codeleaf://oauth"
        const val PROVIDER = "BITBUCKET"
    }
}
