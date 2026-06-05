package com.k1.gitreader.data.oauth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val status: Int, val body: String)

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
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            if (basicAuth.isNotEmpty()) setRequestProperty("Authorization", "Basic $basicAuth")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = form.entries.joinToString("&") { (k, v) ->
                "${enc(k)}=${enc(v)}"
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            // Bitbucket は 400 でも errorStream に JSON を返す。
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(status, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
