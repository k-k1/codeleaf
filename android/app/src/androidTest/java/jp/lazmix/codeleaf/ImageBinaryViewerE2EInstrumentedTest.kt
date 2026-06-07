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
 * 画像/バイナリ/テキストの種別表示 E2E。ローカル git リポに実画像(PNG)・非画像バイナリ(PDF)・
 * Shift_JIS+CRLF のテキストを入れて clone し、(1)画像は描画ビュー(AsyncImage)＋上部メタバー
 * (PNG/寸法)、(2)PDF は file(1) 風の種別ラベル、(3)テキストは推定エンコード/改行コードのメタバーが
 * 出ること(=コード表示 CodeView に落ちないこと)を検証する。
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
            File(dir, "README.md").writeText("# imgbin\n\nopen pic.png / doc.pdf / sjis.txt\n")
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
        // 画像ビューアは AsyncImage の contentDescription にファイル名を持つ(CodeView ではない)。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("pic.png").fetchSemanticsNodes().isNotEmpty()
        }
        // 上部メタバーにフォーマットと寸法。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("PNG ・ 240×160", substring = true).fetchSemanticsNodes().isNotEmpty()
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

    @Test
    fun openText_showsEncodingAndEolMetaBar() {
        compose.onNodeWithText("sjis.txt").performClick()
        // 上部メタバーに推定エンコードと改行コード(Shift_JIS で再デコードされ本文も化けない)。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("SHIFT_JIS", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("CRLF", substring = true).assertIsDisplayed()
    }
}
