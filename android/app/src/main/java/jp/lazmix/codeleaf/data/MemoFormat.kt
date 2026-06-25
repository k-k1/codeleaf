package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.MemoEntry

/**
 * メモの共有/コピー用テキスト整形(Markdown 風)。純粋関数で JVM 単体テスト可能。
 *
 * メモ帳全体はリポ→ファイル単位でグルーピングし、各ファイル内は行番号順。引用は最大3行で省略(...)。
 * 例:
 * ```
 * # レビュー (my-app)
 *
 * ## [my-app] src/Main.kt
 * ### L42
 * > val x = 1
 * 重い
 *
 * ### L132
 * > val y = 2
 * ここも
 *
 * ## [my-app] README.md
 * ### L1
 * > # My App
 * 古い
 * ```
 */
object MemoFormat {

    /** 引用の表示上限行数。超過分は `> ...` で省略する。 */
    private const val MAX_QUOTE_LINES = 3

    /** L42 / L42-43 形式の行範囲表記。 */
    fun lineRange(start: Int, end: Int): String =
        if (start >= end) "L$start" else "L$start-$end"

    /** 引用を最大 [MAX_QUOTE_LINES] 行で各行 `> ` 引用。超過時は末尾に `> ...` を足す。 */
    private fun quoteBlock(quote: String): String {
        val lines = quote.split("\n")
        val shown = lines.take(MAX_QUOTE_LINES).map { "> $it" }
        return (if (lines.size > MAX_QUOTE_LINES) shown + "> ..." else shown).joinToString("\n")
    }

    /** 1エントリの本体(### 行範囲 + 引用 + コメント)。ファイル見出し(##)は呼び出し側で付ける。 */
    private fun entryBody(e: MemoEntry): String = buildString {
        append("### ${lineRange(e.lineStart, e.lineEnd)}")
        if (e.quote.isNotEmpty()) {
            append('\n')
            append(quoteBlock(e.quote))
        }
        if (e.comment.isNotBlank()) {
            append('\n')
            append(e.comment.trim())
        }
    }

    /** 単一エントリの共有/コピー用(ファイル見出し付きで自己完結)。 */
    fun entry(repoName: String, e: MemoEntry): String =
        "## [$repoName] ${e.filePath}\n" + entryBody(e)

    /** メモ帳全体を整形(見出し + ファイル毎にグルーピング・各ファイル内は行番号順)。 */
    fun memo(repoName: String, title: String, entries: List<MemoEntry>): String {
        // ファイル単位にまとめる(出現順を保持)。各ファイル内は行番号順に並べ替える。
        val byFile = LinkedHashMap<String, MutableList<MemoEntry>>()
        for (e in entries) byFile.getOrPut(e.filePath) { mutableListOf() }.add(e)
        val blocks = byFile.map { (file, es) ->
            val sorted = es.sortedWith(compareBy({ it.lineStart }, { it.lineEnd }))
            "## [$repoName] $file\n" + sorted.joinToString("\n\n") { entryBody(it) }
        }
        return (listOf("# $title ($repoName)") + blocks).joinToString("\n\n")
    }
}
