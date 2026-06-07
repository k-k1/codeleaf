package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.Favorite
import jp.lazmix.codeleaf.data.db.FavoriteDao
import kotlinx.coroutines.flow.Flow

/**
 * お気に入り(ファイル/フォルダ)の読み書き。タイムスタンプ付与をここで集約する。
 * 実体の存在判定はファイルシステム側([RepoRepository.exists])が担う。
 * [now] は時刻供給(テストで差し替え可)。
 */
class FavoriteRepository(
    private val dao: FavoriteDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeFavorites(repoId: Long): Flow<List<Favorite>> = dao.observeFavorites(repoId)

    /** 登録/解除をトグルする。登録済みなら消し、未登録なら先頭(最小 sortOrder-1)に追加する。 */
    suspend fun toggle(repoId: Long, relPath: String, isDir: Boolean) {
        if (dao.count(repoId, relPath) > 0) {
            dao.deleteByPath(repoId, relPath)
        } else {
            val head = (dao.minSortOrder(repoId) ?: 0) - 1
            dao.insert(
                Favorite(
                    repoId = repoId,
                    relPath = relPath,
                    isDir = isDir,
                    createdAt = now(),
                    sortOrder = head,
                ),
            )
        }
    }

    /** 並べ替え結果を保存する。[orderedIds] の並びどおりに sortOrder を 0..n へ振り直す。 */
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
