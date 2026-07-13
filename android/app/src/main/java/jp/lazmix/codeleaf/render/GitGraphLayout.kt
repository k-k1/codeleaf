package jp.lazmix.codeleaf.render

import jp.lazmix.codeleaf.git.GraphCommit

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

/** セル内の縦位置。上半分は上端→中央、下半分は中央→下端。 */
enum class EdgeHalf { TOP, BOTTOM }

/**
 * セル内の接続線1本(純粋な幾何のみ・色や座標は持たない)。
 * - TOP:    (fromLane, セル上端) → (toLane, 中央)
 * - BOTTOM: (fromLane, 中央)    → (toLane, セル下端)
 * fromLane==toLane は縦の通過線。描画色は GraphCell 側で TOP=fromLane / BOTTOM=toLane の
 * レーン色に対応させる(この規約は現行 GraphCell の描画と一致)。
 */
data class GraphEdge(val fromLane: Int, val toLane: Int, val half: EdgeHalf)

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
     * 1行のセルに描く接続線を導出する純粋関数(描画 `GraphCell` と描画側テストの単一の真実)。
     * セルは上半分(入力レーン→ノード)と下半分(ノード→出力レーン)に分かれる:
     * - 上半分: `lanesAbove[j]==sha` は流入斜め線(j→nodeLane)、それ以外は縦の通過線。
     * - 下半分: 通過レーン(`lanesAbove[j]==lanesBelow[j]`, j!=nodeLane)は縦の通過線。
     *   ただし**そのレーンの sha がこのコミットの親でもあれば**、ノード→そのレーンへの合流線を重ねる
     *   (親が既存レーン在住だとレイアウトは新レーンを割らないため。通過線は残しつつ合流を描く)。
     *   通過でないレーンはノードからの分岐/合流斜め線。
     */
    fun edgesFor(row: GraphRow): List<GraphEdge> {
        val n = row.nodeLane
        val sha = row.commit.sha
        val parents = row.commit.parents
        val edges = ArrayList<GraphEdge>()
        row.lanesAbove.forEachIndexed { j, s ->
            if (s == null) return@forEachIndexed
            edges.add(if (s == sha) GraphEdge(j, n, EdgeHalf.TOP) else GraphEdge(j, j, EdgeHalf.TOP))
        }
        row.lanesBelow.forEachIndexed { j, s ->
            if (s == null) return@forEachIndexed
            val passThrough = row.lanesAbove.getOrNull(j) == s && j != n
            if (passThrough) {
                edges.add(GraphEdge(j, j, EdgeHalf.BOTTOM))
                if (s in parents) edges.add(GraphEdge(n, j, EdgeHalf.BOTTOM))
            } else {
                edges.add(GraphEdge(n, j, EdgeHalf.BOTTOM))
            }
        }
        return edges
    }

    /**
     * 接続線の交差数を数える純粋関数。`edgesFor` の幾何(描画そのもの)に忠実。
     * 各半分で、斜め線(fromLane!=toLane)の両端の「間」に縦の通過線があれば1交差。
     * 上半分と下半分は別領域なので互いに交差しない。斜め線どうしはノード/親端点を共有し扇形に開くだけで交差しないため対象外。
     * ノードから通過レーンへの合流線(本来の通過縦線と共存)も斜め線として同様にカウントされる。
     */
    fun countCrossings(rows: List<GraphRow>): Int {
        var total = 0
        for (row in rows) {
            val edges = edgesFor(row)
            for (half in EdgeHalf.entries) {
                val verticals = edges.filter { it.half == half && it.fromLane == it.toLane }.map { it.fromLane }
                for (e in edges) {
                    if (e.half != half || e.fromLane == e.toLane) continue
                    val lo = minOf(e.fromLane, e.toLane)
                    val hi = maxOf(e.fromLane, e.toLane)
                    total += verticals.count { it in (lo + 1) until hi }
                }
            }
        }
        return total
    }
}
