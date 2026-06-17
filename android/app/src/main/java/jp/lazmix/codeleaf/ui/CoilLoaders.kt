package jp.lazmix.codeleaf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.SvgDecoder

/**
 * SVG をデコードできる Coil ImageLoader。対象(アイコンのアセットや SVG ファイル)は小さく、
 * Application 単位で 1 つあれば十分なので applicationContext から remember する。
 */
@Composable
internal fun rememberSvgLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}
