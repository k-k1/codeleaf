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
import kotlinx.coroutines.launch

/** 同期結果のスナックバー文言。失敗は例外メッセージ付き、成功は「同期完了」。各画面・VM で共通。 */
fun syncResultMessage(error: Throwable?): String =
    error?.let { "同期失敗: ${it.message}" } ?: "同期完了"

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
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (onSync == null || refreshing) return@PullToRefreshBox
            scope.launch {
                refreshing = true
                val result = runCatching { onSync() }
                runCatching { onReload() } // 再読込の失敗は各画面が error 表示で扱う
                refreshing = false
                snackbar.showSnackbar(syncResultMessage(result.exceptionOrNull()))
            }
        },
        modifier = modifier,
        content = content,
    )
}
