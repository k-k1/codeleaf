package com.k1.gitreader.render

import com.k1.gitreader.git.GraphCommit

/**
 * コミットグラフ1行のレーン情報。
 * - nodeLane: このコミットのノードが乗る列。
 * - lanesAbove: この行に上から入ってくる各レーンが指す sha(null=空き)。
 * - lanesBelow: この行から下へ出ていく各レーンが指す sha(null=空き)。
 * 描画側は above/below と nodeLane から接続線を導出する。
 */
data class GraphRow(
    val commit: GraphCommit,
    val nodeLane: Int,
    val lanesAbove: List<String?>,
    val lanesBelow: List<String?>,
)

/**
 * コミット列(新しい順・親 sha を含む)からレーン割当を計算する純粋ロジック。
 * トポロジ的に正しい分岐/合流を表現しつつ、接続線の交差を減らすよう追加親をノード近くへ寄せる。
 */
object GitGraphLayout {

    /**
     * 改良版レイアウト。
     * 第1親はノードと同じレーンを継続(継続性)。マージの追加親は「最左の空き」ではなく
     * **nodeLane に最も近い空きレーン**へ寄せ、流出する斜め線が通過レーンをまたぐのを減らす。
     */
    fun layout(commits: List<GraphCommit>): List<GraphRow> =
        layoutInternal(commits, nearestToNode = true)

    /**
     * 旧・素朴版(追加親を常に最左の空きへ)。交差最小化していない。
     * 改良の before/after を JVM 単体で固定するためのベースラインとして残す。
     */
    internal fun layoutGreedy(commits: List<GraphCommit>): List<GraphRow> =
        layoutInternal(commits, nearestToNode = false)

    private fun layoutInternal(commits: List<GraphCommit>, nearestToNode: Boolean): List<GraphRow> {
        val active = ArrayList<String?>() // lane -> 次に来るべきコミット sha
        val rows = ArrayList<GraphRow>(commits.size)

        for (c in commits) {
            val above = active.toList()

            // このコミットのノード列を決める(待っているレーンが無ければ最左の空き、無ければ末尾追加)
            var nodeLane = active.indexOf(c.sha)
            if (nodeLane < 0) {
                nodeLane = active.indexOfFirst { it == null }
                if (nodeLane < 0) {
                    active.add(null)
                    nodeLane = active.size - 1
                }
            }
            // 同じ sha を待っていた他レーン(複数の子からの合流)はノード列に集約して解放
            for (k in active.indices) {
                if (k != nodeLane && active[k] == c.sha) active[k] = null
            }

            // ノード列は第1親へ継続(親が無ければ解放)
            val parents = c.parents
            active[nodeLane] = parents.getOrNull(0)
            // 追加の親(マージ)は既存レーン再利用 or 空きレーンへ
            for (pi in 1 until parents.size) {
                val p = parents[pi]
                if (active.indexOf(p) >= 0) continue // 既にこの親を待つレーンがある(継続性維持)
                var lane = if (nearestToNode) nearestEmptyLane(active, nodeLane) else active.indexOfFirst { it == null }
                if (lane < 0) {
                    active.add(null)
                    lane = active.size - 1
                }
                active[lane] = p
            }
            // 末尾の空きレーンは詰めて幅を抑える
            while (active.isNotEmpty() && active.last() == null) active.removeAt(active.size - 1)

            rows.add(GraphRow(c, nodeLane, above, active.toList()))
        }
        return rows
    }

    /**
     * nodeLane に最も近い空きレーンの index を返す(無ければ -1)。
     * 距離が同じなら右側(大きい index)を優先して、長寿命ブランチ用の左側レーンを温存する。
     */
    private fun nearestEmptyLane(active: List<String?>, nodeLane: Int): Int {
        var best = -1
        var bestDist = Int.MAX_VALUE
        for (i in active.indices) {
            if (active[i] != null) continue
            val dist = kotlin.math.abs(i - nodeLane)
            // dist が小さい方を採用。同距離なら右側(i>nodeLane)を優先 = 後勝ちで上書き。
            if (dist < bestDist || (dist == bestDist && i > nodeLane)) {
                best = i
                bestDist = dist
            }
        }
        return best
    }

    /** 全行を通じて必要なレーン数(描画幅の決定用)。 */
    fun laneCount(rows: List<GraphRow>): Int =
        rows.maxOfOrNull { maxOf(it.nodeLane + 1, it.lanesAbove.size, it.lanesBelow.size) } ?: 1

    /**
     * 接続線の交差数を数える純粋関数(描画 `GraphCell` の幾何に忠実)。
     *
     * 各行のセルは上半分(入力レーン→ノード)と下半分(ノード→出力レーン)に分かれる:
     * - 上半分: `lanesAbove[j]==sha` の流入斜め線(j→nodeLane)。間にある通過縦レーンと交差し得る。
     * - 下半分: ノードから `lanesBelow[k]`(k!=nodeLane かつ非通過)へ出る分岐斜め線(nodeLane→k)。
     * 上半分と下半分は別の領域なので互いに交差しない。通過縦レーンが斜め線の両端の「間」にあれば1交差。
     */
    fun countCrossings(rows: List<GraphRow>): Int {
        var total = 0
        for (row in rows) {
            val n = row.nodeLane
            val a = row.lanesAbove
            val b = row.lanesBelow
            val s = row.commit.sha

            // 上半分: 流入斜め線(j -> n) × 上半分の通過縦レーン(A[m]!=null && A[m]!=s)
            val topVerticals = a.indices.filter { a[it] != null && a[it] != s }
            for (j in a.indices) {
                if (a[j] == s && j != n) {
                    val lo = minOf(j, n)
                    val hi = maxOf(j, n)
                    total += topVerticals.count { it in (lo + 1) until hi }
                }
            }

            // 下半分: 流出斜め線(n -> k) × 下半分の通過縦レーン(passThrough: B[m]==A[m]!=null, m!=n)
            fun passThrough(m: Int) = m != n && b[m] != null && a.getOrNull(m) == b[m]
            val botVerticals = b.indices.filter { passThrough(it) }
            for (k in b.indices) {
                if (b[k] != null && k != n && !passThrough(k)) {
                    val lo = minOf(n, k)
                    val hi = maxOf(n, k)
                    total += botVerticals.count { it in (lo + 1) until hi }
                }
            }
        }
        return total
    }
}
