package jp.lazmix.codeleaf.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitbucketApiTest {

    private val page1 = """
        {"values":[
          {"full_name":"ws/repo-a","name":"repo-a","links":{"clone":[
            {"name":"https","href":"https://user@bitbucket.org/ws/repo-a.git"},
            {"name":"ssh","href":"git@bitbucket.org:ws/repo-a.git"}]}},
          {"full_name":"ws/repo-b","name":"repo-b","links":{"clone":[
            {"name":"https","href":"https://bitbucket.org/ws/repo-b.git"}]}}
        ],"next":"https://api.bitbucket.org/2.0/repositories?page=2"}
    """.trimIndent()

    private val page2 = """
        {"values":[
          {"full_name":"ws/repo-c","name":"repo-c","links":{"clone":[
            {"name":"https","href":"https://bitbucket.org/ws/repo-c.git"}]}}
        ]}
    """.trimIndent()

    @Test
    fun parseRepoPageExtractsHttpsCloneAndNext() {
        val (repos, next) = parseRepoPage(page1)
        assertEquals(2, repos.size)
        // userinfo は除去される
        assertEquals(RemoteRepo("ws/repo-a", "repo-a", "https://bitbucket.org/ws/repo-a.git"), repos[0])
        assertEquals("https://bitbucket.org/ws/repo-b.git", repos[1].cloneUrl)
        assertEquals("https://api.bitbucket.org/2.0/repositories?page=2", next)
    }

    @Test
    fun parseRepoPageLastPageHasNoNext() {
        val (repos, next) = parseRepoPage(page2)
        assertEquals(1, repos.size)
        assertNull(next)
    }

    // /2.0/user/workspaces は workspace をネストする形。
    private val workspaces = """{"values":[{"type":"workspace_membership","workspace":{"slug":"ws","name":"WS"}}]}"""

    @Test
    fun parseWorkspaceSlugsHandlesNestedAndTopLevel() {
        val nested = parseWorkspaceSlugs("""{"values":[{"workspace":{"slug":"a"}},{"workspace":{"slug":"b"}}],"next":"u"}""")
        assertEquals(listOf("a", "b"), nested.first)
        assertEquals("u", nested.second)
        // 直に slug を持つ形にも対応。
        val flat = parseWorkspaceSlugs("""{"values":[{"slug":"c"}]}""")
        assertEquals(listOf("c"), flat.first)
        assertNull(flat.second)
    }

    @Test
    fun listRepositoriesEnumeratesWorkspacesThenReposAndSendsBearer() {
        var firstAuth: String? = null
        val http = object : ApiHttp {
            override fun getJson(url: String, authHeader: String): HttpResult {
                if (firstAuth == null) firstAuth = authHeader
                return when {
                    url.contains("/user/workspaces") -> HttpResult(200, workspaces)
                    url.contains("page=2") -> HttpResult(200, page2)
                    else -> HttpResult(200, page1) // /2.0/repositories/ws
                }
            }
        }
        val repos = BitbucketApi(http).listRepositories("AT").getOrThrow()
        assertEquals(listOf("ws/repo-a", "ws/repo-b", "ws/repo-c"), repos.map { it.fullName })
        assertEquals("Bearer AT", firstAuth) // 最初の呼び出し(=workspaces)も Bearer
    }

    @Test
    fun listRepositoriesSurfacesWorkspacesHttpError() {
        // workspaces 取得が失敗(401)したら HTTP エラーとして表面化する。
        val http = object : ApiHttp {
            override fun getJson(url: String, authHeader: String) = HttpResult(401, """{"error":"x"}""")
        }
        val err = BitbucketApi(http).listRepositories("AT").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Http)
    }

    @Test
    fun normalizeRepoUrlCanonicalizes() {
        assertEquals(
            "https://bitbucket.org/ws/repo-a",
            normalizeRepoUrl("https://user@bitbucket.org/ws/Repo-A.git/"),
        )
        // 同一リポは userinfo / .git / 末尾スラッシュ差を吸収して一致
        assertEquals(
            normalizeRepoUrl("https://bitbucket.org/ws/repo-a.git"),
            normalizeRepoUrl("https://x@bitbucket.org/ws/repo-a"),
        )
    }
}
