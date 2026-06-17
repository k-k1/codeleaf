package jp.lazmix.codeleaf.data.oauth

import org.json.JSONObject
import java.io.IOException

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
