package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.lazmix.codeleaf.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Material3 `BottomAppBar`(既定 80dp)は目次/パンくず程度の用途には高すぎるため、
 * ナビゲーションバーのインセットは尊重しつつ高さを詰めた薄いボトムバー。
 */
@Composable
fun SlimBottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Scaffold の既定コンテンツインセットから navigationBars を除いたもの。本文が自前の下部バーで
 * navigationBars を padding する画面で、インセットの二重計上(バー下の余白が二重に入る)を防ぐ。
 */
val contentInsetsExcludingNavBar: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)

/**
 * 処理中(ブランチ切替など)に画面全面を覆い、タッチを消費しつつ中央にスピナーを出すオーバーレイ。
 * 呼び出し側は最前面に `if (busy) BusyBlockingOverlay()` のように重ねて使う。
 */
@Composable
fun BusyBlockingOverlay(text: String = stringResource(R.string.bottombar_switching)) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.White)
        }
    }
}
