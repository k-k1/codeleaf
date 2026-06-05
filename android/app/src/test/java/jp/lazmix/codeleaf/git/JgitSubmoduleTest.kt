package jp.lazmix.codeleaf.git

import org.eclipse.jgit.api.Git
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * submodule を含む親リポを clone したとき、submodule が取得され
 * submodulePaths で検出できることを検証する（JGit は JVM 上でも動作する）。
 * ネットワーク非依存（ローカル file パスで完結）。
 */
class JgitSubmoduleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun initRepoWithFile(dir: File, fileName: String, content: String) {
        Git.init().setInitialBranch("main").setDirectory(dir).call().use { git ->
            File(dir, fileName).writeText(content)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("init").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
        }
    }

    @Test
    fun cloneFetchesSubmodule_andSubmodulePathsDetectsIt() {
        // submodule 元リポ
        val subDir = tmp.newFolder("sub")
        initRepoWithFile(subDir, "LIB.md", "# lib\n")

        // 親リポに submodule を追加（path=vendor/sub）
        val parentDir = tmp.newFolder("parent")
        Git.init().setInitialBranch("main").setDirectory(parentDir).call().use { git ->
            File(parentDir, "README.md").writeText("# parent\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("init").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
            git.submoduleAdd()
                .setURI(subDir.toURI().toString())
                .setPath("vendor/sub")
                .call()
                .close()
            git.commit().setMessage("add submodule").setAuthor("t", "t@e").setCommitter("t", "t@e").call()
        }

        // アプリと同じ経路で clone（updateSubmodules 込み）
        val client = JgitClient()
        val cloneDir = tmp.newFolder("clone")
        cloneDir.delete() // clone 先は未作成である必要がある
        client.clone(parentDir.absolutePath, cloneDir, null)

        // submodule が検出され、実体ファイルも取得されている
        assertTrue("vendor/sub" in client.submodulePaths(cloneDir))
        assertTrue(File(cloneDir, "vendor/sub/LIB.md").exists())
    }
}
