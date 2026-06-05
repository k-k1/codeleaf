package jp.lazmix.codeleaf.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BitbucketOAuthTokenExchangerTest {

    private class FakeHttp(
        val result: HttpResult? = null,
        val throwIo: Boolean = false,
        var lastForm: Map<String, String>? = null,
        var lastBasic: String? = null,
    ) : TokenHttp {
        override fun postForm(url: String, basicAuth: String, form: Map<String, String>): HttpResult {
            lastForm = form
            lastBasic = basicAuth
            if (throwIo) throw IOException("boom")
            return result!!
        }
    }

    @Test
    fun exchangeCodeSendsAuthorizationCodeGrantAndParses() {
        val http = FakeHttp(HttpResult(200, """{"access_token":"AT","refresh_token":"RT","expires_in":7200}"""))
        val ex = BitbucketOAuthTokenExchanger("cid", "secret", http)
        val tokens = ex.exchangeCode("the-code", "codeleaf://oauth").getOrThrow()
        assertEquals("AT", tokens.accessToken)
        assertEquals("authorization_code", http.lastForm?.get("grant_type"))
        assertEquals("the-code", http.lastForm?.get("code"))
        // Basic base64("cid:secret")
        assertEquals("Y2lkOnNlY3JldA==", http.lastBasic)
    }

    @Test
    fun refreshSendsRefreshTokenGrant() {
        val http = FakeHttp(HttpResult(200, """{"access_token":"AT2","expires_in":7200}"""))
        val ex = BitbucketOAuthTokenExchanger("cid", "secret", http)
        ex.refresh("the-rt").getOrThrow()
        assertEquals("refresh_token", http.lastForm?.get("grant_type"))
        assertEquals("the-rt", http.lastForm?.get("refresh_token"))
    }

    @Test
    fun invalidGrantSurfaces() {
        val http = FakeHttp(HttpResult(400, """{"error":"invalid_grant"}"""))
        val err = BitbucketOAuthTokenExchanger("cid", "secret", http).refresh("rt").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.InvalidGrant)
    }

    @Test
    fun networkErrorBecomesOAuthNetworkError() {
        val http = FakeHttp(throwIo = true)
        val err = BitbucketOAuthTokenExchanger("cid", "secret", http).exchangeCode("c", "r").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Network)
    }
}
