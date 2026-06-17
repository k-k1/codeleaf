package jp.lazmix.codeleaf.render

import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j

/**
 * 非 Markdown ファイルのコードハイライト用ヘルパ。
 * Prism4j(GrammarLocatorDef は prism4j-bundler が生成)を共有し、Markdown フェンス用と
 * コードファイル全体表示用の双方で同じ文法・テーマを使う。
 */
object CodeHighlight {

    /**
     * GrammarLocatorDef は kapt(prism4j-bundler)が PrismGrammarLocator から生成する。
     * [CodeGrammarLocator] で重ねて未同梱の bash/typescript/rust を補う。
     */
    val prism4j: Prism4j by lazy { Prism4j(CodeGrammarLocator()) }

    fun theme(dark: Boolean): Prism4jTheme =
        if (dark) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create()

    /** language が null/未対応の場合は素のテキスト(span なし)が返る。 */
    fun highlight(language: String?, code: String, dark: Boolean): CharSequence =
        Prism4jSyntaxHighlight.create(prism4j, theme(dark)).highlight(language ?: "", code)

    /** ファイル名(拡張子)から Prism4j の言語 ID を推定する。未対応は null。 */
    fun languageForFile(name: String): String? {
        // 拡張子を持たない特殊ファイル名(Makefile 等)を先に拾う。
        when (name.substringAfterLast('/').lowercase()) {
            "makefile", "makefile.am", "gnumakefile" -> return "makefile"
            ".bashrc", ".bash_profile", ".zshrc", ".profile" -> return "bash"
            "dockerfile", "containerfile" -> return "dockerfile"
            "gemfile", "rakefile", "guardfile", "capfile", "vagrantfile", "podfile", "brewfile", "berksfile" ->
                return "ruby"
        }
        return when (name.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "groovy", "gradle" -> "groovy"
            "scala", "sc" -> "scala"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
            "cs" -> "csharp"
            "js", "mjs", "cjs", "jsx" -> "javascript"
            "ts", "tsx", "mts", "cts" -> "typescript"
            "rs" -> "rust"
            "rb", "rake", "gemspec", "podspec", "ru" -> "ruby"
            "php", "phtml", "php3", "php4", "php5", "phps" -> "php"
            "lua" -> "lua"
            "tf", "tfvars", "hcl" -> "hcl"
            "sh", "bash", "zsh" -> "bash"
            "json" -> "json"
            "css" -> "css"
            "html", "htm", "xml", "xhtml", "svg" -> "markup"
            "py" -> "python"
            "go" -> "go"
            "sql" -> "sql"
            "yml", "yaml" -> "yaml"
            "swift" -> "swift"
            "dart" -> "dart"
            "mk" -> "makefile"
            "tex" -> "latex"
            "clj", "cljs", "cljc", "edn" -> "clojure"
            "toml" -> "toml"
            "ini", "cfg", "conf", "properties" -> "ini"
            "dockerfile" -> "dockerfile"
            "diff", "patch" -> "diff"
            "md", "markdown" -> "markdown"
            else -> null
        }
    }
}
