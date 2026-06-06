package jp.lazmix.codeleaf

import android.app.Application
import androidx.room.Room
import jp.lazmix.codeleaf.data.MemoRepository
import jp.lazmix.codeleaf.data.RepoRepository
import jp.lazmix.codeleaf.data.SettingsStore
import jp.lazmix.codeleaf.data.crypto.TokenStore
import jp.lazmix.codeleaf.data.db.AppDatabase
import jp.lazmix.codeleaf.data.db.MIGRATION_1_2
import jp.lazmix.codeleaf.data.db.MIGRATION_2_3
import jp.lazmix.codeleaf.data.db.MIGRATION_3_4
import jp.lazmix.codeleaf.data.db.MIGRATION_4_5
import jp.lazmix.codeleaf.data.oauth.BitbucketOAuthService
import jp.lazmix.codeleaf.data.oauth.BitbucketOAuthTokenExchanger
import jp.lazmix.codeleaf.data.oauth.GitHubDeviceFlowService
import jp.lazmix.codeleaf.data.oauth.OAuthAccount
import jp.lazmix.codeleaf.data.oauth.OAuthSessionStore
import jp.lazmix.codeleaf.git.JgitClient
import kotlinx.coroutines.channels.Channel
import java.io.File

/** 手動 DI コンテナ（v1 は DI ライブラリ未使用）。 */
class AppContainer(app: Application) {
    private val db: AppDatabase = Room.databaseBuilder(
        app, AppDatabase::class.java, "codeleaf.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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

    val memoRepository: MemoRepository = MemoRepository(db.memoDao())
}

class GitReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
