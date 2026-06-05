package jp.lazmix.codeleaf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** searchCorpus(インメモリ全文検索)の JVM 単体テスト。 */
class SearchCorpusTest {

    private val corpus = listOf(
        TextFile("a.md", "needle alpha\nfoo bar"),
        TextFile("b.md", "needle beta"),
    )

    @Test
    fun literal_matchesAcrossFilesWithLineNumbers() {
        val out = searchCorpus(corpus, "needle", regex = false)
        assertNull(out.error)
        assertEquals(
            listOf(
                SearchHit("a.md", 1, "needle alpha"),
                SearchHit("b.md", 1, "needle beta"),
            ),
            out.hits,
        )
    }

    @Test
    fun literal_isCaseInsensitiveAndTracksLine() {
        val out = searchCorpus(corpus, "FOO", regex = false)
        assertEquals(listOf(SearchHit("a.md", 2, "foo bar")), out.hits)
    }

    @Test
    fun regex_matchesPattern() {
        // "be.a" は "beta" にマッチするが "alpha" にはマッチしない
        val out = searchCorpus(corpus, "be.a", regex = true)
        assertNull(out.error)
        assertEquals(listOf(SearchHit("b.md", 1, "needle beta")), out.hits)
    }

    @Test
    fun invalidRegex_returnsError() {
        val out = searchCorpus(corpus, "(", regex = true)
        assertNotNull(out.error)
        assertTrue(out.hits.isEmpty())
    }

    @Test
    fun blankQuery_isEmpty() {
        assertTrue(searchCorpus(corpus, "  ", regex = false).hits.isEmpty())
    }

    @Test
    fun pathFilter_restrictsToMatchingPaths() {
        val out = searchCorpus(corpus, "needle", regex = false, pathFilter = "a.md")
        assertEquals(listOf(SearchHit("a.md", 1, "needle alpha")), out.hits)
    }

    @Test
    fun pathFilter_extensionKeepsAll() {
        val out = searchCorpus(corpus, "needle", regex = false, pathFilter = ".md")
        assertEquals(2, out.hits.size)
    }

    @Test
    fun multiWord_andOrderIndependent() {
        // "alpha needle" は語順に依らず "needle alpha" 行に一致、"needle beta" には一致しない
        val out = searchCorpus(corpus, "alpha needle", regex = false)
        assertEquals(listOf(SearchHit("a.md", 1, "needle alpha")), out.hits)
    }

    @Test
    fun multiWord_requiresAllTerms() {
        // beta は a.md に無いので、両語を含む行は無し
        assertTrue(searchCorpus(corpus, "needle bar", regex = false).hits.isEmpty())
        assertEquals(1, searchCorpus(corpus, "foo bar", regex = false).hits.size)
    }

    @Test
    fun multiWord_regexTermsAreAnded() {
        val out = searchCorpus(corpus, "ne.dle al.ha", regex = true)
        assertEquals(listOf(SearchHit("a.md", 1, "needle alpha")), out.hits)
    }

    @Test
    fun multiWord_invalidRegexTermReturnsError() {
        assertNotNull(searchCorpus(corpus, "needle (", regex = true).error)
    }

    @Test
    fun maxHits_isRespected() {
        val many = listOf(TextFile("x.txt", (1..10).joinToString("\n") { "hit $it" }))
        val out = searchCorpus(many, "hit", regex = false, maxHits = 3)
        assertEquals(3, out.hits.size)
    }
}
