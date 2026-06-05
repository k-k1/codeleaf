package jp.lazmix.codeleaf.ui

import androidx.compose.ui.graphics.Color
import jp.lazmix.codeleaf.data.db.RepoColor

/** プリセットのカード色。NONE は色なし(null)。 */
fun RepoColor.accent(): Color? = when (this) {
    RepoColor.NONE -> null
    RepoColor.BLUE -> Color(0xFF42A5F5)
    RepoColor.GREEN -> Color(0xFF66BB6A)
    RepoColor.RED -> Color(0xFFEF5350)
    RepoColor.PURPLE -> Color(0xFFAB47BC)
    RepoColor.ORANGE -> Color(0xFFFFA726)
    RepoColor.TEAL -> Color(0xFF26C6DA)
}
