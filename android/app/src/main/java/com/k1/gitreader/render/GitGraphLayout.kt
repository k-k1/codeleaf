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
 * 交差最小化などの最適化はしない素朴版だが、トポロジ的に正しい分岐/合流を表現する。
 */
object GitGraphLayout {

    fun layout(commits: List<GraphCommit>): List<GraphRow> {
        val active = ArrayList<String?>() // lane -> 次に来るべきコミット sha
        val rows = ArrayList<GraphRow>(commits.size)

        for (c in commits) {
            val above = active.toList()

            // このコミットのノード列を決める
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
                var lane = active.indexOf(p)
                if (lane < 0) {
                    lane = active.indexOfFirst { it == null }
                    if (lane < 0) {
                        active.add(null)
                        lane = active.size - 1
                    }
                    active[lane] = p
                }
            }
            // 末尾の空きレーンは詰めて幅を抑える
            while (active.isNotEmpty() && active.last() == null) active.removeAt(active.size - 1)

            rows.add(GraphRow(c, nodeLane, above, active.toList()))
        }
        return rows
    }

    /** 全行を通じて必要なレーン数(描画幅の決定用)。 */
    fun laneCount(rows: List<GraphRow>): Int =
        rows.maxOfOrNull { maxOf(it.nodeLane + 1, it.lanesAbove.size, it.lanesBelow.size) } ?: 1
}
