package jp.lazmix.codeleaf.render

import jp.lazmix.codeleaf.git.GraphCommit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** GitGraphLayout のレーン割当(純粋)の JVM 単体テスト。 */
class GitGraphLayoutTest {

    private fun c(sha: String, vararg parents: String) =
        GraphCommit(sha, parents.toList(), "msg $sha", "msg $sha", "author", Instant.EPOCH, emptyList())

    @Test
    fun linearHistory_singleLane() {
        val rows = GitGraphLayout.layout(listOf(c("C", "B"), c("B", "A"), c("A")))
        assertEquals(listOf(0, 0, 0), rows.map { it.nodeLane })
        assertEquals(1, GitGraphLayout.laneCount(rows))
        assertEquals(listOf("B"), rows[0].lanesBelow)
        assertEquals(emptyList<String?>(), rows[2].lanesBelow)
    }

    @Test
    fun twoBranches_sharedRoot_useTwoLanes() {
        // A(main tip)->R, B(feature tip)->R, R
        val rows = GitGraphLayout.layout(listOf(c("A", "R"), c("B", "R"), c("R")))
        assertEquals(0, rows[0].nodeLane)
        assertEquals(1, rows[1].nodeLane) // 2 本目のブランチは別レーン
        assertEquals(0, rows[2].nodeLane) // 共有 root はレーン0へ集約
        assertEquals(2, GitGraphLayout.laneCount(rows))
        assertEquals(listOf("R", "R"), rows[2].lanesAbove) // 2レーンが root に合流
    }

    @Test
    fun mergeCommit_hasTwoParentLanes() {
        // M(parents C,B), C->A, B->A, A
        val rows = GitGraphLayout.layout(
            listOf(c("M", "C", "B"), c("C", "A"), c("B", "A"), c("A")),
        )
        val m = rows[0]
        assertEquals(0, m.nodeLane)
        assertEquals(listOf("C", "B"), m.lanesBelow) // マージの2親が別レーンへ
        assertEquals(1, rows.first { it.commit.sha == "B" }.nodeLane)
        assertEquals(2, GitGraphLayout.laneCount(rows))
        // 最後の A は2レーンが集約
        assertEquals(0, rows.last().nodeLane)
        assertEquals(listOf("A", "A"), rows.last().lanesAbove)
    }

    @Test
    fun countCrossings_linearAndCleanMerge_haveZero() {
        // 直線・素直な分岐合流は交差ゼロ。
        val linear = GitGraphLayout.layout(listOf(c("C", "B"), c("B", "A"), c("A")))
        assertEquals(0, GitGraphLayout.countCrossings(linear))

        val merge = GitGraphLayout.layout(
            listOf(c("M", "C", "B"), c("C", "A"), c("B", "A"), c("A")),
        )
        assertEquals(0, GitGraphLayout.countCrossings(merge))
    }

    /**
     * 回帰: マージの第2親が「既に別レーンで待たれている」場合、合流線が描かれること。
     *
     * B が lane0 を確保して P を待ち、後の行の M(parents=[X,P])は lane1 に乗る。
     * P は lane0 在住なのでレイアウトは新レーンを割らず素通り扱いになる。
     * このとき描画は「素通り縦線」に加えて「ノード(lane1)→lane0 の合流線」を重ねる必要がある。
     * 以前はこの合流線が欠落していた(素通り直線と扇形の2択でしか導出していなかった)。
     */
    @Test
    fun mergeIntoExistingLane_drawsConvergingEdge() {
        // 新しい順: B, M, X, P。B→P で lane0 を確保、M の第2親 P は lane0 在住。
        val rows = GitGraphLayout.layout(
            listOf(c("B", "P"), c("M", "X", "P"), c("X", "P"), c("P")),
        )
        val m = rows.first { it.commit.sha == "M" }
        // 前提: レイアウトは新レーンを割らず素通り(第2親 P は lane0 のまま)。
        assertEquals(1, m.nodeLane)
        assertEquals(listOf("P"), m.lanesAbove)
        assertEquals(listOf("P", "X"), m.lanesBelow)

        val edges = GitGraphLayout.edgesFor(m)
        // 素通り縦線(lane0)は残る。
        assert(GraphEdge(0, 0, EdgeHalf.BOTTOM) in edges) { "素通り縦線が無い: $edges" }
        // ★ 回帰の核心: ノード(lane1)→ lane0 への合流線が存在すること。
        assert(GraphEdge(1, 0, EdgeHalf.BOTTOM) in edges) { "合流線 node->lane0 が欠落: $edges" }
        // 第1親 X はノードから lane1 継続。
        assert(GraphEdge(1, 1, EdgeHalf.BOTTOM) in edges) { "第1親への線が無い: $edges" }
    }

    /**
     * countCrossings が合流線(ノード→素通りレーン)も幾何に忠実に数えること。
     * 合流先レーンが node から見て通過レーンの「向こう側」にある配置では、合流斜め線が
     * 間の通過縦線をまたいで1交差になる。合流線をモデル化していなかった旧実装はこれを0に過少計上していた。
     */
    @Test
    fun countCrossings_countsConvergingMergeLine() {
        // lane0=P(B が待つ・M の第2親), lane1=L(長寿命で通過), node M は lane2 でマージ。
        // 合流線 node(2)->lane0 は間の通過レーン lane1(L)をまたぐ → 1 交差。
        val commits = listOf(
            c("B", "P"), //   lane0 を確保して P を待つ
            c("Cx", "L"), //  lane1 を確保(L は通過し続ける)
            c("M", "Q", "P"), // lane2 でマージ。第2親 P は lane0 在住 → 合流線 2->0 が lane1 をまたぐ
            c("Q"),
            c("L"),
            c("P"),
        )
        val rows = GitGraphLayout.layout(commits)
        val m = rows.first { it.commit.sha == "M" }
        assertEquals(2, m.nodeLane)
        assertEquals(listOf("P", "L"), m.lanesAbove)
        // 合流線 node(2)->lane0 が下半分に存在。
        assert(GraphEdge(2, 0, EdgeHalf.BOTTOM) in GitGraphLayout.edgesFor(m)) {
            "合流線 node->lane0 が欠落: ${GitGraphLayout.edgesFor(m)}"
        }
        // 合流線が通過レーン lane1 をまたぐ 1 交差を数える(旧実装は0に過少計上していた)。
        assertEquals(1, GitGraphLayout.countCrossings(rows))
    }

    /**
     * 中間に空きレーン(0 と 2)があり node が右(lane3)でマージする履歴。
     * 旧・素朴版は追加親を最左の空き(lane0)へ置き、間の通過レーン(lane1)をまたいで交差する。
     * 改良版は node に最も近い空き(lane2)へ寄せ、交差ゼロ・同じレーン幅を保つ。
     */
    @Test
    fun improvedLayout_reducesCrossings_vsGreedy_sameWidth() {
        val commits = listOf(
            c("a2", "a1"), // lane0 を確保
            c("b1", "P"), // lane1 を確保(P は長寿命で通過し続ける)
            c("k2", "k1"), // lane2 を確保
            c("m1", "M"), // lane3 を確保(M を待つ)
            c("a1"), // root: lane0 を解放 → 中間に空き
            c("k1"), // root: lane2 を解放 → 中間に空き
            c("M", "Q", "R"), // lane3 でマージ。追加親 R の配置が分かれる
            c("Q"),
            c("P"),
            c("R"),
        )
        val improved = GitGraphLayout.layout(commits)
        val greedy = GitGraphLayout.layoutGreedy(commits)

        val improvedX = GitGraphLayout.countCrossings(improved)
        val greedyX = GitGraphLayout.countCrossings(greedy)

        // 改良で交差が減る(これが本タスクの主目的)。
        assertEquals(0, improvedX)
        assertEquals(1, greedyX)
        assert(improvedX < greedyX) { "expected fewer crossings: improved=$improvedX greedy=$greedyX" }

        // レーン幅は増えない。
        assertEquals(GitGraphLayout.laneCount(greedy), GitGraphLayout.laneCount(improved))

        // トポロジ整合: マージ M はノード lane3、第1親 Q を同レーン継続、追加親 R は近い空き lane2 へ。
        val m = improved.first { it.commit.sha == "M" }
        assertEquals(3, m.nodeLane)
        assertEquals("Q", m.lanesBelow[3])
        assertEquals("R", m.lanesBelow[2])
    }
}
