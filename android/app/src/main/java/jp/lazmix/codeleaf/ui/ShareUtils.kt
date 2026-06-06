package jp.lazmix.codeleaf.ui

import android.content.Context
import android.content.Intent

/** プレーンテキストを共有シート(ACTION_SEND)で送る。メッセンジャー等への共有に使う。 */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
