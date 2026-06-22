package jp.lazmix.codeleaf.ui

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

/**
 * clone URL の簡易バリデーション。問題なければ null、あれば表示用メッセージを返す。
 * 空文字は「未入力」として呼び出し側の必須チェック(ボタン無効)に委ねるため null。
 * 厳密な到達性は確認せず、明らかに形式が違うものだけ弾く(誤検知で正規 URL を拒まない)。
 */
internal fun repoUrlError(url: String): String? {
    val u = url.trim()
    if (u.isEmpty()) return null
    if (u.any { it.isWhitespace() }) return "URL に空白が含まれています"
    val lower = u.lowercase()
    // ローカル clone ソース(端末上の git リポ)を許可: file:// URL とベタの絶対パス。
    // JGit はどちらも clone 可能。到達性は確認せず、明らかな形式違いだけ弾く方針に合わせる。
    if (lower.startsWith("file://")) {
        return if (u.substringAfter("://").startsWith("/")) null
        else "file:// の後ろは絶対パスにしてください（例: file:///path/to/repo）"
    }
    if (u.startsWith("/")) return null
    val hasScheme = lower.startsWith("https://") || lower.startsWith("http://") ||
        lower.startsWith("ssh://") || lower.startsWith("git://")
    if (hasScheme) {
        val rest = u.substringAfter("://")
        // host と owner/repo のパスが要る。
        if (!rest.contains('/') || rest.substringAfter('/').isBlank()) {
            return "URL に owner/repo が含まれていません（例: https://github.com/owner/repo.git）"
        }
        return null
    }
    // scp 形式 git@host:owner/repo(.git) も許可。
    if (Regex("""^[^@\s/]+@[^@\s/:]+:.+""").matches(u)) return null
    return "URL の形式が正しくありません（例: https://github.com/owner/repo.git）"
}

/**
 * git 操作失敗の例外を、原因チェーンの文言から利用者向けメッセージに分類する純粋関数。
 * 分類できなければ null を返し、呼び出し側で素の message にフォールバックする。clone/sync 共有。
 */
internal fun gitErrorMessage(t: Throwable): String? {
    val text = generateSequence(t) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" / ")
        .lowercase()
    fun has(vararg keys: String) = keys.any { it in text }
    return when {
        has("not authorized", "authentication", "auth fail", "401", "403", "not permitted", "permission denied") ->
            "認証に失敗しました。トークンと権限(GitHub PAT は Contents: Read-only 必須)を確認してください。"
        has("unknownhost", "unable to resolve host", "name or service not known", "no address associated") ->
            "ホストに接続できません。URL とネットワーク接続を確認してください。"
        has("repository not found", "not found", "404", "noremoterepository", "service not found") ->
            "リポジトリが見つかりません。URL を確認してください(private なら認証も必要)。"
        has("timed out", "timeout") ->
            "接続がタイムアウトしました。ネットワークを確認して再試行してください。"
        // JGit の TransportHttp が HTTPS 通信中の IOException を包む文言("cannot open git-upload-pack")と、
        // SSL/接続断など低レベルな通信失敗をまとめて通信エラーとして扱う。
        has("cannot open git-upload-pack", "connection reset", "connection refused",
            "unexpected end of stream", "broken pipe", "sslhandshake", "ssl handshake",
            "software caused connection abort", "unable to connect", "failed to connect") ->
            "通信エラーが発生しました。ネットワーク接続を確認して再試行してください。"
        has("not a git repository", "invalid remote", "not designed to transport") ->
            "git リポジトリとして開けませんでした。URL を確認してください。"
        else -> null
    }
}

/**
 * clone 失敗の例外を利用者向けメッセージに変換する。分類できなければ素の message を添える。
 */
internal fun cloneErrorMessage(t: Throwable): String =
    gitErrorMessage(t) ?: "clone に失敗しました: ${t.message ?: t.javaClass.simpleName}"
