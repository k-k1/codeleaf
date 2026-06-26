package jp.lazmix.codeleaf.ui

import android.content.Context
import jp.lazmix.codeleaf.R

/** ざっくり相対時刻。表示文言は [context] からロケール解決する(各画面 LocalContext を渡す)。 */
fun relativeTimeMillis(epochMillis: Long, context: Context): String {
    val diff = System.currentTimeMillis() - epochMillis
    val min = diff / 60_000
    val hour = min / 60
    val day = hour / 24
    return when {
        min < 1 -> context.getString(R.string.time_just_now)
        min < 60 -> context.getString(R.string.time_min_ago, min)
        hour < 24 -> context.getString(R.string.time_hour_ago, hour)
        day < 30 -> context.getString(R.string.time_day_ago, day)
        day < 365 -> context.getString(R.string.time_month_ago, day / 30)
        else -> context.getString(R.string.time_year_ago, day / 365)
    }
}

/** コミット SHA の短縮表示（先頭 7 桁）。各画面で重複していた `sha.take(7)` を集約する。 */
fun shortSha(sha: String): String = sha.take(7)
