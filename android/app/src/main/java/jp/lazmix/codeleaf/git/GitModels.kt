package jp.lazmix.codeleaf.git

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.time.Instant

/** リモートブランチ1件分の情報（直近順表示・選択用）。 */
data class BranchInfo(
    val name: String,
    val sha: String,
    val committedAt: Instant,
)

/**
 * ファイル/フォルダを最後に変更したコミット情報（ブラウザ一覧の「いつ・誰」表示用）。
 * author = 著者名、at = 著者日時。
 */
data class EntryCommit(
    val author: String,
    val at: Instant,
)

/**
 * 変更パス [changedPath] が対象 [target] に属するか判定する純粋関数。
 * target がファイルなら完全一致、フォルダ（プレフィックス）なら配下（`target/...`）を真とする。
 * ディレクトリ最終コミットの解決で、差分パスを一覧エントリへ突き合わせるのに使う。
 */
internal fun matchesPathPrefix(changedPath: String, target: String): Boolean =
    changedPath == target || changedPath.startsWith("$target/")

/** submodule の範囲表示で使うコミット1件（中身＝submodule リポのコミット）。 */
data class SubmoduleCommit(
    val sha: String,
    val shortMessage: String,
    val author: String,
    val at: Instant,
)

/** submodule(gitlink)の変更方向。 */
enum class SubmoduleChangeDirection {
    /** old が new の祖先（通常の前進更新）。 */
    FORWARD,
    /** new が old の祖先（巻き戻し）。 */
    BACKWARD,
    /** 互いに祖先でない（分岐）。 */
    DIVERGED,
    /** submodule 追加（old 無し）。 */
    ADD,
    /** submodule 削除（new 無し）。 */
    REMOVE,
    /** submodule 実体が未取得などで解決できない（ハッシュのみ表示にフォールバック）。 */
    UNRESOLVED,
}

/**
 * 親コミットでの submodule(gitlink)変更。old→new と、その間の submodule コミット列を持つ。
 * [commits] は new 側で増えた（ADD/REMOVE は tip 側の）コミットを新しい順。境界の old は [boundary]。
 */
data class SubmoduleChange(
    val path: String,
    val oldSha: String?,
    val newSha: String?,
    val direction: SubmoduleChangeDirection,
    val commits: List<SubmoduleCommit>,
    /** 基点（old）のコミット。表示の「基点」行用。ADD/解決不能時は null。 */
    val boundary: SubmoduleCommit?,
    /** [commits] が limit で打ち切られたか。 */
    val truncated: Boolean,
)

/** コミット1件分の情報（履歴表示用）。 */
@Parcelize
@TypeParceler<Instant, InstantParceler>
data class CommitInfo(
    val sha: String,
    val shortMessage: String,
    val author: String,
    val committedAt: Instant,
) : Parcelable

/** Instant を epochMilli Long で Parcel に書き出す(プロセス死復元のため)。 */
object InstantParceler : Parceler<Instant> {
    override fun create(parcel: Parcel): Instant = Instant.ofEpochMilli(parcel.readLong())
    override fun Instant.write(parcel: Parcel, flags: Int) = parcel.writeLong(toEpochMilli())
}

/** コミットグラフ1ノード。parents は親コミットの sha（マージは複数）、refs は指しているブランチ/タグ名。 */
@Parcelize
@TypeParceler<Instant, InstantParceler>
data class GraphCommit(
    val sha: String,
    val parents: List<String>,
    val shortMessage: String,
    val fullMessage: String,
    val author: String,
    val committedAt: Instant,
    val refs: List<String>,
    /**
     * このコミットを指すリモートブランチ表示名(origin/ を剥がした名前)。タグは含まない。
     * グラフ長押しでの「ブランチ切替」メニュー用([refs] はチップ表示用でタグも混在する)。
     */
    val branches: List<String> = emptyList(),
    /**
     * 現在チェックアウト中ブランチ(HEAD)から到達可能か。true=ローカル作業ツリーに反映済み。
     * false=他ブランチ専用/未取り込みで、UI ではグレー表示する。
     */
    val inCurrentBranch: Boolean = true,
) : Parcelable

/**
 * コミットグラフ chip 用の ref 表示名。読み取りミラーなので冗長さを避ける純粋関数:
 * - ローカルブランチ(refs/heads 配下) は出さない(null) … リモートと重複するため
 * - リモートブランチ(refs/remotes 配下) は remote 名を剥がす(origin/main → main)。origin/HEAD は除外
 * - タグ(refs/tags 配下) はそのまま
 * - それ以外(refs/stash 等) は出さない
 */
internal fun graphRefDisplayName(fullName: String): String? = when {
    fullName.startsWith("refs/heads/") -> null
    fullName.startsWith("refs/remotes/") ->
        fullName.removePrefix("refs/remotes/").substringAfter('/').takeIf { it != "HEAD" }
    fullName.startsWith("refs/tags/") -> fullName.removePrefix("refs/tags/")
    else -> null
}

/**
 * リモートブランチ(refs/remotes 配下)のときだけブランチ表示名(origin/ を剥がした名前)を返す。
 * タグ・ローカルブランチ・origin/HEAD は null。グラフ長押しの「ブランチ切替」候補抽出に使う純粋関数。
 */
internal fun remoteBranchDisplayName(fullName: String): String? =
    if (fullName.startsWith("refs/remotes/")) {
        fullName.removePrefix("refs/remotes/").substringAfter('/').takeIf { it != "HEAD" }
    } else {
        null
    }

/**
 * SSH 形式の git URL を HTTPS に変換する（変換不要ならそのまま返す）。submodule 用。
 * 認証はトークン(HTTPS)で行うため、SSH のままだと JGit が取得できず submodule が空になる。
 * 対応: scp 形式 `user@host:owner/repo(.git)` と `ssh://[user@]host[:port]/owner/repo(.git)`。
 */
internal fun sshToHttps(url: String): String {
    val u = url.trim()
    // scp 形式: user@host:path（"http(s)://..." は '/' を含むため [^@/] に阻まれ誤マッチしない）。
    Regex("""^[^@/\s]+@([^:/\s]+):(.+)$""").matchEntire(u)?.let {
        return "https://${it.groupValues[1]}/${it.groupValues[2].removePrefix("/")}"
    }
    // ssh://[user@]host[:port]/path
    Regex("""^ssh://(?:[^@/\s]+@)?([^:/\s]+)(?::\d+)?/(.+)$""").matchEntire(u)?.let {
        return "https://${it.groupValues[1]}/${it.groupValues[2]}"
    }
    return u
}
