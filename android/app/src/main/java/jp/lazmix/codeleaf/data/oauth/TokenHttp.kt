package jp.lazmix.codeleaf.data.oauth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val status: Int, val body: String)

/** OAuth / REST 共通の接続タイムアウト(ms)。 */
const val HTTP_TIMEOUT_MS = 15_000

/** HTTP ステータスが 2xx(成功)か。 */
fun Int.isHttpSuccess(): Boolean = this in 200..299

/** メソッド・タイムアウト・Accept・(空でなければ)Authorization を設定した接続を開く。 */
internal fun openJsonConnection(url: String, method: String, authHeader: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = HTTP_TIMEOUT_MS
        readTimeout = HTTP_TIMEOUT_MS
        if (authHeader.isNotEmpty()) setRequestProperty("Authorization", authHeader)
        setRequestProperty("Accept", "application/json")
    }

/** 応答コードに応じて input/error ストリームから本文を読み HttpResult を返す(2xx 以外でも本文を拾う)。 */
internal fun HttpURLConnection.readHttpResult(): HttpResult {
    val status = responseCode
    val stream = if (status.isHttpSuccess()) inputStream else errorStream
    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    return HttpResult(status, body)
}

/** トークンエンドポイントへの form POST を抽象化（テストでフェイクに差し替える）。 */
interface TokenHttp {
    /**
     * @param basicAuth Base64 値。空文字なら Authorization ヘッダを付けない
     *   （GitHub Device Flow は client_id を body に載せ、認証ヘッダ無しで叩く）。
     * @throws IOException ネットワーク失敗時。
     */
    fun postForm(url: String, basicAuth: String, form: Map<String, String>): HttpResult
}

/** OkHttp を足さず HttpURLConnection で実装（依存を絞る方針）。 */
class HttpUrlConnectionTokenHttp : TokenHttp {
    override fun postForm(url: String, basicAuth: String, form: Map<String, String>): HttpResult {
        val auth = if (basicAuth.isNotEmpty()) "Basic $basicAuth" else ""
        val conn = openJsonConnection(url, "POST", auth).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val payload = form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            return conn.readHttpResult()
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
