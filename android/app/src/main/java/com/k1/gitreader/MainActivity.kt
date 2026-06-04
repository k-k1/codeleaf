package com.k1.gitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.k1.gitreader.ui.GitReaderApp
import com.k1.gitreader.ui.GitReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = (application as GitReaderApplication).container.settingsStore
        setContent {
            // 一覧・追加・設定画面はデフォルトテーマで描画(リポ閲覧中は repo 毎テーマが上書き)。
            val settings by settingsStore.settings.collectAsState()
            GitReaderTheme(settings.defaultTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitReaderApp()
                }
            }
        }
    }
}
