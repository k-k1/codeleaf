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
        assertEquals(RepoUrlError.WHITESPACE, repoUrlError("https://github.com/own er/repo"))
    }

    @Test fun scpForm_ok() {
        assertNull(repoUrlError("git@github.com:owner/repo.git"))
    }

    @Test fun localAbsolutePath_ok() {
        // 端末上のローカル git リポ(file:// clone 用)。E2E もこの経路を通す。
        assertNull(repoUrlError("/data/user/0/jp.lazmix.codeleaf/cache/e2e-src"))
    }

    @Test fun fileUrl_ok() {
        assertNull(repoUrlError("file:///path/to/repo"))
    }

    @Test fun fileUrl_nonAbsolute_isError() {
        assertNotNull(repoUrlError("file://relative/repo"))
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

    @Test fun uploadPack_mapsToCommunication() {
        // JGit TransportHttp が HTTPS fetch 失敗時に出す文言。通信エラー扱いにする。
        val m = cloneErrorMessage(RuntimeException("https://github.com/o/r.git: cannot open git-upload-pack"))
        assertTrue(m.contains("通信"))
    }

    @Test fun connectionReset_underWrapper_mapsToCommunication() {
        val root = java.net.SocketException("Connection reset")
        val wrapped = RuntimeException("cannot open git-upload-pack", root)
        assertTrue(cloneErrorMessage(wrapped).contains("通信"))
    }

    // --- syncResultMessage ---

    @Test fun syncMessage_communicationError_isClassified() {
        val m = syncResultMessage(RuntimeException("uri: cannot open git-upload-pack"))
        assertTrue(m.contains("同期失敗"))
        assertTrue(m.contains("通信"))
    }

    @Test fun syncMessage_success_isDone() {
        assertTrue(syncResultMessage(null).contains("同期完了"))
    }
}
