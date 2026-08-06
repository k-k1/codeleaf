package jp.lazmix.codeleaf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MarkdownRenderer.repairFullwidthTables / repairFullwidthTablesCore の JVM 単体テスト。
 * 参照実装 agent-fleet console の markdown.test.ts を移植し、課題で見つかった実データの
 * 4パターン(A-D)と反例3種を固定フィクスチャとして加えている。
 */
class RepairFullwidthTablesTest {

    private fun core(s: String) = MarkdownRenderer.repairFullwidthTablesCore(s)

    // ---- 参照実装の移植 ----

    @Test
    fun rewritesTableWrittenEntirelyWithFullwidthPipes() {
        val r = core("｜章｜点｜\n｜---｜---｜\n｜A1｜6.5｜\n")
        assertEquals("|章|点|\n|---|---|\n|A1|6.5|\n", r?.body)
        assertEquals(listOf(0), r?.repaired)
        assertEquals(1, r?.total)
    }

    @Test
    fun repairsWhenOnlyDelimiterRowIsAscii() {
        assertEquals(
            "|章|点|\n|---|---|\n|A1|6.5|",
            core("｜章｜点｜\n|---|---|\n｜A1｜6.5｜")?.body,
        )
    }

    @Test
    fun repairsHalfConvertedTableWhereOnlyHeaderIsFullwidth() {
        assertEquals(
            "|章|点|\n|---|---|\n| A1 | 6.5 |\n| A2 | 7 |",
            core("｜章｜点｜\n|---|---|\n| A1 | 6.5 |\n| A2 | 7 |")?.body,
        )
    }

    @Test
    fun suppliesMissingDelimiterRowOnceEnoughRowsAgree() {
        assertEquals(
            "|章|点|\n|---|---|\n|A1|6|\n|A2|7|\n|A3|8|",
            core("｜章｜点｜\n｜A1｜6｜\n｜A2｜7｜\n｜A3｜8｜")?.body,
        )
        // 2行はただの偶然ともつかない。区切り行が無いので触らない。
        assertNull(core("｜章｜点｜\n｜A1｜6｜"))
    }

    @Test
    fun leavesFullwidthPipeThatIsCellContentOfWorkingTable() {
        assertNull(core("| status | `pending｜failed` |\n|---|---|\n| a | b |"))
    }

    @Test
    fun leavesProseAndFencedCodeAlone() {
        assertNull(core("- 集中度：A1 高｜A2 低｜A3 高"))
        assertNull(core("```\n｜章｜点｜\n｜---｜---｜\n｜A1｜6｜\n```"))
        assertNull(core("    ｜章｜点｜\n    ｜---｜---｜\n    ｜A1｜6｜"))
    }

    @Test
    fun countsEveryTableSoIndexesLineUp() {
        val r = core("| a | b |\n|---|---|\n| 1 | 2 |\n\n｜c｜d｜\n｜---｜---｜\n｜3｜4｜")
        assertEquals(listOf(1), r?.repaired)
        assertEquals(2, r?.total)
    }

    @Test
    fun returnsNullWithoutAnyFullwidthPipe() {
        assertNull(core("| a | b |\n|---|---|\n| 1 | 2 |"))
    }

    // ---- 課題で見つかった実データの4パターン ----

    /** A. 全部が全角(最多)。 */
    @Test
    fun patternA_allFullwidth() {
        val src = "｜章コード｜点｜一行コメント｜\n" +
            "｜---｜---｜---｜\n" +
            "｜ A1C01 ｜ 6.5 🟧 ｜ 指フェチ視点が新鮮 ｜\n" +
            "｜ A1C02 ｜ 8 🟦 ｜ 三色ノートで着席完了 ｜"
        val body = core(src)?.body
        assertEquals(
            "|章コード|点|一行コメント|\n" +
                "|---|---|---|\n" +
                "| A1C01 | 6.5 🟧 | 指フェチ視点が新鮮 |\n" +
                "| A1C02 | 8 🟦 | 三色ノートで着席完了 |",
            body,
        )
    }

    /** B. ヘッダ行だけ全角(区切り行・データ行は半角)。見落としやすいので必ず含める。 */
    @Test
    fun patternB_onlyHeaderFullwidth() {
        val src = "｜章コード｜点｜一行コメント｜\n" +
            "|---|---|---|\n" +
            "| A1C01 | 6.5 🟧 | まつげの先の震え |"
        assertEquals(
            "|章コード|点|一行コメント|\n" +
                "|---|---|---|\n" +
                "| A1C01 | 6.5 🟧 | まつげの先の震え |",
            core(src)?.body,
        )
    }

    /** C. 区切り行だけ半角(ヘッダとデータは全角)。区切り行を判定から除外しないと漏れる。 */
    @Test
    fun patternC_onlyDelimiterAscii() {
        val src = "｜章コード｜点｜一行コメント｜\n" +
            "|---|---|---|\n" +
            "｜A3C01｜8｜四月三日とゴム裏返し｜\n" +
            "｜A3C01I01｜5.5｜陽菜ソロ｜"
        assertEquals(
            "|章コード|点|一行コメント|\n" +
                "|---|---|---|\n" +
                "|A3C01|8|四月三日とゴム裏返し|\n" +
                "|A3C01I01|5.5|陽菜ソロ|",
            core(src)?.body,
        )
    }

    /** D. 区切り行が存在しない(全部全角)。3行以上そろえば区切り行を補う。 */
    @Test
    fun patternD_noDelimiterRow() {
        val src = "｜章コード｜点｜一行コメント｜\n" +
            "｜A4C01｜7.5｜非対称の贈与に唸った｜\n" +
            "｜A4C01I01｜7｜苦い｜\n" +
            "｜A4C02｜7.5｜頬が緩んだ｜"
        assertEquals(
            "|章コード|点|一行コメント|\n" +
                "|---|---|---|\n" +
                "|A4C01|7.5|非対称の贈与に唸った|\n" +
                "|A4C01I01|7|苦い|\n" +
                "|A4C02|7.5|頬が緩んだ|",
            core(src)?.body,
        )
    }

    // ---- 反例(壊してはいけない) ----

    /** 反例1: セル内容としての意図的な全角｜(半角 | が同居)。ブロックごと触らない。 */
    @Test
    fun counterExample1_intentionalFullwidthInCell() {
        val src = "| 進捗 | `GET /x` | `{status: pending｜complete｜failed}` |\n" +
            "|---|---|---|\n" +
            "| 完了 | `GET /y` | ok |"
        assertNull(core(src))
    }

    /** 反例2: 本文中の区切りとしての全角｜(行頭行末が｜でない)。行判定で落ちる。 */
    @Test
    fun counterExample2_fullwidthAsInlineSeparator() {
        assertNull(core("- 章ごとの集中度：A2C01 高｜A2C02 高｜A2C03 高｜A2C04 低｜A2C05 高"))
    }

    /** 反例3: フェンス内・4スペース以上インデントは対象外。 */
    @Test
    fun counterExample3_fenceAndIndentedCode() {
        assertNull(core("~~~\n｜章｜点｜\n｜---｜---｜\n｜A1｜6｜\n~~~"))
        assertNull(core("    ｜章｜点｜\n    ｜---｜---｜\n    ｜A1｜6｜\n    ｜A2｜7｜"))
    }

    // ---- 注意書きの差し込み ----

    @Test
    fun repairFullwidthTablesInsertsNoticeAboveEachRepairedTable() {
        val notice = "全角｜のため補正して表示"
        val out = MarkdownRenderer.repairFullwidthTables(
            "前文\n\n｜章｜点｜\n｜---｜---｜\n｜A1｜6｜\n",
            notice,
        )
        // 注意書きが引用ブロックとして表の上に入り、表は半角に補正されている。
        assertTrue(out.contains("> $notice"))
        assertTrue(out.contains("|章|点|"))
        // 引用ブロックは表より前に現れる。
        assertTrue(out.indexOf("> $notice") < out.indexOf("|章|点|"))
        // 引用ブロックと表の間には空行があり、表が引用へ吸われない。
        assertTrue(out.contains("> $notice\n\n|章|点|"))
    }

    @Test
    fun repairFullwidthTablesReturnsSourceUnchangedWhenNothingToRepair() {
        val src = "| a | b |\n|---|---|\n| 1 | 2 |"
        assertEquals(src, MarkdownRenderer.repairFullwidthTables(src, "notice"))
    }

    @Test
    fun repairFullwidthTablesWithoutNoticeJustReturnsRepairedBody() {
        val out = MarkdownRenderer.repairFullwidthTables("｜章｜点｜\n｜---｜---｜\n｜A1｜6｜", null)
        assertEquals("|章|点|\n|---|---|\n|A1|6|", out)
        assertFalse(out.contains(">"))
    }
}
