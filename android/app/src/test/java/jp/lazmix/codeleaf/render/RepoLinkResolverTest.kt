package jp.lazmix.codeleaf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 相対リンク → repo ルート相対パス解決(RepoLinkResolver.resolveRepoRelativePath)の JVM 単体テスト。
 * 実ファイルの有無で挙動が変わるため一時ディレクトリにリポ構造を作って検証する。
 */
class RepoLinkResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var workDir: File

    private fun resolve(baseDir: File, link: String): String? =
        RepoLinkResolver.resolveRepoRelativePath(baseDir, workDir, link)

    @org.junit.Before
    fun setUp() {
        workDir = tmp.root
        // リポ構造: README.md / docs/guide.md / docs/img/x.png / api/spec.md
        File(workDir, "README.md").writeText("# root")
        File(workDir, "docs").mkdirs()
        File(workDir, "docs/guide.md").writeText("# guide")
        File(workDir, "docs/img").mkdirs()
        File(workDir, "docs/img/x.png").writeText("png")
        File(workDir, "api").mkdirs()
        File(workDir, "api/spec.md").writeText("# spec")
    }

    @Test
    fun sameDir_relativeLink() {
        val base = File(workDir, "docs")
        assertEquals("docs/guide.md", resolve(base, "guide.md"))
        assertEquals("docs/guide.md", resolve(base, "./guide.md"))
    }

    @Test
    fun parentDir_relativeLink() {
        val base = File(workDir, "docs")
        assertEquals("README.md", resolve(base, "../README.md"))
        assertEquals("api/spec.md", resolve(base, "../api/spec.md"))
    }

    @Test
    fun anchorAndQuery_areStripped() {
        val base = File(workDir, "docs")
        assertEquals("docs/guide.md", resolve(base, "guide.md#section"))
        assertEquals("README.md", resolve(base, "../README.md?x=1"))
    }

    @Test
    fun nonMarkdownButExistingFile_resolves() {
        // .md 以外でも存在するリポ内ファイルは遷移対象(FileViewer がコード表示)
        val base = File(workDir, "docs")
        assertEquals("docs/img/x.png", resolve(base, "img/x.png"))
    }

    @Test
    fun absoluteAndSchemeLinks_returnNull() {
        val base = File(workDir, "docs")
        assertNull(resolve(base, "https://example.com/a.md"))
        assertNull(resolve(base, "mailto:a@b.com"))
        assertNull(resolve(base, "/abs/path.md"))
        assertNull(resolve(base, "#section"))
    }

    @Test
    fun outsideRepo_returnsNull() {
        val base = File(workDir, "docs")
        assertNull(resolve(base, "../../escape.md"))
    }

    @Test
    fun nonExistentFile_returnsNull() {
        val base = File(workDir, "docs")
        assertNull(resolve(base, "missing.md"))
    }

    @Test
    fun directoryTarget_returnsNull_forFileResolution() {
        // ファイル限定の resolveRepoRelativePath はディレクトリを解決しない(=null)。
        val base = workDir
        assertNull("ファイル解決ではディレクトリ対象外", resolve(base, "docs"))
    }

    private fun target(baseDir: File, link: String): RepoTarget? =
        RepoLinkResolver.resolveRepoTarget(baseDir, workDir, link)

    @Test
    fun fileTarget_resolvesAsFile() {
        val base = File(workDir, "docs")
        assertEquals(RepoTarget.FileTarget("docs/guide.md"), target(base, "guide.md"))
        assertEquals(RepoTarget.FileTarget("README.md"), target(base, "../README.md"))
        assertEquals(RepoTarget.FileTarget("api/spec.md"), target(base, "../api/spec.md"))
    }

    @Test
    fun directoryTarget_resolvesAsDir() {
        // 子ディレクトリ・親経由の他ディレクトリ・末尾スラッシュ付きいずれもディレクトリ遷移。
        assertEquals(RepoTarget.DirTarget("docs"), target(workDir, "docs"))
        assertEquals(RepoTarget.DirTarget("docs"), target(workDir, "docs/"))
        assertEquals(RepoTarget.DirTarget("api"), target(File(workDir, "docs"), "../api"))
        assertEquals(RepoTarget.DirTarget("docs/img"), target(File(workDir, "docs"), "img"))
    }

    @Test
    fun rootDirectoryTarget_hasEmptyPath() {
        // 親へ上がってリポルートを指すリンクは path="" のディレクトリ(ブラウザのトップ)。
        assertEquals(RepoTarget.DirTarget(""), target(File(workDir, "docs"), ".."))
        assertEquals(RepoTarget.DirTarget(""), target(File(workDir, "docs"), "../"))
    }

    @Test
    fun percentEncodedPath_isDecoded() {
        // スペースや非ASCIIを含むパスは %XX で来ても実体に解決する。
        File(workDir, "my docs").mkdirs()
        File(workDir, "my docs/a b.md").writeText("# x")
        assertEquals(RepoTarget.FileTarget("my docs/a b.md"), target(workDir, "my%20docs/a%20b.md"))
        assertEquals(RepoTarget.DirTarget("my docs"), target(workDir, "my%20docs"))
    }

    @Test
    fun literalPlusInName_isNotTurnedIntoSpace() {
        // パスの '+' はスペースにしない(form エンコードではないため)。
        File(workDir, "c++.md").writeText("# x")
        assertEquals(RepoTarget.FileTarget("c++.md"), target(workDir, "c++.md"))
    }

    @Test
    fun outsideRepoOrMissing_returnsNullTarget() {
        val base = File(workDir, "docs")
        assertNull(target(base, "../../escape.md"))
        assertNull(target(base, "missing.md"))
        assertNull(target(base, "https://example.com/a"))
        assertNull(target(base, "#section"))
    }
}
