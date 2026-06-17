package jp.lazmix.codeleaf.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Query(
        """
        SELECT m.*, COUNT(e.id) AS entryCount
        FROM memos m LEFT JOIN memo_entries e ON e.memoId = m.id
        WHERE m.repoId = :repoId
        GROUP BY m.id
        ORDER BY m.updatedAt DESC
        """,
    )
    fun observeMemosWithCount(repoId: Long): Flow<List<MemoWithCount>>

    @Query("SELECT * FROM memo_entries WHERE memoId = :memoId ORDER BY filePath COLLATE NOCASE, lineStart, id")
    fun observeEntries(memoId: Long): Flow<List<MemoEntry>>

    @Query("SELECT * FROM memo_entries WHERE memoId = :memoId ORDER BY filePath COLLATE NOCASE, lineStart, id")
    suspend fun getEntries(memoId: Long): List<MemoEntry>

    @Insert
    suspend fun insertMemo(memo: Memo): Long

    @Query("UPDATE memos SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameMemo(id: Long, title: String, now: Long)

    @Query("UPDATE memos SET updatedAt = :now WHERE id = :id")
    suspend fun touchMemo(id: Long, now: Long)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteMemo(id: Long)

    @Insert
    suspend fun insertEntry(entry: MemoEntry): Long

    @Query("DELETE FROM memo_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)
}
