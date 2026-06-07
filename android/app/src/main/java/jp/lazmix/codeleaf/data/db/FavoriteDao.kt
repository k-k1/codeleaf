package jp.lazmix.codeleaf.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    /** 追加が新しい順。一覧表示・ブラウザの★判定の両方で購読する。 */
    @Query("SELECT * FROM favorites WHERE repoId = :repoId ORDER BY createdAt DESC")
    fun observeFavorites(repoId: Long): Flow<List<Favorite>>

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
