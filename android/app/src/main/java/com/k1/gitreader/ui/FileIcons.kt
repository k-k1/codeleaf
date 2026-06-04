package com.k1.gitreader.ui

/**
 * 拡張子(または特定ファイル名)から Devicon のブランドロゴ(assets/devicon 配下の svg)を引く。
 * 対応しない拡張子は null を返し、呼び出し側で汎用ファイルアイコンにフォールバックする。
 *
 * monochrome=true のロゴ(塗り色を持たず黒で描画される markdown/rust など)は、
 * ダーク/ライト両テーマで視認できるよう描画側で onSurface 色にティントする。
 */
data class DevIcon(val asset: String, val monochrome: Boolean = false)

object FileIcons {
    private const val DIR = "file:///android_asset/devicon"

    /** Devicon の asset へのフル URI(Coil のモデルに渡す)。 */
    fun assetUri(icon: DevIcon): String = "$DIR/${icon.asset}.svg"

    /** 拡張子マップ。値は assets/devicon/<asset>.svg に対応。 */
    private val byExt: Map<String, DevIcon> = buildMap {
        fun put(icon: DevIcon, vararg exts: String) = exts.forEach { put(it, icon) }

        put(DevIcon("kotlin"), "kt", "kts")
        put(DevIcon("java"), "java", "jar", "class")
        put(DevIcon("groovy"), "groovy")
        put(DevIcon("gradle"), "gradle")
        put(DevIcon("scala"), "scala", "sc")
        put(DevIcon("c"), "c", "h")
        put(DevIcon("cplusplus"), "cpp", "cc", "cxx", "hpp", "hh")
        put(DevIcon("csharp"), "cs")
        put(DevIcon("javascript"), "js", "mjs", "cjs")
        put(DevIcon("typescript"), "ts", "mts", "cts")
        put(DevIcon("react"), "jsx", "tsx")
        put(DevIcon("css3"), "css")
        put(DevIcon("sass"), "scss", "sass")
        put(DevIcon("less"), "less")
        put(DevIcon("html5"), "html", "htm", "xhtml")
        put(DevIcon("xml"), "xml", "svg", "plist")
        put(DevIcon("python"), "py", "pyw", "pyi")
        put(DevIcon("go"), "go")
        put(DevIcon("swift"), "swift")
        put(DevIcon("dart"), "dart")
        put(DevIcon("markdown", monochrome = true), "md", "markdown", "mdx")
        put(DevIcon("ruby"), "rb", "gemspec")
        put(DevIcon("php"), "php")
        put(DevIcon("rust", monochrome = true), "rs")
        put(DevIcon("bash"), "sh", "bash", "zsh")
        put(DevIcon("vuejs"), "vue")
        put(DevIcon("nodejs"), "node")
        put(DevIcon("json", monochrome = true), "json")
        put(DevIcon("yaml", monochrome = true), "yml", "yaml")
        put(DevIcon("docker"), "dockerfile")
    }

    /** 拡張子に依存しない特定ファイル名(完全一致, 小文字)。 */
    private val byName: Map<String, DevIcon> = mapOf(
        "dockerfile" to DevIcon("docker"),
        ".gitignore" to DevIcon("git"),
        ".gitattributes" to DevIcon("git"),
        ".gitmodules" to DevIcon("git"),
        "build.gradle" to DevIcon("gradle"),
        "build.gradle.kts" to DevIcon("gradle"),
        "settings.gradle.kts" to DevIcon("gradle"),
    )

    /** ファイル名からアイコンを推定する。未対応は null。 */
    fun forFile(name: String): DevIcon? {
        byName[name.lowercase()]?.let { return it }
        val ext = name.substringAfterLast('.', "").lowercase()
        return byExt[ext]
    }
}
