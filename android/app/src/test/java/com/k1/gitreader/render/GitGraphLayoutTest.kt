package com.k1.gitreader.render

import com.k1.gitreader.git.GraphCommit
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
}
