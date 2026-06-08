package jp.lazmix.codeleaf.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Repo>>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun getById(id: Long): Repo?

    @Insert
    suspend fun insert(repo: Repo): Long

    @Update
    suspend fun update(repo: Repo)

    @Delete
    suspend fun delete(repo: Repo)

    /** プロセス死で中断した clone(CLONING 残留)を起動時に FAILED へ倒す。 */
    @Query("UPDATE repos SET cloneState = 'FAILED' WHERE cloneState = 'CLONING'")
    suspend fun failInterruptedClones()
}
