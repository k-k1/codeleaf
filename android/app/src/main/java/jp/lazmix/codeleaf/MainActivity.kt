package jp.lazmix.codeleaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import jp.lazmix.codeleaf.data.db.ThemeMode
import jp.lazmix.codeleaf.ui.CodeLeafApp
import jp.lazmix.codeleaf.ui.CodeLeafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // システムバーは透過(edge-to-edge)。各画面の Scaffold/IconRail/SlimBottomBar が
        // status/navigation の inset を負担するので、本体はバー下に潜らない。
        enableEdgeToEdge()
        val settingsStore = (application as CodeLeafApplication).container.settingsStore
        setContent {
            // 一覧・追加・設定画面はデフォルトテーマで描画(リポ閲覧中は repo 毎テーマが上書き)。
            val settings by settingsStore.settings.collectAsState()
            // ステータス/ナビバーのアイコン色を現在テーマの明暗に追従(明テーマで白アイコンが
            // 白背景に溶けて見えなくなるのを防ぐ)。
            val dark = when (settings.defaultTheme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            CodeLeafTheme(settings.defaultTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CodeLeafApp()
                }
            }
        }
    }
}
