package jp.lazmix.codeleaf.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationRequestTest {

    @Test
    fun buildIncludesRequiredParams() {
        val req = BitbucketAuthorization.build("cid123", "codeleaf://oauth", "st-XYZ")
        assertTrue(req.url.startsWith("https://bitbucket.org/site/oauth2/authorize?"))
        assertTrue(req.url.contains("client_id=cid123"))
        assertTrue(req.url.contains("response_type=code"))
        assertTrue(req.url.contains("state=st-XYZ"))
        // redirect_uri は URL エンコードされる
        assertTrue(req.url.contains("redirect_uri=codeleaf%3A%2F%2Foauth"))
        assertEquals("st-XYZ", req.state)
        assertEquals("codeleaf://oauth", req.redirectUri)
    }

    @Test
    fun parseRedirectSuccess() {
        val r = BitbucketAuthorization.parseRedirect(mapOf("code" to "abc", "state" to "s1"), "s1")
        assertEquals(RedirectResult.Success("abc"), r)
    }

    @Test
    fun parseRedirectStateMismatch() {
        val r = BitbucketAuthorization.parseRedirect(mapOf("code" to "abc", "state" to "other"), "s1")
        assertEquals(RedirectResult.StateMismatch, r)
    }

    @Test
    fun parseRedirectAuthError() {
        val r = BitbucketAuthorization.parseRedirect(mapOf("error" to "access_denied"), "s1")
        assertEquals(RedirectResult.AuthError("access_denied"), r)
    }

    @Test
    fun parseRedirectMalformed() {
        val r = BitbucketAuthorization.parseRedirect(mapOf("state" to "s1"), "s1")
        assertEquals(RedirectResult.Malformed, r)
    }
}
