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

    // --- gitErrorKind 分類 ---

    @Test fun auth_maps() {
        assertEquals(GitErrorKind.AUTH, gitErrorKind(RuntimeException("not authorized")))
    }

    @Test fun unknownHost_maps() {
        assertEquals(GitErrorKind.HOST, gitErrorKind(RuntimeException("https://x/y: unable to resolve host \"x\"")))
    }

    @Test fun notFound_maps() {
        assertEquals(GitErrorKind.NOT_FOUND, gitErrorKind(RuntimeException("Repository not found")))
    }

    @Test fun timeout_maps() {
        assertEquals(GitErrorKind.TIMEOUT, gitErrorKind(RuntimeException("connect timed out")))
    }

    @Test fun causeChain_isInspected() {
        val root = java.net.UnknownHostException("Unable to resolve host \"github.com\"")
        val wrapped = RuntimeException("transport error", root)
        assertEquals(GitErrorKind.HOST, gitErrorKind(wrapped))
    }

    @Test fun uploadPack_mapsToCommunication() {
        // JGit TransportHttp が HTTPS fetch 失敗時に出す文言。通信エラー扱いにする。
        assertEquals(
            GitErrorKind.NETWORK,
            gitErrorKind(RuntimeException("https://github.com/o/r.git: cannot open git-upload-pack")),
        )
    }

    @Test fun connectionReset_underWrapper_mapsToCommunication() {
        val root = java.net.SocketException("Connection reset")
        val wrapped = RuntimeException("cannot open git-upload-pack", root)
        assertEquals(GitErrorKind.NETWORK, gitErrorKind(wrapped))
    }

    @Test fun unknown_isNull() {
        assertNull(gitErrorKind(RuntimeException("weird gremlin")))
    }

    // --- cloneErrorUiText / syncResultUiText ---

    @Test fun cloneError_classified_usesRes() {
        assertTrue(cloneErrorUiText(RuntimeException("not authorized")) is UiText.Res)
    }

    @Test fun cloneError_unknown_keepsRawMessage() {
        val ui = cloneErrorUiText(RuntimeException("weird gremlin"))
        assertTrue(ui is UiText.GitError && ui.kind == null && ui.rawFallback == "weird gremlin")
    }

    @Test fun syncMessage_communicationError_isClassified() {
        val ui = syncResultUiText(RuntimeException("uri: cannot open git-upload-pack"))
        assertTrue(ui is UiText.GitError && ui.kind == GitErrorKind.NETWORK)
    }

    @Test fun syncMessage_success_isDone() {
        assertTrue(syncResultUiText(null) is UiText.Res)
    }
}
