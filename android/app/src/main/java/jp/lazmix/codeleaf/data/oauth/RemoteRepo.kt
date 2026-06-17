package jp.lazmix.codeleaf.data.oauth

import org.json.JSONObject

/** リモートリポ1件（一覧選択用・GitHub/Bitbucket 共通）。cloneUrl は userinfo を除いた HTTPS。 */
data class RemoteRepo(val fullName: String, val name: String, val cloneUrl: String)

/** リポ JSON 1件 + clone URL から RemoteRepo を作る。full_name か clone URL が空なら null。GitHub/Bitbucket 共通。 */
internal fun buildRemoteRepo(r: JSONObject, cloneUrl: String?): RemoteRepo? {
    val fullName = r.optString("full_name", "")
    if (fullName.isEmpty() || cloneUrl.isNullOrEmpty()) return null
    val name = r.optString("name", "").ifEmpty { fullName.substringAfterLast('/') }
    return RemoteRepo(fullName, name, stripUserInfo(cloneUrl))
}

/** `https://user@host/...` の userinfo を除去する。 */
fun stripUserInfo(url: String): String = url.replace(Regex("://[^/@]*@"), "://")

/** clone URL を比較用に正規化（userinfo/.git/末尾スラッシュ除去・小文字化）。 */
fun normalizeRepoUrl(url: String): String {
    var s = url.trim().substringBefore('?').substringBefore('#')
    s = stripUserInfo(s).trimEnd('/')
    if (s.endsWith(".git")) s = s.dropLast(4)
    return s.lowercase()
}
