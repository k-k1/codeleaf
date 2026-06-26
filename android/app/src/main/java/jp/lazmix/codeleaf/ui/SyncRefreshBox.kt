package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * pull-to-refresh で [onSync] を実行し、[onReload] で表示を再読込して結果スナックバーを出す共通ボックス。
 * [onSync] が null、または更新中は何もしない。`refreshing` 状態はこの中で完結する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRefreshBox(
    snackbar: SnackbarHostState,
    onSync: (suspend () -> Unit)?,
    onReload: suspend () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current // コルーチン内で snackbar 文言をロケール解決するため事前に捕捉。
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (onSync == null || refreshing) return@PullToRefreshBox
            scope.launch {
                refreshing = true
                val result = runCatching { onSync() }
                runCatching { onReload() } // 再読込の失敗は各画面が error 表示で扱う
                refreshing = false
                snackbar.showSnackbar(syncResultUiText(result.exceptionOrNull()).resolve(context))
            }
        },
        modifier = modifier,
        content = content,
    )
}
