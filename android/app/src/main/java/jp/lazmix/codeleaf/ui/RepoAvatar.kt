package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** CJK/全角(日本語・中国語・韓国語・全角記号)を含むか。全角は円に収まる文字数が少ない。 */
private fun Char.isWide(): Boolean {
    val c = code
    return c in 0x3000..0x30FF ||   // CJK記号・ひらがな・カタカナ
        c in 0x3400..0x9FFF ||      // CJK拡張A＋統合漢字
        c in 0xAC00..0xD7A3 ||      // ハングル音節
        c in 0xF900..0xFAFF ||      // CJK互換漢字
        c in 0xFF00..0xFF60 || c in 0xFFE0..0xFFE6 // 全角英数・記号
}

/**
 * リポ名からアバター用ラベルを作る。区切り(- _ / . 空白)で分割し、
 * 半角(ラテン)は3文字、全角(CJK)は円に収まるよう2文字にする。
 * 複数セグメントなら「先頭セグメント頭文字＋末尾セグメントの残り」、単一なら先頭から。
 * 例: g3-ibss→"GIB" / git-reader→"GRE" / api→"API" / メモ帳ツール→"メモ" / 日本語→"日本"。
 * 接頭辞が共通でも末尾で区別できるようにする狙い。
 */
internal fun repoAvatarLabel(name: String): String {
    val segs = name.split('-', '_', '/', '.', ' ').filter { it.isNotBlank() }
    val n = if (name.any { it.isWide() }) 2 else 3
    val raw = when {
        segs.isEmpty() -> name.take(n)
        segs.size == 1 -> segs[0].take(n)
        else -> segs.first().take(1) + segs.last().take(n - 1)
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
    hsv[1] *= 0.45f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 同じ色相で濃い(彩度上げ・明度下げ)版。選択中リポの縁取りに使う。 */
private fun Color.deep(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
    hsv[2] *= 0.55f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 色丸＋2文字ラベルのリポアバター。selected でリングを付ける。 */
@Composable
fun RepoAvatar(repo: Repo, selected: Boolean, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val base = repoAvatarColor(repo)
    val bg = base.muted()
    val fg = if (bg.luminance() < 0.5f) Color.White else Color(0xFF1B1B1B)
    val label = repoAvatarLabel(repo.name)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                // 選択中はそのリポ色の濃い版で縁取り(リポと結び付けつつ目立たせる)。
                if (selected) {
                    Modifier.border(3.dp, base.deep(), CircleShape)
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
