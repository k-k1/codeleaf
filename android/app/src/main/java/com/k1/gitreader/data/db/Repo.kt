package com.k1.gitreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 対応 git ホスト。認証情報の扱い（username 必須かどうか）が分岐する。 */
enum class GitHost { GITHUB, BITBUCKET }

/** リポジトリ毎の表示テーマ。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 登録リポジトリ。token はここには持たず、TokenStore (Keystore暗号化) に id 紐付けで保存する。
 */
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
)
