package com.k1.gitreader

import android.app.Application
import androidx.room.Room
import com.k1.gitreader.data.RepoRepository
import com.k1.gitreader.data.SettingsStore
import com.k1.gitreader.data.crypto.TokenStore
import com.k1.gitreader.data.db.AppDatabase
import com.k1.gitreader.git.JgitClient
import java.io.File

/** 手動 DI コンテナ（v1 は DI ライブラリ未使用）。 */
class AppContainer(app: Application) {
    private val db: AppDatabase = Room.databaseBuilder(
        app, AppDatabase::class.java, "gitreader.db",
    ).build()

    private val reposRoot: File = File(app.filesDir, "repos").apply { mkdirs() }

    val repoRepository: RepoRepository = RepoRepository(
        dao = db.repoDao(),
        tokenStore = TokenStore(app),
        jgit = JgitClient(),
        reposRoot = reposRoot,
    )

    val settingsStore: SettingsStore = SettingsStore(app)
}

class GitReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
