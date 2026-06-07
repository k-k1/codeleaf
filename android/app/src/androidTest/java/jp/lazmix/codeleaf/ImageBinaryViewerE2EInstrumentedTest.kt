package jp.lazmix.codeleaf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
 * 種別別ビューア E2E。ローカル git リポに実画像(PNG)・実 PDF・非画像バイナリ(gzip)・
 * Shift_JIS+CRLF テキストを入れて clone し、それぞれ画像描画＋メタバー / PDF ビューア /
 * file(1) 風ラベル / 推定エンコード・改行コードのメタバーが出ること(=どれもコード表示に落ちない)を
 * 検証する。
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
            File(dir, "README.md").writeText("# imgbin\n\nopen pic.png / doc.pdf / blob.bin / sjis.txt\n")
            // 青地に黄色い円を描いた 240x160 の実 PNG。
            val bmp = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply {
                drawColor(Color.rgb(0x33, 0x66, 0xCC))
                drawCircle(120f, 80f, 50f, Paint().apply { color = Color.YELLOW })
            }
            File(dir, "pic.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // PdfRenderer で開ける実 PDF を 1 ページ生成。
            writePdf(File(dir, "doc.pdf"))
            // gzip magic + NUL の擬似バイナリ(「gzip 圧縮」と判定される)。
            File(dir, "blob.bin").writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x00, 0x01))
            // Shift_JIS + CRLF の日本語テキスト(自動判定 + 改行コード検証用)。
            val sjis = "日本語のテキストです。\r\nシフトJISで保存しています。\r\n".repeat(4)
            File(dir, "sjis.txt").writeBytes(sjis.toByteArray(charset("Shift_JIS")))
            git.commitAll("init")
        }
        app.addFixtureRepo("imgbin", src)
        compose.onNodeWithText("imgbin").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("pic.png").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun openImage_showsImageViewerAndMetaBar() {
        compose.onNodeWithText("pic.png").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("pic.png").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("PNG ・ 240×160", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun openPdf_showsPdfViewer() {
        compose.onNodeWithText("doc.pdf").performClick()
        // PDF ビューアが開けば倍率コントロール「1x」とメタバー「PDF ・」が出る(概要カードではない)。
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1x").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("PDF ・", substring = true).assertIsDisplayed()
    }

    @Test
    fun openBinary_showsTypeLabel() {
        compose.onNodeWithText("blob.bin").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("gzip 圧縮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("gzip 圧縮").assertIsDisplayed()
    }

    @Test
    fun openText_showsEncodingAndEolMetaBar() {
        compose.onNodeWithText("sjis.txt").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("SHIFT_JIS", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("CRLF", substring = true).assertIsDisplayed()
    }

    /** PdfRenderer で開ける最小の 1 ページ PDF を [file] に書く。 */
    private fun writePdf(file: File) {
        val pdf = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(300, 400, 1).create()
        val page = pdf.startPage(info)
        page.canvas.apply {
            drawColor(Color.WHITE)
            drawRect(40f, 40f, 260f, 200f, Paint().apply { color = Color.rgb(0xCC, 0x33, 0x33) })
            drawText("PDF Page 1", 50f, 300f, Paint().apply { color = Color.BLACK; textSize = 28f })
        }
        pdf.finishPage(page)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }
}
