package jp.lazmix.codeleaf.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * 同梱 OSS ライブラリのライセンス一覧。データは aboutlibraries Gradle プラグインが各依存の POM から
 * 収集し R.raw.aboutlibraries へ生成したものを [LibrariesContainer] が読み込む(手書き保守なし)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("オープンソースライセンス") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LibrariesContainer(Modifier.fillMaxSize().padding(padding))
    }
}
