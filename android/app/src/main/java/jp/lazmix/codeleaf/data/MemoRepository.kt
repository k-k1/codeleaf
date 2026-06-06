package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.Memo
import jp.lazmix.codeleaf.data.db.MemoDao
import jp.lazmix.codeleaf.data.db.MemoEntry
import jp.lazmix.codeleaf.data.db.MemoWithCount
import kotlinx.coroutines.flow.Flow

/**
 * メモ帳とエントリの読み書き。タイムスタンプ付与とメモ帳の updatedAt 更新をここで集約する。
 * [now] は時刻供給(テストで差し替え可)。
 */
class MemoRepository(
    private val dao: MemoDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeMemos(repoId: Long): Flow<List<MemoWithCount>> = dao.observeMemosWithCount(repoId)

    fun observeEntries(memoId: Long): Flow<List<MemoEntry>> = dao.observeEntries(memoId)

    suspend fun getMemo(id: Long): Memo? = dao.getMemo(id)

    suspend fun getEntries(memoId: Long): List<MemoEntry> = dao.getEntries(memoId)

    /** 新規メモ帳を作成し id を返す。 */
    suspend fun createMemo(repoId: Long, title: String): Long {
        val t = now()
        return dao.insertMemo(Memo(repoId = repoId, title = title.trim(), createdAt = t, updatedAt = t))
    }

    suspend fun renameMemo(id: Long, title: String) = dao.renameMemo(id, title.trim(), now())

    suspend fun deleteMemo(id: Long) = dao.deleteMemo(id)

    /** エントリを追加し、親メモ帳の updatedAt を更新する。 */
    suspend fun addEntry(
        memoId: Long,
        filePath: String,
        lineStart: Int,
        lineEnd: Int,
        quote: String,
        comment: String,
    ): Long {
        val t = now()
        val id = dao.insertEntry(
            MemoEntry(
                memoId = memoId,
                filePath = filePath,
                lineStart = lineStart,
                lineEnd = lineEnd,
                quote = quote,
                comment = comment.trim(),
                createdAt = t,
            ),
        )
        dao.touchMemo(memoId, t)
        return id
    }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)
}
