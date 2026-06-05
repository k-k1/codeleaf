package jp.lazmix.codeleaf.data.oauth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * GitHub Device Flow の高レベル調停。device/code 取得とトークンのポーリングを担う。
 * HTTP は [TokenHttp]（client_secret 不要なので Authorization ヘッダは付けない）。
 *
 * Bitbucket と違い redirect/Custom Tabs deep link は使わない（user_code 表示＋ポーリング方式）。
 * よって [OAuthTokenExchanger]（exchangeCode/refresh）には載らず独立クラスにしている。
 */
class GitHubDeviceFlowService(
    private val clientId: String,
    private val http: TokenHttp = HttpUrlConnectionTokenHttp(),
    private val scope: String = GitHubDeviceFlow.DEFAULT_SCOPE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    /** ポーリング間隔の待機（テストで差し替え）。 */
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** device/code を要求。成功すると user_code・verification_uri 等を返す。 */
    suspend fun requestDeviceCode(): Result<GitHubDeviceCode> = withContext(ioDispatcher) {
        try {
            val res = http.postForm(
                GitHubDeviceFlow.DEVICE_CODE_URL,
                "",
                GitHubDeviceFlow.deviceCodeForm(clientId, scope),
            )
            parseDeviceCodeResponse(res.status, res.body)
        } catch (e: IOException) {
            Result.failure(OAuthException(OAuthError.Network(e)))
        }
    }

    /**
     * ユーザの承認をポーリングで待つ。Pending/SlowDown は間隔を空けて継続し、
     * 承認で [OAuthAccount] を返す。拒否/失効/期限超過は失敗。
     */
    suspend fun pollForToken(code: GitHubDeviceCode): Result<OAuthAccount> = withContext(ioDispatcher) {
        var intervalSec = code.intervalSec.coerceAtLeast(1)
        val deadline = now() + code.expiresInSec * 1000
        while (true) {
            if (now() >= deadline) {
                return@withContext Result.failure(IllegalStateException("コードの有効期限が切れました。もう一度ログインしてください"))
            }
            sleep(intervalSec * 1000L)
            val result = try {
                val res = http.postForm(
                    GitHubDeviceFlow.TOKEN_URL,
                    "",
                    GitHubDeviceFlow.tokenForm(clientId, code.deviceCode),
                )
                parsePollResponse(res.status, res.body)
            } catch (e: IOException) {
                GitHubPollResult.Failed(OAuthError.Network(e))
            }
            when (result) {
                is GitHubPollResult.Authorized ->
                    return@withContext Result.success(
                        OAuthAccount.fromTokens(GitHubDeviceFlow.PROVIDER, result.tokens, now()),
                    )
                GitHubPollResult.Pending -> Unit // 同じ間隔で再試行
                is GitHubPollResult.SlowDown ->
                    intervalSec = result.newIntervalSec.coerceAtLeast(intervalSec + 5)
                GitHubPollResult.Denied ->
                    return@withContext Result.failure(IllegalStateException("ログインが拒否されました"))
                GitHubPollResult.Expired ->
                    return@withContext Result.failure(IllegalStateException("コードの有効期限が切れました。もう一度ログインしてください"))
                is GitHubPollResult.Failed ->
                    return@withContext Result.failure(OAuthException(result.error))
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(IllegalStateException("unreachable"))
    }
}
