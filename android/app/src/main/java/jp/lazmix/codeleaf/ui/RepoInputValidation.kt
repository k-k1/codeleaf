package jp.lazmix.codeleaf.ui

import jp.lazmix.codeleaf.R

/**
 * git URL からリポジトリ名を推定する。末尾スラッシュ・.git・クエリ/フラグメントを除去し、
 * 最後のパスセグメントを返す。GitHub/Bitbucket の https URL を想定。
 */
internal fun repoNameFromUrl(url: String): String {
    val cleaned = url.trim()
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
    if (cleaned.isEmpty()) return ""
    return cleaned.substringAfterLast('/').removeSuffix(".git")
}

/** URL バリデーションのエラー種別(表示文言は UI 層で stringResource に解決)。 */
enum class RepoUrlError { WHITESPACE, FILE_NOT_ABSOLUTE, MISSING_OWNER_REPO, BAD_FORMAT }

/**
 * clone URL の簡易バリデーション。問題なければ null、あれば [RepoUrlError] を返す(純粋・テスト可能)。
 * 空文字は「未入力」として呼び出し側の必須チェック(ボタン無効)に委ねるため null。
 * 厳密な到達性は確認せず、明らかに形式が違うものだけ弾く(誤検知で正規 URL を拒まない)。
 */
internal fun repoUrlError(url: String): RepoUrlError? {
    val u = url.trim()
    if (u.isEmpty()) return null
    if (u.any { it.isWhitespace() }) return RepoUrlError.WHITESPACE
    val lower = u.lowercase()
    // ローカル clone ソース(端末上の git リポ)を許可: file:// URL とベタの絶対パス。
    // JGit はどちらも clone 可能。到達性は確認せず、明らかな形式違いだけ弾く方針に合わせる。
    if (lower.startsWith("file://")) {
        return if (u.substringAfter("://").startsWith("/")) null else RepoUrlError.FILE_NOT_ABSOLUTE
    }
    if (u.startsWith("/")) return null
    val hasScheme = lower.startsWith("https://") || lower.startsWith("http://") ||
        lower.startsWith("ssh://") || lower.startsWith("git://")
    if (hasScheme) {
        val rest = u.substringAfter("://")
        // host と owner/repo のパスが要る。
        if (!rest.contains('/') || rest.substringAfter('/').isBlank()) {
            return RepoUrlError.MISSING_OWNER_REPO
        }
        return null
    }
    // scp 形式 git@host:owner/repo(.git) も許可。
    if (Regex("""^[^@\s/]+@[^@\s/:]+:.+""").matches(u)) return null
    return RepoUrlError.BAD_FORMAT
}

/** git 操作失敗の分類(表示文は UI 層で resId に解決)。clone/sync/切替で共有。 */
enum class GitErrorKind(val resId: Int) {
    AUTH(R.string.git_err_auth),
    HOST(R.string.git_err_host),
    NOT_FOUND(R.string.git_err_not_found),
    TIMEOUT(R.string.git_err_timeout),
    NETWORK(R.string.git_err_network),
    NOT_GIT_REPO(R.string.git_err_not_repo),
}

/**
 * git 操作失敗の例外を、原因チェーンの文言から [GitErrorKind] に分類する純粋関数(テスト可能)。
 * 分類できなければ null(呼び出し側で素の message にフォールバック)。
 */
internal fun gitErrorKind(t: Throwable): GitErrorKind? {
    val text = generateSequence(t) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" / ")
        .lowercase()
    fun has(vararg keys: String) = keys.any { it in text }
    return when {
        has("not authorized", "authentication", "auth fail", "401", "403", "not permitted", "permission denied") ->
            GitErrorKind.AUTH
        has("unknownhost", "unable to resolve host", "name or service not known", "no address associated") ->
            GitErrorKind.HOST
        has("repository not found", "not found", "404", "noremoterepository", "service not found") ->
            GitErrorKind.NOT_FOUND
        has("timed out", "timeout") ->
            GitErrorKind.TIMEOUT
        // JGit の TransportHttp が HTTPS 通信中の IOException を包む文言("cannot open git-upload-pack")と、
        // SSL/接続断など低レベルな通信失敗をまとめて通信エラーとして扱う。
        has("cannot open git-upload-pack", "connection reset", "connection refused",
            "unexpected end of stream", "broken pipe", "sslhandshake", "ssl handshake",
            "software caused connection abort", "unable to connect", "failed to connect") ->
            GitErrorKind.NETWORK
        has("not a git repository", "invalid remote", "not designed to transport") ->
            GitErrorKind.NOT_GIT_REPO
        else -> null
    }
}

/** clone 失敗を表示用 UiText に変換する。分類できれば分類文、できなければ "clone に失敗しました: <message>"。 */
internal fun cloneErrorUiText(t: Throwable): UiText {
    val kind = gitErrorKind(t)
    return if (kind != null) UiText.Res(kind.resId)
    else UiText.GitError(R.string.clone_failed, null, t.message ?: t.javaClass.simpleName)
}

/** 同期結果の表示用 UiText。失敗は "同期失敗: <分類 or message>"、成功は「同期完了」。各画面・VM 共通。 */
internal fun syncResultUiText(error: Throwable?): UiText =
    if (error == null) UiText.Res(R.string.sync_done)
    else UiText.GitError(R.string.sync_failed, gitErrorKind(error), error.message)
