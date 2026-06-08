package jp.lazmix.codeleaf.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun hostToString(h: GitHost): String = h.name
    @TypeConverter fun stringToHost(s: String): GitHost = GitHost.valueOf(s)
    @TypeConverter fun themeToString(t: ThemeMode): String = t.name
    @TypeConverter fun stringToTheme(s: String): ThemeMode = ThemeMode.valueOf(s)
    @TypeConverter fun colorToString(c: RepoColor): String = c.name
    @TypeConverter fun stringToColor(s: String): RepoColor = RepoColor.valueOf(s)
    @TypeConverter fun authTypeToString(a: AuthType): String = a.name
    @TypeConverter fun stringToAuthType(s: String): AuthType = AuthType.valueOf(s)
    @TypeConverter fun cloneStateToString(c: CloneState): String = c.name
    @TypeConverter fun stringToCloneState(s: String): CloneState = CloneState.valueOf(s)
}

/** v1→v2: 並べ替え順(sortOrder)とカード色(colorTag)を追加。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE repos ADD COLUMN colorTag TEXT NOT NULL DEFAULT 'NONE'")
    }
}

/** v2→v3: 認証種別(authType)を追加。既存行は手入力トークン扱い(TOKEN)。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repos ADD COLUMN authType TEXT NOT NULL DEFAULT 'TOKEN'")
    }
}

/** v3→v4: 所属グループ(groupName)を追加。既存行は未分類(空)。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repos ADD COLUMN groupName TEXT NOT NULL DEFAULT ''")
    }
}

/** v4→v5: メモ帳(memos)とそのエントリ(memo_entries)を追加。いずれも親削除で CASCADE。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`repoId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`repoId`) REFERENCES `repos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memos_repoId` ON `memos` (`repoId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memo_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`memoId` INTEGER NOT NULL, `filePath` TEXT NOT NULL, " +
                "`lineStart` INTEGER NOT NULL, `lineEnd` INTEGER NOT NULL, " +
                "`quote` TEXT NOT NULL, `comment` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`memoId`) REFERENCES `memos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memo_entries_memoId` ON `memo_entries` (`memoId`)")
    }
}

/** v5→v6: お気に入り(favorites)を追加。リポ削除で CASCADE・(repoId, relPath) 一意。 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`repoId` INTEGER NOT NULL, `relPath` TEXT NOT NULL, " +
                "`isDir` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`repoId`) REFERENCES `repos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_repoId` ON `favorites` (`repoId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_repoId_relPath` " +
                "ON `favorites` (`repoId`, `relPath`)",
        )
    }
}

/** v6→v7: お気に入りに手動並べ替え順(sortOrder)を追加。既存行は 0(同順位は id 降順=従来順)。 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}

/** v7→v8: 初回 clone 進行状態(cloneState)を追加。既存行は clone 済み=READY。 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repos ADD COLUMN cloneState TEXT NOT NULL DEFAULT 'READY'")
    }
}

@Database(
    entities = [Repo::class, Memo::class, MemoEntry::class, Favorite::class],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun memoDao(): MemoDao
    abstract fun favoriteDao(): FavoriteDao
}
