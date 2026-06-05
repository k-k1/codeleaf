package com.k1.gitreader.ui

import com.k1.gitreader.data.IconSet

/**
 * 拡張子(または特定ファイル名)からブランドアイコンを引く。アイコンは複数セットから選べ
 * (Devicon / Material / VS Code / Seti)、共通の「種別キー(typeKey, 例: "kotlin")」を
 * 各セットの assets/<dir>/<種別キー>.svg に対応付ける。
 *
 * セットが未収録の種別キー(例: Seti には groovy/nodejs が無い)は null を返し、
 * 呼び出し側で汎用ファイルアイコンにフォールバックする。
 */

/** アイコンへ適用するティント方針。 */
enum class IconTint {
    /** ティントしない(セット同梱の色をそのまま描画)。 */
    NONE,

    /** テーマの onSurface 系色でティント(塗り色を持たない黒ロゴ用)。 */
    ON_SURFACE,

    /** [FileIconSpec.color] の固定色でティント(Seti のタイプ別カラー用)。 */
    FIXED,
}

/** 解決済みアイコン。[uri] は Coil に渡す asset URI。[color] は tint=FIXED のとき使う ARGB。 */
data class FileIconSpec(val uri: String, val tint: IconTint, val color: Long = 0L)

/**
 * 一覧で「特殊」として強調/減光するファイル分類。優先度の高い順に判定する
 * (例: `.env` は DOTFILE でもあるが SECRET が勝つ)。描画スタイルは呼び出し側で解決。
 */
enum class FileMark {
    /** AI アシスタント関連(指示書/設定)。tertiary 色＋先頭バー＋「AI」チップ。 */
    AI,

    /** 機密/要注意(鍵・認証情報・.env 実体)。error 色＋先頭バー＋「!」チップ。 */
    SECRET,

    /** 生成物・ロックファイル。減光して背景化する。 */
    GENERATED,

    /** README/LICENSE 等の重要ドキュメント。太字で軽く強調(色は足さない)。 */
    DOC,

    /** その他のドット始まりファイル/ディレクトリ。少しグレーにして控えめに。 */
    DOTFILE,

    /** 特殊扱いなし(通常表示)。 */
    NONE,
}

object FileIcons {

    /** 拡張子 → 種別キー(セット非依存)。 */
    private val byExt: Map<String, String> = buildMap {
        fun reg(key: String, vararg exts: String) = exts.forEach { this[it] = key }

        reg("kotlin", "kt", "kts")
        reg("java", "java", "jar", "class")
        reg("groovy", "groovy")
        reg("gradle", "gradle")
        reg("scala", "scala", "sc")
        reg("c", "c", "h")
        reg("cplusplus", "cpp", "cc", "cxx", "hpp", "hh")
        reg("csharp", "cs")
        reg("javascript", "js", "mjs", "cjs")
        reg("typescript", "ts", "mts", "cts")
        reg("react", "jsx", "tsx")
        reg("css3", "css")
        reg("sass", "scss", "sass")
        reg("less", "less")
        reg("html5", "html", "htm", "xhtml")
        reg("xml", "xml", "svg", "plist")
        reg("python", "py", "pyw", "pyi")
        reg("go", "go")
        reg("swift", "swift")
        reg("dart", "dart")
        reg("markdown", "md", "markdown", "mdx")
        reg("ruby", "rb", "gemspec")
        reg("php", "php")
        reg("rust", "rs")
        reg("bash", "sh", "bash", "zsh")
        reg("vuejs", "vue")
        reg("nodejs", "node")
        reg("json", "json")
        reg("yaml", "yml", "yaml")
        reg("docker", "dockerfile")
    }

    /** 拡張子に依存しない特定ファイル名(完全一致, 小文字) → 種別キー。 */
    private val byName: Map<String, String> = mapOf(
        "dockerfile" to "docker",
        ".gitignore" to "git",
        ".gitattributes" to "git",
        ".gitmodules" to "git",
        "build.gradle" to "gradle",
        "build.gradle.kts" to "gradle",
        "settings.gradle.kts" to "gradle",
    )

    /** 全種別キー(どのセットも基本これを収録。Seti のみ一部欠落)。 */
    private val allKeys: Set<String> = (byExt.values + byName.values).toSet()

    /**
     * AI コーディングアシスタント関連の特殊ファイル/ディレクトリ名(完全一致, 小文字)。
     * これらは種別アイコンより優先して AI マーカーで描画し、一覧で目立たせる。
     */
    private val aiNames: Set<String> = setOf(
        // 指示書 / ルールファイル
        "claude.md", "claude.local.md",
        "agents.md",
        "gemini.md",
        "copilot-instructions.md",
        ".cursorrules", ".windsurfrules",
        ".aider.conf.yml", ".aiderignore",
        "llms.txt", "llms-full.txt",
        ".mcp.json",
        // 設定ディレクトリ
        ".claude", ".cursor", ".windsurf", ".continue", ".aider", ".codeium",
    )

    /** AI 系の特殊ファイル/ディレクトリか(セット非依存)。 */
    fun isAiFile(name: String): Boolean = name.lowercase() in aiNames

    /** README/LICENSE 等の重要ドキュメントの基底名(拡張子を除いた小文字)。 */
    private val docBaseNames: Set<String> = setOf(
        "readme", "license", "licence", "contributing", "changelog",
        "security", "code_of_conduct", "authors", "notice", "copying",
    )

    /** 機密ファイルの拡張子(鍵・証明書・キーストア)。 */
    private val secretExtensions: Set<String> = setOf(
        "pem", "key", "keystore", "jks", "p12", "pfx", "ppk",
    )

    /** 生成物・ロックの固定ファイル名(拡張子では拾えないもの)。 */
    private val generatedExactNames: Set<String> = setOf(
        "go.sum", "package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.yaml",
        "composer.lock", "gemfile.lock", "poetry.lock", "pipfile.lock",
        "podfile.lock", "flake.lock",
    )

    private fun isSecret(n: String): Boolean {
        if (n.endsWith(".pub")) return false // 公開鍵は機密でない
        // .env / .env.local 等は機密。ただし雛形(.env.example 等)は除外。
        if (n == ".env" || n.startsWith(".env.")) {
            return n.removePrefix(".env.") !in setOf("example", "sample", "template", "dist", "defaults")
        }
        if (n == "credentials" || n.startsWith("credentials.")) return true
        if (n.startsWith("id_rsa") || n.startsWith("id_dsa") ||
            n.startsWith("id_ecdsa") || n.startsWith("id_ed25519")
        ) return true
        return n.substringAfterLast('.', "") in secretExtensions
    }

    private fun isGenerated(n: String): Boolean = when {
        n in generatedExactNames -> true
        n.endsWith(".lock") -> true          // yarn.lock / Cargo.lock など
        n.endsWith("-lock.json") -> true     // package-lock.json 系
        n.endsWith(".min.js") || n.endsWith(".min.css") -> true
        n.endsWith(".js.map") || n.endsWith(".css.map") -> true
        else -> false
    }

    private fun isDoc(n: String): Boolean {
        val base = if ('.' in n) n.substringBeforeLast('.') else n
        return base in docBaseNames
    }

    /** ファイル名から特殊分類を判定する(優先度順)。セット非依存・純粋関数。 */
    fun mark(name: String): FileMark {
        val n = name.lowercase()
        return when {
            isAiFile(n) -> FileMark.AI
            isSecret(n) -> FileMark.SECRET
            isGenerated(n) -> FileMark.GENERATED
            isDoc(n) -> FileMark.DOC
            n.startsWith(".") -> FileMark.DOTFILE
            else -> FileMark.NONE
        }
    }

    /** ファイル名から種別キーを引く(セット非依存)。未対応は null。 */
    fun typeKey(name: String): String? {
        byName[name.lowercase()]?.let { return it }
        val ext = name.substringAfterLast('.', "").lowercase()
        return byExt[ext]
    }

    /** 指定セットでファイル用アイコンを解決。未対応 or セット未収録は null。 */
    fun forFile(set: IconSet, name: String): FileIconSpec? =
        typeKey(name)?.let { spec(set, it) }

    /** 種別キーを直接指定して解決(submodule の "git" 等)。セット未収録は null。 */
    fun forKey(set: IconSet, key: String): FileIconSpec? = spec(set, key)

    private fun spec(set: IconSet, key: String): FileIconSpec? {
        if (key !in coverage(set)) return null
        val uri = "file:///android_asset/${set.dir}/$key.svg"
        return when (set) {
            // Devicon: 塗り色を持たない黒ロゴだけテーマ色にティント。
            IconSet.DEVICON ->
                FileIconSpec(uri, if (key in DEVICON_MONO) IconTint.ON_SURFACE else IconTint.NONE)
            // Material / VS Code: すべてフルカラー同梱なのでティント不要。
            IconSet.MATERIAL, IconSet.VSCODE ->
                FileIconSpec(uri, IconTint.NONE)
            // Seti: 単色グリフをタイプ別の Seti カラーで一律ティント(黒デフォルトのアイコンも可視化)。
            IconSet.SETI ->
                FileIconSpec(uri, IconTint.FIXED, SETI_COLOR[key] ?: SETI_DEFAULT)
        }
    }

    /** セットが収録する種別キー集合。Seti は groovy/nodejs を欠くため除外。 */
    private fun coverage(set: IconSet): Set<String> = when (set) {
        IconSet.SETI -> allKeys - "groovy" - "nodejs"
        else -> allKeys
    }

    /** Devicon で塗り色を持たず黒で描画される(=ティントが要る)種別キー。 */
    private val DEVICON_MONO = setOf("markdown", "rust", "json", "yaml")

    /** Seti のデフォルト色(@white)。マップに無いキーはこれを使う。 */
    private const val SETI_DEFAULT = 0xFFD4D7D6

    /** Seti のタイプ別カラー(seti-ui mapping.less のパレットに準拠)。 */
    private val SETI_COLOR: Map<String, Long> = mapOf(
        "kotlin" to 0xFFE37933,     // orange
        "java" to 0xFFCC3E44,       // red
        "gradle" to 0xFF519ABA,     // blue
        "scala" to 0xFFCC3E44,      // red
        "c" to 0xFF519ABA,          // blue
        "cplusplus" to 0xFF519ABA,  // blue
        "csharp" to 0xFF519ABA,     // blue
        "javascript" to 0xFFCBCB41, // yellow
        "typescript" to 0xFF519ABA, // blue
        "react" to 0xFF519ABA,      // blue
        "css3" to 0xFF519ABA,       // blue
        "sass" to 0xFFF55385,       // pink
        "less" to 0xFF519ABA,       // blue
        "html5" to 0xFFE37933,      // orange
        "xml" to 0xFFE37933,        // orange
        "python" to 0xFF519ABA,     // blue
        "go" to 0xFF519ABA,         // blue
        "swift" to 0xFFE37933,      // orange
        "dart" to 0xFF519ABA,       // blue
        "markdown" to 0xFF519ABA,   // blue
        "ruby" to 0xFFCC3E44,       // red
        "php" to 0xFFA074C4,        // purple
        "rust" to 0xFFD4D7D6,       // grey-light
        "bash" to 0xFF8DC149,       // green
        "vuejs" to 0xFF8DC149,      // green
        "json" to 0xFFCBCB41,       // yellow
        "docker" to 0xFF519ABA,     // blue
        "git" to 0xFF687D8A,        // muted slate (@ignore 相当)
        // yaml は Seti 未マッピング → SETI_DEFAULT(white)
    )
}
