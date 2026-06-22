package jp.lazmix.codeleaf.git

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ディレクトリ最終コミット解決の突合に使う matchesPathPrefix の純粋関数テスト。 */
class MatchesPathPrefixTest {

    @Test fun exactFile_matches() {
        assertTrue(matchesPathPrefix("src/App.kt", "src/App.kt"))
    }

    @Test fun underFolder_matches() {
        assertTrue(matchesPathPrefix("src/main/App.kt", "src"))
        assertTrue(matchesPathPrefix("src/main/App.kt", "src/main"))
    }

    @Test fun siblingPrefix_doesNotMatch() {
        // "src2" は "src" の配下ではない(プレフィックス文字列の誤一致を防ぐ)。
        assertFalse(matchesPathPrefix("src2/App.kt", "src"))
    }

    @Test fun unrelated_doesNotMatch() {
        assertFalse(matchesPathPrefix("docs/x.md", "src"))
    }

    @Test fun folderItself_withoutChildren_doesNotMatchAsFile() {
        // 変更パスがちょうどフォルダ名のみ(=gitlink 等)は完全一致でのみ真。
        assertTrue(matchesPathPrefix("sub", "sub"))
        assertFalse(matchesPathPrefix("sub", "subdir"))
    }
}
