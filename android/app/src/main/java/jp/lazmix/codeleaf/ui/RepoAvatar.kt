package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.lazmix.codeleaf.data.db.Repo

/** 手動カラー未設定(NONE)時にリポ名から自動割当する色。 */
private val AUTO_AVATAR_COLORS = listOf(
    Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFFFFA726), Color(0xFF26C6DA),
    Color(0xFF7E57C2), Color(0xFF26A69A),
)

/**
 * リポ名から2文字のアバター用ラベルを作る。区切り(- _ / . 空白)で分割し、
 * 複数セグメントなら「先頭セグメント頭文字＋末尾セグメント先頭2文字」、単一なら先頭3文字。
 * 例: g3-ibss→"GIB" / g3-docs→"GDO" / git-reader→"GRE" / api→"API"。
 * 接頭辞が共通でも末尾で区別できるようにする狙い。
 */
internal fun repoAvatarLabel(name: String): String {
    val segs = name.split('-', '_', '/', '.', ' ').filter { it.isNotBlank() }
    val raw = when {
        segs.isEmpty() -> name.take(3)
        segs.size == 1 -> segs[0].take(3)
        else -> segs.first().take(1) + segs.last().take(2)
    }
    return raw.uppercase()
}

/** リポの表示色。手動カラーがあればそれ、無ければ名前ハッシュで自動割当。 */
internal fun repoAvatarColor(repo: Repo): Color =
    repo.colorTag.accent() ?: AUTO_AVATAR_COLORS[hashIndex(repo.name, AUTO_AVATAR_COLORS.size)]

private fun hashIndex(s: String, n: Int): Int {
    var h = 0
    for (c in s) h = h * 31 + c.code
    return ((h % n) + n) % n
}

/** 彩度を落として塗りを落ち着かせる(IconRail のアバターが派手すぎないように)。色相・明度は保つ。 */
private fun Color.muted(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[1] *= 0.6f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 色丸＋2文字ラベルのリポアバター。selected でリングを付ける。 */
@Composable
fun RepoAvatar(repo: Repo, selected: Boolean, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val bg = repoAvatarColor(repo).muted()
    val fg = if (bg.luminance() < 0.5f) Color.White else Color(0xFF1B1B1B)
    val label = repoAvatarLabel(repo.name)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.Bold,
            // 3文字は円に収まるよう少し小さめにする。
            fontSize = (size.value * if (label.length >= 3) 0.30f else 0.34f).sp,
            maxLines = 1,
        )
    }
}
