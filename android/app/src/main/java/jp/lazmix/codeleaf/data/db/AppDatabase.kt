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

@Database(entities = [Repo::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
}
