package jp.lazmix.codeleaf.data

import jp.lazmix.codeleaf.data.db.MemoEntry

/**
 * メモの共有/コピー用テキスト整形(Markdown 風)。純粋関数で JVM 単体テスト可能。
 *
 * 1エントリの例:
 * ```
 * [my-app] src/Main.kt L42-43
 * > val x = compute()
 * > return x
 * ここ重い。キャッシュ検討
 * ```
 */
object MemoFormat {

    /** L42 / L42-43 形式の行範囲表記。 */
    fun lineRange(start: Int, end: Int): String =
        if (start >= end) "L$start" else "L$start-$end"

    /** 1エントリを整形。引用は各行を `> ` で、コメントはそのまま続ける。 */
    fun entry(repoName: String, e: MemoEntry): String = buildString {
        append("[$repoName] ${e.filePath} ${lineRange(e.lineStart, e.lineEnd)}")
        if (e.quote.isNotEmpty()) {
            append('\n')
            append(e.quote.split("\n").joinToString("\n") { "> $it" })
        }
        if (e.comment.isNotBlank()) {
            append('\n')
            append(e.comment.trim())
        }
    }

    /** メモ帳全体を整形(見出し + 各エントリを空行区切りで連結)。 */
    fun memo(repoName: String, title: String, entries: List<MemoEntry>): String =
        (listOf("# $title ($repoName)") + entries.map { entry(repoName, it) })
            .joinToString("\n\n")
}
