package jp.lazmix.codeleaf

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import jp.lazmix.codeleaf.ui.CodeLeafApp
import jp.lazmix.codeleaf.ui.CodeLeafTheme

// per-app 言語(AppCompatDelegate.setApplicationLocales)を API31/32 でもバックポートで効かせるため
// ComponentActivity ではなく AppCompatActivity を基底にする(描画は従来どおり Compose)。
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // システムバーは透過(edge-to-edge)。各画面の Scaffold/IconRail/SlimBottomBar が
        // status/navigation の inset を負担するので、本体はバー下に潜らない。
        enableEdgeToEdge()
        val settingsStore = (application as CodeLeafApplication).container.settingsStore
        setContent {
            // 一覧・追加・設定画面はデフォルトテーマで描画(リポ閲覧中は repo 毎テーマが上書き)。
            // システム/ステータスバーのアイコン明暗は、前面画面のテーマに合わせて CodeLeafApp 側で
            // 制御する(別テーマ選択時にバーのアイコンが背景へ溶けるのを防ぐため一元化)。
            val settings by settingsStore.settings.collectAsState()
            CodeLeafTheme(settings.defaultTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CodeLeafApp()
                }
            }
        }
    }
}
