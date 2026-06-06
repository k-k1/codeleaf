package jp.lazmix.codeleaf.ui

/** ざっくり相対時刻（日本語）。 */
fun relativeTimeMillis(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    val min = diff / 60_000
    val hour = min / 60
    val day = hour / 24
    return when {
        min < 1 -> "たった今"
        min < 60 -> "${min}分前"
        hour < 24 -> "${hour}時間前"
        day < 30 -> "${day}日前"
        day < 365 -> "${day / 30}か月前"
        else -> "${day / 365}年前"
    }
}

/** コミット SHA の短縮表示（先頭 7 桁）。各画面で重複していた `sha.take(7)` を集約する。 */
fun shortSha(sha: String): String = sha.take(7)
