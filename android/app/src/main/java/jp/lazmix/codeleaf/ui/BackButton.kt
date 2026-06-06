package jp.lazmix.codeleaf.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/** TopAppBar の navigationIcon に置く共通の「戻る」ボタン。各画面で重複していた IconButton+Icon を集約する。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
    }
}
