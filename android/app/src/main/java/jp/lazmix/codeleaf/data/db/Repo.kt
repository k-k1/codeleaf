package jp.lazmix.codeleaf.data.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/** 対応 git ホスト。認証情報の扱い（username 必須かどうか）が分岐する。 */
enum class GitHost { GITHUB, BITBUCKET }

/** 認証種別。TOKEN=手入力 PAT/API token、OAUTH=OAuth ログイン(access/refresh を TokenStore に保存)。 */
enum class AuthType { TOKEN, OAUTH }

/** リポジトリ毎の表示テーマ。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** リポジトリ一覧カードのアクセント色(プリセット)。NONE は色なし。 */
enum class RepoColor { NONE, BLUE, GREEN, RED, PURPLE, ORANGE, TEAL }

/**
 * 初回 clone の進行状態。CLONING=登録済みで clone 実行中（一覧では進捗パネル）、
 * READY=clone 完了で閲覧可、FAILED=clone 失敗（再試行 or 削除）。
 * プロセス死で CLONING のまま残った行は起動時に FAILED へ倒す。
 */
enum class CloneState { CLONING, READY, FAILED }

/**
 * 登録リポジトリ。token はここには持たず、TokenStore (Keystore暗号化) に id 紐付けで保存する。
 */
@Parcelize
@Entity(tableName = "repos")
data class Repo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val host: GitHost,
    val username: String,
    val branch: String,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastSyncedAt: Long? = null,
    val sortOrder: Int = 0,
    val colorTag: RepoColor = RepoColor.NONE,
    val authType: AuthType = AuthType.TOKEN,
    /** 所属グループ(Working Set)。空=未分類。一覧はこの値で絞り込む。 */
    val groupName: String = "",
    /** 初回 clone の進行状態。既存(clone 済み)行は READY 既定。 */
    val cloneState: CloneState = CloneState.READY,
) : Parcelable
