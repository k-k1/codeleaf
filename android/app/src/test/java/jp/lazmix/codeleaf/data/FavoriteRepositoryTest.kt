package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.Favorite
import jp.lazmix.codeleaf.data.db.FavoriteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FavoriteRepository.toggle] が「未登録なら追加・登録済みなら解除」を満たすか(インメモリ DAO)。 */
class FavoriteRepositoryTest {

    /** (repoId, relPath) 一意のインメモリ FavoriteDao。テストで必要な操作だけを実装する。 */
    private class FakeFavoriteDao : FavoriteDao {
        val rows = mutableListOf<Favorite>()
        private var nextId = 1L

        override fun observeFavorites(repoId: Long): Flow<List<Favorite>> = emptyFlow()

        override suspend fun insert(favorite: Favorite): Long {
            if (rows.any { it.repoId == favorite.repoId && it.relPath == favorite.relPath }) return -1L
            val id = nextId++
            rows.add(favorite.copy(id = id))
            return id
        }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteByPath(repoId: Long, relPath: String) {
            rows.removeAll { it.repoId == repoId && it.relPath == relPath }
        }

        override suspend fun count(repoId: Long, relPath: String): Int =
            rows.count { it.repoId == repoId && it.relPath == relPath }

        override suspend fun minSortOrder(repoId: Long): Int? =
            rows.filter { it.repoId == repoId }.minOfOrNull { it.sortOrder }

        override suspend fun setSortOrder(id: Long, order: Int) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(sortOrder = order)
        }
    }

    @Test
    fun toggleAddsWhenAbsentAndRemovesWhenPresent() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = FavoriteRepository(dao, now = { 1_000L })

        // 1回目: 未登録 → 追加(タイムスタンプ・isDir が保存される)。
        repo.toggle(repoId = 7L, relPath = "src/main/App.kt", isDir = false)
        assertEquals(1, dao.rows.size)
        val saved = dao.rows.single()
        assertEquals(7L, saved.repoId)
        assertEquals("src/main/App.kt", saved.relPath)
        assertEquals(false, saved.isDir)
        assertEquals(1_000L, saved.createdAt)

        // 2回目: 同じ対象 → 解除。
        repo.toggle(repoId = 7L, relPath = "src/main/App.kt", isDir = false)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun toggleIsScopedPerRepoAndPath() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = FavoriteRepository(dao, now = { 0L })

        repo.toggle(repoId = 1L, relPath = "docs", isDir = true)
        repo.toggle(repoId = 2L, relPath = "docs", isDir = true) // 別リポの同名は別物
        repo.toggle(repoId = 1L, relPath = "README.md", isDir = false)
        assertEquals(3, dao.rows.size)

        // repo 1 の docs だけ解除しても他は残る。
        repo.toggle(repoId = 1L, relPath = "docs", isDir = true)
        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.none { it.repoId == 1L && it.relPath == "docs" })
        assertTrue(dao.rows.any { it.repoId == 2L && it.relPath == "docs" })
    }

    @Test
    fun newFavoriteGoesToHeadViaDecreasingSortOrder() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = FavoriteRepository(dao, now = { 0L })

        repo.toggle(repoId = 1L, relPath = "a", isDir = false) // 最初は 0
        repo.toggle(repoId = 1L, relPath = "b", isDir = false) // min(0)-1 = -1
        repo.toggle(repoId = 1L, relPath = "c", isDir = false) // min(-1)-1 = -2

        // sortOrder 昇順 = 新しい順(c, b, a)。
        val ordered = dao.rows.sortedBy { it.sortOrder }.map { it.relPath }
        assertEquals(listOf("c", "b", "a"), ordered)
    }

    @Test
    fun reorderRewritesSortOrderToIndex() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = FavoriteRepository(dao, now = { 0L })
        repo.toggle(repoId = 1L, relPath = "a", isDir = false)
        repo.toggle(repoId = 1L, relPath = "b", isDir = false)
        repo.toggle(repoId = 1L, relPath = "c", isDir = false)
        val byPath = dao.rows.associateBy { it.relPath }

        // a, b, c の順に並べ替える。
        repo.reorder(listOf(byPath.getValue("a").id, byPath.getValue("b").id, byPath.getValue("c").id))

        assertEquals(0, dao.rows.single { it.relPath == "a" }.sortOrder)
        assertEquals(1, dao.rows.single { it.relPath == "b" }.sortOrder)
        assertEquals(2, dao.rows.single { it.relPath == "c" }.sortOrder)
    }
}
