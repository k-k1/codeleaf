package com.k1.gitreader.data

import com.k1.gitreader.data.crypto.TokenStore
import com.k1.gitreader.data.db.GitHost
import com.k1.gitreader.data.db.Repo
import com.k1.gitreader.data.db.RepoDao
import com.k1.gitreader.data.db.ThemeMode
import com.k1.gitreader.git.BranchInfo
import com.k1.gitreader.git.JgitClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** 新規リポジトリ登録フォームの入力値。 */
data class NewRepo(
    val name: String,
    val url: String,
    val host: GitHost,
    val username: String,
    val token: String,
    val branch: String?,
    val themeMode: ThemeMode,
)

/**
 * UI と git/db/暗号化トークンを仲介する Repository 層。
 * 全 I/O は ioDispatcher 上で実行する。
 */
class RepoRepository(
    private val dao: RepoDao,
    private val tokenStore: TokenStore,
    private val jgit: JgitClient,
    private val reposRoot: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeRepos(): Flow<List<Repo>> = dao.observeAll()

    fun workDir(repo: Repo): File = File(reposRoot, repo.id.toString())

    /** 登録 → clone → 既定ブランチ確定。失敗時は行と暗号化トークンを巻き戻す。 */
    suspend fun addAndClone(input: NewRepo): Repo = withContext(ioDispatcher) {
        val id = dao.insert(
            Repo(
                name = input.name.ifBlank { input.url.substringAfterLast('/').removeSuffix(".git") },
                url = input.url,
                host = input.host,
                username = input.username,
                branch = input.branch.orEmpty(),
                themeMode = input.themeMode,
            ),
        )
        tokenStore.setToken(id, input.token)
        val dir = File(reposRoot, id.toString())
        try {
            val cp = jgit.credentials(input.username, input.token)
            jgit.clone(input.url, dir, cp)
            val branch = input.branch?.takeIf { it.isNotBlank() } ?: jgit.currentBranch(dir)
            val saved = dao.getById(id)!!.copy(branch = branch, lastSyncedAt = nowMillis())
            dao.update(saved)
            saved
        } catch (t: Throwable) {
            // ロールバック
            dao.getById(id)?.let { dao.delete(it) }
            tokenStore.removeToken(id)
            dir.deleteRecursively()
            throw t
        }
    }

    /** 指定ブランチで最新化（ローカル変更は破棄）。 */
    suspend fun sync(repo: Repo, branch: String = repo.branch): Repo = withContext(ioDispatcher) {
        val token = tokenStore.getToken(repo.id)
        val cp = jgit.credentials(repo.username, token)
        jgit.sync(workDir(repo), branch, cp)
        val saved = repo.copy(branch = branch, lastSyncedAt = nowMillis())
        dao.update(saved)
        saved
    }

    suspend fun listBranches(repo: Repo): List<BranchInfo> = withContext(ioDispatcher) {
        jgit.listBranches(workDir(repo))
    }

    suspend fun delete(repo: Repo) = withContext(ioDispatcher) {
        dao.delete(repo)
        tokenStore.removeToken(repo.id)
        workDir(repo).deleteRecursively()
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
