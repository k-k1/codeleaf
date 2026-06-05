package com.k1.gitreader.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthResponseParserTest {

    @Test
    fun parsesSuccessfulTokenResponse() {
        val body = """
            {"access_token":"AT","refresh_token":"RT","expires_in":7200,"scopes":"repository"}
        """.trimIndent()
        val tokens = parseTokenResponse(200, body).getOrThrow()
        assertEquals("AT", tokens.accessToken)
        assertEquals("RT", tokens.refreshToken)
        assertEquals(7200L, tokens.expiresInSec)
        assertEquals("repository", tokens.scopes)
    }

    @Test
    fun missingRefreshTokenAndScopesBecomeNull() {
        val tokens = parseTokenResponse(200, """{"access_token":"AT","expires_in":3600}""").getOrThrow()
        assertNull(tokens.refreshToken)
        assertNull(tokens.scopes)
        assertEquals(3600L, tokens.expiresInSec)
    }

    @Test
    fun invalidGrantMapsToInvalidGrantError() {
        val err = parseTokenResponse(400, """{"error":"invalid_grant","error_description":"x"}""").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.InvalidGrant)
    }

    @Test
    fun otherHttpErrorMapsToHttpError() {
        val err = parseTokenResponse(500, "oops").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Http)
    }

    @Test
    fun missingAccessTokenIsParseError() {
        val err = parseTokenResponse(200, """{"expires_in":3600}""").exceptionOrNull()
        assertTrue(err is OAuthException && (err as OAuthException).error is OAuthError.Parse)
    }
}
