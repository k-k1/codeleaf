package jp.lazmix.codeleaf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 画像/バイナリビューア E2E。ローカル git リポに実画像(PNG)と非画像バイナリ(PDF)を入れて clone し、
 * 画像を開くと描画ビュー(AsyncImage・contentDescription=ファイル名)が、PDF を開くと file(1) 風の
 * 種別ラベルが出ることを検証する(どちらもコード表示=CodeView には落ちない)。
 */
@RunWith(AndroidJUnit4::class)
class ImageBinaryViewerE2EInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GitReaderApplication>()

    @Before
    fun setUp() {
        app.cleanRepos()
        val src = app.gitRepo("imgbin-src") { git, dir ->
            File(dir, "README.md").writeText("# imgbin\n\nopen pic.png / doc.pdf\n")
            // 青地に黄色い円を描いた 240x160 の実 PNG。
            val bmp = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply {
                drawColor(Color.rgb(0x33, 0x66, 0xCC))
                drawCircle(120f, 80f, 50f, Paint().apply { color = Color.YELLOW })
            }
            File(dir, "pic.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // PDF magic + NUL を含む擬似バイナリ(「PDF 文書」と判定される)。
            File(dir, "doc.pdf").writeBytes(
                "%PDF-1.7\n".toByteArray() + byteArrayOf(0, 1, 2, 3, 0x0A, 0x25),
            )
            git.commitAll("init")
        }
        app.addFixtureRepo("imgbin", src)
        compose.onNodeWithText("imgbin").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("pic.png").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun openImage_showsImageViewer() {
        compose.onNodeWithText("pic.png").performClick()
        // 画像ビューアは AsyncImage の contentDescription にファイル名を持つ(CodeView ではない)。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("pic.png").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun openBinary_showsTypeLabel() {
        compose.onNodeWithText("doc.pdf").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("PDF 文書").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PDF 文書").assertIsDisplayed()
    }
}
