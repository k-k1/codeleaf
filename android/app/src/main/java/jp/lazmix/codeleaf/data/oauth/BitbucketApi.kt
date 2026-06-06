package jp.lazmix.codeleaf.data.oauth

import org.json.JSONObject
import java.io.IOException

/** Bitbucket のリモートリポ1件（一覧選択用）。cloneUrl は userinfo を除いた HTTPS。 */
data class RemoteRepo(val fullName: String, val name: String, val cloneUrl: String)

/** GET(JSON) を抽象化（テストでフェイクに差し替え）。 */
interface ApiHttp {
    /** @throws IOException ネットワーク失敗時。 */
    fun getJson(url: String, authHeader: String): HttpResult
}

class HttpUrlConnectionApiHttp : ApiHttp {
    override fun getJson(url: String, authHeader: String): HttpResult {
        val conn = openJsonConnection(url, "GET", authHeader)
        try {
            return conn.readHttpResult()
        } finally {
            conn.disconnect()
        }
    }
}

/** Bitbucket Cloud REST API(読み取り)。アクセストークンは Bearer で送る。 */
class BitbucketApi(private val http: ApiHttp = HttpUrlConnectionApiHttp()) {

    /**
     * アクセス可能なリポを列挙する。
     *
     * かつての横断列挙 `GET /2.0/repositories?role=member` は Bitbucket の CHANGE-2770 で
     * **廃止され 410**。`GET /2.0/workspaces` も 410 になるため、唯一残る横断APIである
     * `GET /2.0/user/workspaces`(CHANGE-3022)で所属ワークスペースを取得し、
     * 各ワークスペースの `GET /2.0/repositories/{slug}` を集約する。
     */
    fun listRepositories(accessToken: String, maxRepos: Int = 500): Result<List<RemoteRepo>> {
        return try {
            val auth = "Bearer $accessToken"
            // 1) 所属ワークスペース(slug)を列挙。
            val slugs = ArrayList<String>()
            var wsUrl: String? = WORKSPACES_PAGE
            while (wsUrl != null) {
                val res = http.getJson(wsUrl, auth)
                if (!res.status.isHttpSuccess()) {
                    return Result.failure(OAuthException(OAuthError.Http(res.status, res.body)))
                }
                val (s, next) = parseWorkspaceSlugs(res.body)
                slugs.addAll(s)
                wsUrl = next
            }
            // 2) 各ワークスペースのリポを集約(個別の失敗はスキップして続行)。
            val out = ArrayList<RemoteRepo>()
            for (slug in slugs) {
                var url: String? = repoPageUrl(slug)
                while (url != null && out.size < maxRepos) {
                    val res = http.getJson(url, auth)
                    if (!res.status.isHttpSuccess()) break
                    val (repos, next) = parseRepoPage(res.body)
                    out.addAll(repos)
                    url = next
                }
                if (out.size >= maxRepos) break
            }
            Result.success(out.take(maxRepos))
        } catch (e: IOException) {
            Result.failure(OAuthException(OAuthError.Network(e)))
        }
    }

    private companion object {
        const val WORKSPACES_PAGE = "https://api.bitbucket.org/2.0/user/workspaces?pagelen=100"
        fun repoPageUrl(slug: String): String =
            "https://api.bitbucket.org/2.0/repositories/$slug?sort=-updated_on&pagelen=100"
    }
}

/** ワークスペース一覧ページ(JSON)から slug 一覧と次ページURLを取り出す純粋関数。
 *  /2.0/user/workspaces は workspace をネストする形・直に slug を持つ形の両方に対応する。 */
fun parseWorkspaceSlugs(body: String): Pair<List<String>, String?> {
    val o = JSONObject(body)
    val arr = o.optJSONArray("values")
    val slugs = buildList {
        for (i in 0 until (arr?.length() ?: 0)) {
            val v = arr!!.getJSONObject(i)
            val slug = (v.optJSONObject("workspace")?.optString("slug").orEmpty())
                .ifEmpty { v.optString("slug", "") }
            if (slug.isNotEmpty()) add(slug)
        }
    }
    val next = o.optString("next", "").ifEmpty { null }
    return slugs to next
}

/** リポ一覧ページ(JSON)を解析する純粋関数。戻り値 = (リポ一覧, 次ページURL or null)。 */
fun parseRepoPage(body: String): Pair<List<RemoteRepo>, String?> {
    val o = JSONObject(body)
    val arr = o.optJSONArray("values")
    val repos = buildList {
        for (i in 0 until (arr?.length() ?: 0)) {
            val r = arr!!.getJSONObject(i)
            buildRemoteRepo(r, httpsCloneHref(r))?.let { add(it) }
        }
    }
    val next = o.optString("next", "").ifEmpty { null }
    return repos to next
}

/** リポ JSON 1件 + clone URL から RemoteRepo を作る。full_name か clone URL が空なら null。GitHub/Bitbucket 共通。 */
internal fun buildRemoteRepo(r: JSONObject, cloneUrl: String?): RemoteRepo? {
    val fullName = r.optString("full_name", "")
    if (fullName.isEmpty() || cloneUrl.isNullOrEmpty()) return null
    val name = r.optString("name", "").ifEmpty { fullName.substringAfterLast('/') }
    return RemoteRepo(fullName, name, stripUserInfo(cloneUrl))
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
