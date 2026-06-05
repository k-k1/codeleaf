package jp.lazmix.codeleaf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * `codeleaf://oauth?...` の redirect を受ける透明 Activity。
 * code/state を交換し結果を AppContainer.oauthResults へ送って即 finish する。
 * MainActivity(単一Activity Compose ナビ)には副作用を入れない。
 */
class OAuthRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent?.data)
    }

    // launchMode=singleTask: 既存インスタンスがある場合は onNewIntent に来る。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent.data)
    }

    private fun handle(data: Uri?) {
        val container = (application as GitReaderApplication).container
        val service = container.bitbucketOAuthService
        if (data == null || service == null) {
            finish()
            return
        }
        val query = data.queryParameterNames.associateWith { data.getQueryParameter(it).orEmpty() }
        lifecycleScope.launch {
            val result = service.completeAuthorization(query)
            container.oauthResults.send(result)
            finish()
        }
    }
}
