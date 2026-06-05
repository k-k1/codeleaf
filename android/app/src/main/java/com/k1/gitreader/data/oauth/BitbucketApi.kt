package com.k1.gitreader.data.oauth

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Bitbucket のリモートリポ1件（一覧選択用）。cloneUrl は userinfo を除いた HTTPS。 */
data class RemoteRepo(val fullName: String, val name: String, val cloneUrl: String)

/** GET(JSON) を抽象化（テストでフェイクに差し替え）。 */
interface ApiHttp {
    /** @throws IOException ネットワーク失敗時。 */
    fun getJson(url: String, authHeader: String): HttpResult
}

class HttpUrlConnectionApiHttp : ApiHttp {
    override fun getJson(url: String, authHeader: String): HttpResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", authHeader)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(status, body)
        } finally {
            conn.disconnect()
        }
    }
}

/** Bitbucket Cloud REST API(読み取り)。アクセストークンは Bearer で送る。 */
class BitbucketApi(private val http: ApiHttp = HttpUrlConnectionApiHttp()) {

    /** アクセス可能なリポを列挙（next ページを追従、上限 maxRepos）。 */
    fun listRepositories(accessToken: String, maxRepos: Int = 500): Result<List<RemoteRepo>> {
        return try {
            val out = ArrayList<RemoteRepo>()
            var url: String? = FIRST_PAGE
            while (url != null && out.size < maxRepos) {
                val res = http.getJson(url, "Bearer $accessToken")
                if (res.status !in 200..299) {
                    return Result.failure(OAuthException(OAuthError.Http(res.status, res.body)))
                }
                val (repos, next) = parseRepoPage(res.body)
                out.addAll(repos)
                url = next
            }
            Result.success(out.take(maxRepos))
        } catch (e: IOException) {
            Result.failure(OAuthException(OAuthError.Network(e)))
        }
    }

    private companion object {
        const val FIRST_PAGE =
            "https://api.bitbucket.org/2.0/repositories?role=member&sort=-updated_on&pagelen=100"
    }
}

/** リポ一覧ページ(JSON)を解析する純粋関数。戻り値 = (リポ一覧, 次ページURL or null)。 */
fun parseRepoPage(body: String): Pair<List<RemoteRepo>, String?> {
    val o = JSONObject(body)
    val arr = o.optJSONArray("values")
    val repos = buildList {
        for (i in 0 until (arr?.length() ?: 0)) {
            val r = arr!!.getJSONObject(i)
            val fullName = r.optString("full_name", "")
            val name = r.optString("name", "").ifEmpty { fullName.substringAfterLast('/') }
            val href = httpsCloneHref(r)
            if (fullName.isNotEmpty() && href != null) {
                add(RemoteRepo(fullName, name, stripUserInfo(href)))
            }
        }
    }
    val next = o.optString("next", "").ifEmpty { null }
    return repos to next
}

private fun httpsCloneHref(repo: JSONObject): String? {
    val clone = repo.optJSONObject("links")?.optJSONArray("clone") ?: return null
    for (i in 0 until clone.length()) {
        val link = clone.getJSONObject(i)
        if (link.optString("name") == "https") {
            return link.optString("href", "").ifEmpty { null }
        }
    }
    return null
}

/** `https://user@host/...` の userinfo を除去する。 */
fun stripUserInfo(url: String): String = url.replace(Regex("://[^/@]*@"), "://")

/** clone URL を比較用に正規化（userinfo/.git/末尾スラッシュ除去・小文字化）。 */
fun normalizeRepoUrl(url: String): String {
    var s = url.trim().substringBefore('?').substringBefore('#')
    s = stripUserInfo(s).trimEnd('/')
    if (s.endsWith(".git")) s = s.dropLast(4)
    return s.lowercase()
}
