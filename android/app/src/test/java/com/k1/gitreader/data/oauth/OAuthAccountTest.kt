package com.k1.gitreader.data.oauth

import com.k1.gitreader.data.db.AuthType
import com.k1.gitreader.data.db.GitHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthAccountTest {

    @Test
    fun jsonRoundTrip() {
        val a = OAuthAccount("BITBUCKET", "AT", "RT", 1_700_000_000_000, "repository")
        assertEquals(a, OAuthAccount.fromJson(a.toJson()))
    }

    @Test
    fun jsonRoundTripWithNulls() {
        val a = OAuthAccount("BITBUCKET", "AT", null, 42L, null)
        val back = OAuthAccount.fromJson(a.toJson())
        assertNull(back.refreshToken)
        assertNull(back.scopes)
        assertEquals(a, back)
    }

    @Test
    fun fromTokensComputesAbsoluteExpiry() {
        val tokens = OAuthTokens("AT", "RT", 3600, "repository")
        val a = OAuthAccount.fromTokens("BITBUCKET", tokens, nowMs = 1_000_000L)
        assertEquals(1_000_000L + 3_600_000L, a.expiresAtEpochMs)
    }

    @Test
    fun needsRefreshRespectsMargin() {
        val now = 1_000_000L
        assertFalse(needsRefresh(now + 10 * 60_000, now))   // 10 分先 → 不要
        assertTrue(needsRefresh(now + 60_000, now))          // 1 分先(< 既定 120 秒) → 必要
        assertTrue(needsRefresh(now - 1, now))               // 既に失効 → 必要
    }

    @Test
    fun gitUsernameForBranches() {
        assertEquals("x-token-auth", gitUsernameFor(GitHost.BITBUCKET, AuthType.OAUTH, "ignored"))
        assertEquals("x-access-token", gitUsernameFor(GitHost.GITHUB, AuthType.OAUTH, "ignored"))
        assertEquals("alice", gitUsernameFor(GitHost.BITBUCKET, AuthType.TOKEN, "alice"))
        assertNull(gitUsernameFor(GitHost.GITHUB, AuthType.TOKEN, ""))
    }
}
