package jp.lazmix.codeleaf.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.lazmix.codeleaf.R

/**
 * 「表示時に解決するテキスト」。ViewModel など Context/Compose を持たない層では翻訳前の
 * メッセージ([Res]/[GitError])を組み立て、表示側で [asString]（Compose）または
 * [resolve]（Context あり・コルーチンの snackbar 等）でロケールに解決する。
 */
sealed interface UiText {
    /** 既に確定した文字列(例外メッセージ等・翻訳しない)。 */
    data class Raw(val value: String) : UiText

    /** リソース ID と書式引数。 */
    data class Res(val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * "<prefix>: <分類済み or 生メッセージ>" 形式の git エラー。
     * [kind] があればその分類文を、無ければ [rawFallback]（例外 message）を [prefixId] に差し込む。
     */
    data class GitError(val prefixId: Int, val kind: GitErrorKind?, val rawFallback: String?) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
        is GitError -> {
            val detail = kind?.let { context.getString(it.resId) }
                ?: rawFallback
                ?: context.getString(R.string.error_unknown)
            context.getString(prefixId, detail)
        }
    }
}

/** Compose から UiText を文字列へ解決する。 */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
