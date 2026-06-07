package jp.lazmix.codeleaf.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF を PdfRenderer でページ描画する読み取り専用ビューア。ページは縦スクロールで連続表示し、
 * 各ページは表示幅に合わせて遅延レンダリングする。−/＋ で表示倍率(1〜3 倍)を切替え、
 * 等倍超では横スクロールで全幅を見られる。ピンチズームは未対応(倍率ボタンで代替)。
 */
@Composable
fun PdfViewer(file: File, modifier: Modifier = Modifier) {
    val session = remember(file.path) { runCatching { PdfSession(file) }.getOrNull() }
    DisposableEffect(file.path) { onDispose { session?.close() } }

    if (session == null || session.pageCount <= 0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("PDF を開けません", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var zoom by remember(file.path) { mutableIntStateOf(1) } // 1..3

    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewportPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
            // 倍率を掛けた幅でレンダリング。メモリ保護のため上限でクランプ。
            val targetW = (viewportPx * zoom).coerceAtMost(MAX_PAGE_WIDTH_PX)
            val targetWdp = with(density) { targetW.toDp() }
            val hScroll = rememberScrollState()

            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (zoom > 1) Modifier.horizontalScroll(hScroll) else Modifier),
            ) {
                LazyColumn(
                    Modifier.width(targetWdp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(session.pageCount) { i ->
                        PdfPageView(session, i, targetW)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // 倍率コントロール(右下オーバーレイ)。
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (zoom > 1) zoom-- },
                    enabled = zoom > 1,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("${zoom}x", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { if (zoom < MAX_ZOOM) zoom++ },
                    enabled = zoom < MAX_ZOOM,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

/** 1 ページを [widthPx] 幅で描画して表示する。アスペクト比は既知なので描画前から高さが安定する。 */
@Composable
private fun PdfPageView(session: PdfSession, index: Int, widthPx: Int) {
    var bmp by remember(index, widthPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        bmp = session.renderPage(index, widthPx)?.asImageBitmap()
    }
    val density = LocalDensity.current
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { (widthPx * session.aspect(index)).toInt().toDp() }
    Box(
        Modifier.width(widthDp).height(heightDp).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * PdfRenderer を1つ抱える描画セッション。PdfRenderer は同時に1ページしか開けないため、
 * ページ描画は Mutex で直列化する。各ページの寸法は生成時に控えておきレイアウトを安定させる。
 */
private class PdfSession(file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    val pageCount: Int = renderer.pageCount

    // (幅, 高さ)を先に控える。openPage は排他なので生成時に一度だけ。
    private val sizes: Array<Pair<Int, Int>> =
        Array(pageCount) { i -> renderer.openPage(i).use { it.width to it.height } }
    private val mutex = Mutex()

    /** ページの高さ/幅(レイアウト用)。 */
    fun aspect(index: Int): Float {
        val (w, h) = sizes[index]
        return if (w > 0) h.toFloat() / w else 1.414f
    }

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val (w, h) = sizes[index]
                val targetH = if (w > 0) (widthPx.toLong() * h / w).toInt().coerceAtLeast(1) else widthPx
                // PdfRenderer.render は ARGB_8888 必須。背景は白で塗ってから描画する。
                val bitmap = Bitmap.createBitmap(widthPx, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                renderer.openPage(index).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                bitmap
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

private const val MAX_ZOOM = 3
private const val MAX_PAGE_WIDTH_PX = 2200
