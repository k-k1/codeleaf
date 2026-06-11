package jp.lazmix.codeleaf.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 開いていたファイル 1 件（detailStack の 1 エントリ）。line は任意ジャンプ先。 */
data class OpenFile(val path: String, val line: Int?)

/**
 * リポ毎の「最後にいた場所」。リポを開き直したときに復元する。
 * - chain : そのリポの Browse フォルダパス列（先頭は必ず ""=ルート / 深い順）
 * - files : 開いていたファイル（detailStack）。多ペインでは複数になりうる。
 * - focus : 集中モード（全幅ビューア）だったか。
 */
data class NavPosition(
    val chain: List<String>,
    val files: List<OpenFile>,
    val focus: Boolean,
) {
    fun toJson(): String = JSONObject().apply {
        put("chain", JSONArray(chain))
        put(
            "files",
            JSONArray().apply {
                files.forEach { f ->
                    put(
                        JSONObject().apply {
                            put("path", f.path)
                            put("line", f.line ?: JSONObject.NULL)
                        },
                    )
                }
            },
        )
        put("focus", focus)
    }.toString()

    companion object {
        fun fromJson(s: String): NavPosition {
            val o = JSONObject(s)
            val chain = o.getJSONArray("chain").let { a -> (0 until a.length()).map { a.getString(it) } }
            val files = o.getJSONArray("files").let { a ->
                (0 until a.length()).map { i ->
                    val fo = a.getJSONObject(i)
                    OpenFile(fo.getString("path"), if (fo.isNull("line")) null else fo.getInt("line"))
                }
            }
            return NavPosition(chain, files, o.optBoolean("focus", false))
        }
    }
}

/**
 * リポ毎のナビ位置を永続化する（SharedPreferences + JSON。SettingsStore/TokenStore と同パターン）。
 * Room マイグレーション不要。アプリ再起動を跨いで保持する。
 */
class NavPositionStore(context: Context) {
    private val prefs = context.getSharedPreferences("nav_positions", Context.MODE_PRIVATE)

    /** 壊れた JSON・空/不正な chain（先頭が "" でない）は null 扱い → 呼び側はルートへフォールバック。 */
    fun get(repoId: Long): NavPosition? {
        val raw = prefs.getString(key(repoId), null) ?: return null
        val pos = runCatching { NavPosition.fromJson(raw) }.getOrNull() ?: return null
        if (pos.chain.isEmpty() || pos.chain.first().isNotEmpty()) return null
        return pos
    }

    fun save(repoId: Long, pos: NavPosition) {
        prefs.edit().putString(key(repoId), pos.toJson()).apply()
    }

    fun clear(repoId: Long) {
        prefs.edit().remove(key(repoId)).apply()
    }

    private fun key(repoId: Long) = "nav_$repoId"
}
