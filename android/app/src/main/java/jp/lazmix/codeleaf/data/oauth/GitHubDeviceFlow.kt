package jp.lazmix.codeleaf.data.oauth

import org.json.JSONObject

/**
 * GitHub OAuth 2.0 Device Authorization Grant（RFC 8628）の純粋なやり取り。
 * HTTP/Android 非依存（URL 組み立てとレスポンス解析のみ）なので JVM 単体でテストできる。
 *
 * OAuth App + Device Flow を採用。client_secret は不要（公開クライアント）。
 * scope=repo（private 読みに必要）だが GitHub の classic OAuth には read-only な repo scope が無く
 * read+write 全権が付与される点はトレードオフ（アプリは読むだけ）。詳細は android/DEVELOPMENT.md §10。
 */
object GitHubDeviceFlow {
    const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    const val DEFAULT_SCOPE = "repo"
    val PROVIDER = OAuthProvider.GITHUB

    /** OAuth App の user token は既定で無期限。refresh を起こさないよう遠未来の有効期限を割り当てる。 */
    const val NON_EXPIRING_SEC = 100L * 365 * 24 * 60 * 60 // ≒100年

    /** device/code 要求の form パラメータ。 */
    fun deviceCodeForm(clientId: String, scope: String = DEFAULT_SCOPE): Map<String, String> =
        mapOf("client_id" to clientId, "scope" to scope)

    /** token ポーリングの form パラメータ。 */
    fun tokenForm(clientId: String, deviceCode: String): Map<String, String> =
        mapOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to GRANT_TYPE,
        )
}

/** device/code 応答。verificationUri をブラウザで開き userCode を入力させ、deviceCode でポーリングする。 */
data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Long,
    val intervalSec: Int,
)

/** token ポーリング1回の結果。 */
sealed interface GitHubPollResult {
    /** 認可完了。 */
    data class Authorized(val tokens: OAuthTokens) : GitHubPollResult
    /** ユーザがまだ承認していない（intervalSec 待って再ポーリング）。 */
    data object Pending : GitHubPollResult
    /** ポーリングが速すぎる。newIntervalSec へ間隔を延ばして継続。 */
    data class SlowDown(val newIntervalSec: Int) : GitHubPollResult
    /** ユーザが拒否した。 */
    data object Denied : GitHubPollResult
    /** device_code が失効した（最初からやり直し）。 */
    data object Expired : GitHubPollResult
    /** 想定外の失敗（ネットワーク/HTTP/パース）。 */
    data class Failed(val error: OAuthError) : GitHubPollResult
}

/** device/code 応答(status, body)を [GitHubDeviceCode] に変換する。 */
fun parseDeviceCodeResponse(status: Int, body: String): Result<GitHubDeviceCode> {
    if (!status.isHttpSuccess()) {
        return Result.failure(OAuthException(OAuthError.Http(status, body)))
    }
    return runCatching {
        val o = JSONObject(body)
        GitHubDeviceCode(
            deviceCode = o.getString("device_code"),
            userCode = o.getString("user_code"),
            verificationUri = o.optString("verification_uri", "https://github.com/login/device"),
            expiresInSec = o.optLong("expires_in", 900L),
            intervalSec = o.optInt("interval", 5),
        )
    }.recoverCatching { throw OAuthException(OAuthError.Parse(it)) }
}

/**
 * token ポーリング応答(status, body)を [GitHubPollResult] に解釈する。
 * GitHub は未承認でも HTTP 200 + `{"error":"authorization_pending"}` を返すため body の error を見る。
 */
fun parsePollResponse(status: Int, body: String): GitHubPollResult {
    val json = runCatching { JSONObject(body) }.getOrNull()
        ?: return GitHubPollResult.Failed(OAuthError.Parse(IllegalStateException("空応答(HTTP $status)")))

    json.optString("error", "").takeIf { it.isNotEmpty() }?.let { err ->
        return when (err) {
            "authorization_pending" -> GitHubPollResult.Pending
            "slow_down" -> GitHubPollResult.SlowDown(json.optInt("interval", 0))
            "access_denied" -> GitHubPollResult.Denied
            "expired_token" -> GitHubPollResult.Expired
            else -> GitHubPollResult.Failed(OAuthError.Http(status, body))
        }
    }

    val accessToken = json.optString("access_token", "")
    if (accessToken.isEmpty()) {
        return GitHubPollResult.Failed(OAuthError.Http(status, body))
    }
    return GitHubPollResult.Authorized(
        OAuthTokens(
            accessToken = accessToken,
            refreshToken = null, // OAuth App の user token は無期限・refresh 無し
            expiresInSec = GitHubDeviceFlow.NON_EXPIRING_SEC,
            scopes = json.optString("scope", "").ifEmpty { null },
        ),
    )
}
