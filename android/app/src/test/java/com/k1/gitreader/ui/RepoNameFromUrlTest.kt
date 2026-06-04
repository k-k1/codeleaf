package com.k1.gitreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** repoNameFromUrl(URL→表示名 自動補完)の JVM 単体テスト。 */
class RepoNameFromUrlTest {

    @Test
    fun githubHttpsWithGitSuffix() {
        assertEquals("repo", repoNameFromUrl("https://github.com/owner/repo.git"))
    }

    @Test
    fun githubHttpsWithoutGitSuffix() {
        assertEquals("repo", repoNameFromUrl("https://github.com/owner/repo"))
    }

    @Test
    fun bitbucketHttps() {
        assertEquals("my-docs", repoNameFromUrl("https://bitbucket.org/team/my-docs.git"))
    }

    @Test
    fun trailingSlashAndQueryAndFragment() {
        assertEquals("repo", repoNameFromUrl("https://github.com/owner/repo/"))
        assertEquals("repo", repoNameFromUrl("https://github.com/owner/repo.git?x=1"))
        assertEquals("repo", repoNameFromUrl("https://github.com/owner/repo#frag"))
    }

    @Test
    fun blankReturnsEmpty() {
        assertEquals("", repoNameFromUrl("   "))
    }
}
