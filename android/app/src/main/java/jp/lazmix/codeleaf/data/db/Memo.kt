package jp.lazmix.codeleaf.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * メモ帳。リポジトリごとに複数持てる注釈の束(「レビュー」「質問」等のテーマ単位)。
 * リポ削除で CASCADE 削除される。
 */
@Entity(
    tableName = "memos",
    foreignKeys = [
        ForeignKey(
            entity = Repo::class,
            parentColumns = ["id"],
            childColumns = ["repoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("repoId")],
)
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val title: String,
    val createdAt: Long,
    /** 最終更新(エントリ追加・改名)。一覧はこの降順で並べる。 */
    val updatedAt: Long,
)

/**
 * メモ帳の1エントリ。閲覧中ファイルの行範囲とその引用、ユーザのコメントを保持する。
 * 行番号は 1 始まり・[lineEnd] は含む(単一行なら start==end)。メモ帳削除で CASCADE 削除。
 */
@Entity(
    tableName = "memo_entries",
    foreignKeys = [
        ForeignKey(
            entity = Memo::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memoId")],
)
data class MemoEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoId: Long,
    /** リポルート相対パス。 */
    val filePath: String,
    val lineStart: Int,
    val lineEnd: Int,
    /** 対象行の引用(複数行は改行区切り)。 */
    val quote: String,
    val comment: String,
    val createdAt: Long,
)

/** メモ帳 + エントリ数(一覧表示用)。 */
data class MemoWithCount(
    @Embedded val memo: Memo,
    val entryCount: Int,
)
