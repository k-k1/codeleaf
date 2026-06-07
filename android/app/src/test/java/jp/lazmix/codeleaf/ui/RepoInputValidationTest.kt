package jp.lazmix.codeleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoInputValidationTest {

    // --- repoUrlError ---

    @Test fun validHttpsWithGit_ok() {
        assertNull(repoUrlError("https://github.com/owner/repo.git"))
    }

    @Test fun validHttpsNoGit_ok() {
        assertNull(repoUrlError("https://bitbucket.org/team/proj"))
    }

    @Test fun empty_isNullDeferredToRequired() {
        assertNull(repoUrlError(""))
        assertNull(repoUrlError("   "))
    }

    @Test fun hostOnly_noPath_isError() {
        assertNotNull(repoUrlError("https://github.com"))
    }

    @Test fun bareWord_isError() {
        assertNotNull(repoUrlError("github.com/owner/repo")) // スキーム無しは弾く
        assertNotNull(repoUrlError("not a url"))
    }

    @Test fun whitespace_isError() {
        assertTrue(repoUrlError("https://github.com/own er/repo")!!.contains("空白"))
    }

    @Test fun scpForm_ok() {
        assertNull(repoUrlError("git@github.com:owner/repo.git"))
    }

    // --- cloneErrorMessage ---

    @Test fun auth_maps() {
        val m = cloneErrorMessage(RuntimeException("not authorized"))
        assertTrue(m.contains("認証"))
    }

    @Test fun unknownHost_maps() {
        val m = cloneErrorMessage(RuntimeException("https://x/y: unable to resolve host \"x\""))
        assertTrue(m.contains("ホスト"))
    }

    @Test fun notFound_maps() {
        val m = cloneErrorMessage(RuntimeException("Repository not found"))
        assertTrue(m.contains("見つかりません"))
    }

    @Test fun timeout_maps() {
        val m = cloneErrorMessage(RuntimeException("connect timed out"))
        assertTrue(m.contains("タイムアウト"))
    }

    @Test fun causeChain_isInspected() {
        val root = java.net.UnknownHostException("Unable to resolve host \"github.com\"")
        val wrapped = RuntimeException("transport error", root)
        assertTrue(cloneErrorMessage(wrapped).contains("ホスト"))
    }

    @Test fun unknown_fallsBackToMessage() {
        val m = cloneErrorMessage(RuntimeException("weird gremlin"))
        assertTrue(m.contains("weird gremlin"))
    }
}
