package jp.lazmix.codeleaf.data.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GitHubDeviceFlowTest {

    // --- 純粋関数：device/code 応答 ---

    @Test
    fun parsesDeviceCodeResponse() {
        val body = """
            {"device_code":"DC","user_code":"WDJB-MJHT",
             "verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}
        """.trimIndent()
        val code = parseDeviceCodeResponse(200, body).getOrThrow()
        assertEquals("DC", code.deviceCode)
        assertEquals("WDJB-MJHT", code.userCode)
        assertEquals("https://github.com/login/device", code.verificationUri)
        assertEquals(900L, code.expiresInSec)
        assertEquals(5, code.intervalSec)
    }

    @Test
    fun deviceCodeHttpErrorFails() {
        val err = parseDeviceCodeResponse(404, "nope").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Http)
    }

    // --- 純粋関数：token ポーリング応答 ---

    @Test
    fun pollPendingAndSlowDownAndTerminals() {
        assertTrue(parsePollResponse(200, """{"error":"authorization_pending"}""") is GitHubPollResult.Pending)
        val sd = parsePollResponse(200, """{"error":"slow_down","interval":10}""")
        assertTrue(sd is GitHubPollResult.SlowDown && (sd as GitHubPollResult.SlowDown).newIntervalSec == 10)
        assertTrue(parsePollResponse(200, """{"error":"access_denied"}""") is GitHubPollResult.Denied)
        assertTrue(parsePollResponse(200, """{"error":"expired_token"}""") is GitHubPollResult.Expired)
        assertTrue(parsePollResponse(200, """{"error":"unsupported_grant_type"}""") is GitHubPollResult.Failed)
        assertTrue(parsePollResponse(200, "not-json") is GitHubPollResult.Failed)
    }

    @Test
    fun pollAuthorizedYieldsNonExpiringToken() {
        val r = parsePollResponse(200, """{"access_token":"gho_x","token_type":"bearer","scope":"repo"}""")
        assertTrue(r is GitHubPollResult.Authorized)
        val t = (r as GitHubPollResult.Authorized).tokens
        assertEquals("gho_x", t.accessToken)
        assertNull(t.refreshToken)
        assertEquals("repo", t.scopes)
        assertEquals(GitHubDeviceFlow.NON_EXPIRING_SEC, t.expiresInSec)
    }

    // --- 純粋関数：/user/repos ページ ---

    @Test
    fun parsesRepoPage() {
        val body = """
            [{"full_name":"me/a","name":"a","clone_url":"https://github.com/me/a.git"},
             {"full_name":"me/b","name":"b","clone_url":"https://github.com/me/b.git"}]
        """.trimIndent()
        val repos = parseGitHubRepoPage(body)
        assertEquals(2, repos.size)
        assertEquals("me/a", repos[0].fullName)
        assertEquals("https://github.com/me/a.git", repos[0].cloneUrl)
    }

    // --- サービス：ポーリング ---

    private class QueueHttp(vararg responses: HttpResult) : TokenHttp {
        private val q = ArrayDeque(responses.toList())
        val basicAuths = mutableListOf<String>()
        override fun postForm(url: String, basicAuth: String, form: Map<String, String>): HttpResult {
            basicAuths.add(basicAuth)
            return q.removeFirst()
        }
    }

    private fun service(http: TokenHttp) = GitHubDeviceFlowService(
        clientId = "cid",
        http = http,
        ioDispatcher = Dispatchers.Unconfined,
        now = { 0L },        // deadline に到達しない
        sleep = { },         // 待機しない
    )

    @Test
    fun pollLoopsUntilAuthorized() = runBlocking {
        val http = QueueHttp(
            HttpResult(200, """{"error":"authorization_pending"}"""),
            HttpResult(200, """{"error":"slow_down","interval":7}"""),
            HttpResult(200, """{"access_token":"gho_ok","scope":"repo"}"""),
        )
        val code = GitHubDeviceCode("DC", "UC", "https://github.com/login/device", 900, 1)
        val account = service(http).pollForToken(code).getOrThrow()
        assertEquals("gho_ok", account.accessToken)
        assertEquals(GitHubDeviceFlow.PROVIDER, account.provider)
        assertNull(account.refreshToken)
        // Device Flow は Authorization ヘッダを付けない（basicAuth は常に空）。
        assertTrue(http.basicAuths.all { it.isEmpty() })
    }

    @Test
    fun pollDeniedFails() = runBlocking {
        val http = QueueHttp(HttpResult(200, """{"error":"access_denied"}"""))
        val code = GitHubDeviceCode("DC", "UC", "https://github.com/login/device", 900, 1)
        assertTrue(service(http).pollForToken(code).isFailure)
    }

    @Test
    fun pollRetriesThroughTransientNetworkError() = runBlocking {
        // 1回目は通信失敗(IOException)、その後 pending を挟んで承認。一時的な失敗で諦めないこと。
        val script = ArrayDeque(
            listOf<() -> HttpResult>(
                { throw IOException("dns blip") },
                { HttpResult(200, """{"error":"authorization_pending"}""") },
                { HttpResult(200, """{"access_token":"gho_ok","scope":"repo"}""") },
            ),
        )
        val http = object : TokenHttp {
            override fun postForm(url: String, basicAuth: String, form: Map<String, String>) =
                script.removeFirst().invoke()
        }
        val code = GitHubDeviceCode("DC", "UC", "https://github.com/login/device", 900, 1)
        assertEquals("gho_ok", service(http).pollForToken(code).getOrThrow().accessToken)
    }

    @Test
    fun requestDeviceCodeSendsClientIdAndScopeWithoutAuthHeader() = runBlocking {
        val http = object : TokenHttp {
            var form: Map<String, String>? = null
            var basic: String? = null
            override fun postForm(url: String, basicAuth: String, f: Map<String, String>): HttpResult {
                form = f; basic = basicAuth
                return HttpResult(200, """{"device_code":"D","user_code":"U","interval":5,"expires_in":900}""")
            }
        }
        GitHubDeviceFlowService("cid", http).requestDeviceCode().getOrThrow()
        assertEquals("cid", http.form?.get("client_id"))
        assertEquals(GitHubDeviceFlow.DEFAULT_SCOPE, http.form?.get("scope"))
        assertEquals("", http.basic)
    }

    @Test
    fun requestDeviceCodeNetworkErrorSurfaces() = runBlocking {
        val http = object : TokenHttp {
            override fun postForm(url: String, basicAuth: String, form: Map<String, String>): HttpResult =
                throw IOException("boom")
        }
        val err = GitHubDeviceFlowService("cid", http).requestDeviceCode().exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Network)
    }
}
