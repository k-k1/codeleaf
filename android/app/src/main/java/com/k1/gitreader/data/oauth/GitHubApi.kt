package com.k1.gitreader.data.oauth

import org.json.JSONArray
import java.io.IOException

/** GitHub REST API(読み取り)。OAuth user token を Bearer で送り、clone 可能なリポを列挙する。 */
class GitHubApi(private val http: ApiHttp = HttpUrlConnectionApiHttp()) {

    /** 認証ユーザがアクセスできるリポを列挙（page を追従、上限 maxRepos）。 */
    fun listRepositories(accessToken: String, maxRepos: Int = 500): Result<List<RemoteRepo>> {
        return try {
            val out = ArrayList<RemoteRepo>()
            var page = 1
            while (out.size < maxRepos) {
                val url = "$REPOS_URL&page=$page"
                val res = http.getJson(url, "Bearer $accessToken")
                if (res.status !in 200..299) {
                    return Result.failure(OAuthException(OAuthError.Http(res.status, res.body)))
                }
                val repos = parseGitHubRepoPage(res.body)
                if (repos.isEmpty()) break
                out.addAll(repos)
                if (repos.size < PAGE_SIZE) break // 最終ページ
                page++
            }
            Result.success(out.take(maxRepos))
        } catch (e: IOException) {
            Result.failure(OAuthException(OAuthError.Network(e)))
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val REPOS_URL =
            "https://api.github.com/user/repos?per_page=$PAGE_SIZE&sort=updated&affiliation=owner,collaborator,organization_member"
    }
}

/** /user/repos の1ページ(JSON 配列)を解析する純粋関数。 */
fun parseGitHubRepoPage(body: String): List<RemoteRepo> {
    val arr = JSONArray(body)
    return buildList {
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val fullName = r.optString("full_name", "")
            val name = r.optString("name", "").ifEmpty { fullName.substringAfterLast('/') }
            val cloneUrl = r.optString("clone_url", "")
            if (fullName.isNotEmpty() && cloneUrl.isNotEmpty()) {
                add(RemoteRepo(fullName, name, stripUserInfo(cloneUrl)))
            }
        }
    }
}
