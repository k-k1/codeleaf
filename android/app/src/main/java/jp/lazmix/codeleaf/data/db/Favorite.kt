package jp.lazmix.codeleaf.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * リポジトリ内の特定ファイル/フォルダのお気に入り。relPath はリポルート相対('/'区切り)。
 * 実体が消えても行は残し(一覧でグレーアウト・削除のみ可)、リポ削除で CASCADE。
 * (repoId, relPath) は一意で、同じ対象を二重登録しない。
 */
@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = Repo::class,
            parentColumns = ["id"],
            childColumns = ["repoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("repoId"), Index(value = ["repoId", "relPath"], unique = true)],
)
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val relPath: String,
    /** フォルダなら true(タップ時に Browse へ、ファイルなら Viewer へ振り分ける)。 */
    val isDir: Boolean,
    val createdAt: Long,
)
