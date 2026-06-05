package com.k1.gitreader

import android.app.Application
import androidx.room.Room
import com.k1.gitreader.data.RepoRepository
import com.k1.gitreader.data.SettingsStore
import com.k1.gitreader.data.crypto.TokenStore
import com.k1.gitreader.data.db.AppDatabase
import com.k1.gitreader.data.db.MIGRATION_1_2
import com.k1.gitreader.data.db.MIGRATION_2_3
import com.k1.gitreader.data.oauth.BitbucketOAuthService
import com.k1.gitreader.data.oauth.BitbucketOAuthTokenExchanger
import com.k1.gitreader.data.oauth.GitHubDeviceFlowService
import com.k1.gitreader.data.oauth.OAuthAccount
import com.k1.gitreader.data.oauth.OAuthSessionStore
import com.k1.gitreader.git.JgitClient
import kotlinx.coroutines.channels.Channel
import java.io.File

/** 手動 DI コンテナ（v1 は DI ライブラリ未使用）。 */
class AppContainer(app: Application) {
    private val db: AppDatabase = Room.databaseBuilder(
        app, AppDatabase::class.java, "gitreader.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    private val reposRoot: File = File(app.filesDir, "repos").apply { mkdirs() }

    // OAuth: client_id/secret が BuildConfig(local.properties 由来) に揃っていれば有効化。
    val bitbucketOAuthService: BitbucketOAuthService? =
        if (BuildConfig.BITBUCKET_OAUTH_CLIENT_ID.isNotBlank() &&
            BuildConfig.BITBUCKET_OAUTH_CLIENT_SECRET.isNotBlank()
        ) {
            BitbucketOAuthService(
                exchanger = BitbucketOAuthTokenExchanger(
                    BuildConfig.BITBUCKET_OAUTH_CLIENT_ID,
                    BuildConfig.BITBUCKET_OAUTH_CLIENT_SECRET,
                ),
                session = OAuthSessionStore(app),
                clientId = BuildConfig.BITBUCKET_OAUTH_CLIENT_ID,
            )
        } else {
            null
        }

    // GitHub: client_id が BuildConfig にあれば Device Flow を有効化（secret 不要）。
    val githubOAuthService: GitHubDeviceFlowService? =
        if (BuildConfig.GITHUB_OAUTH_CLIENT_ID.isNotBlank()) {
            GitHubDeviceFlowService(clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID)
        } else {
            null
        }

    /** redirect Activity → UI への OAuth 結果受け渡し（1 回ずつ消費）。 */
    val oauthResults = Channel<Result<OAuthAccount>>(Channel.BUFFERED)

    val repoRepository: RepoRepository = RepoRepository(
        dao = db.repoDao(),
        tokenStore = TokenStore(app),
        jgit = JgitClient(),
        reposRoot = reposRoot,
        refreshOAuth = bitbucketOAuthService?.let { svc -> { acc -> svc.refresh(acc) } },
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
