package jp.lazmix.codeleaf.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    /** 手動並べ替え順(同順位は新しい順)。一覧表示・ブラウザの★判定の両方で購読する。 */
    @Query("SELECT * FROM favorites WHERE repoId = :repoId ORDER BY sortOrder ASC, id DESC")
    fun observeFavorites(repoId: Long): Flow<List<Favorite>>

    /** 既存の最小 sortOrder(新規を先頭に積むため)。空なら null。 */
    @Query("SELECT MIN(sortOrder) FROM favorites WHERE repoId = :repoId")
    suspend fun minSortOrder(repoId: Long): Int?

    @Query("UPDATE favorites SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** (repoId, relPath) 一意制約に当たったら無視(トグル側で存在確認するため実質起きない)。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: Favorite): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorites WHERE repoId = :repoId AND relPath = :relPath")
    suspend fun deleteByPath(repoId: Long, relPath: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE repoId = :repoId AND relPath = :relPath")
    suspend fun count(repoId: Long, relPath: String): Int
}
