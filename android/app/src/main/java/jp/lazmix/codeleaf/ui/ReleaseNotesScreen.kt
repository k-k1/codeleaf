package jp.lazmix.codeleaf.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import jp.lazmix.codeleaf.R
import jp.lazmix.codeleaf.render.MarkdownColors
import jp.lazmix.codeleaf.render.MarkdownRenderer

/**
 * リリースノート(CHANGELOG)表示。assets/changelog/{ja,en}.md を Markwon で整形して出す。
 * 本文は ja/en の2言語のみ用意し、現在ロケールが日本語なら ja、それ以外は en を表示する。
 * (ファイルはビルド時に build.gradle.kts の copyChangelog がリポ直下から取り込む)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val isJa = LocalConfiguration.current.locales[0].language == "ja"

    val markdown = remember(isJa) {
        val path = if (isJa) "changelog/ja.md" else "changelog/en.md"
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
    }

    val colors = MarkdownColors(
        link = scheme.primary.toArgb(),
        inlineCodeBg = scheme.surfaceVariant.toArgb(),
        inlineCodeText = scheme.onSurfaceVariant.toArgb(),
        blockQuoteBar = scheme.outline.toArgb(),
        divider = scheme.outlineVariant.toArgb(),
        codeBlockBg = scheme.surfaceVariant.toArgb(),
        codeBlockText = scheme.onSurface.toArgb(),
    )
    val markwon = remember(context, dark, colors) { MarkdownRenderer.create(context, dark, null, colors) }
    val textColor = scheme.onSurface.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_release_notes)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (markdown == null) {
            Text(
                stringResource(R.string.release_notes_unavailable),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                factory = { ctx -> TextView(ctx) },
                update = { tv ->
                    tv.setTextColor(textColor)
                    tv.textSize = 15f
                    markwon.setMarkdown(tv, markdown)
                    tv.movementMethod = LinkMovementMethod.getInstance()
                },
            )
        }
    }
}
