package com.k1.gitreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun hostToString(h: GitHost): String = h.name
    @TypeConverter fun stringToHost(s: String): GitHost = GitHost.valueOf(s)
    @TypeConverter fun themeToString(t: ThemeMode): String = t.name
    @TypeConverter fun stringToTheme(s: String): ThemeMode = ThemeMode.valueOf(s)
}

@Database(entities = [Repo::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
}
