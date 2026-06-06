package jp.lazmix.codeleaf.render

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.GrammarUtils
import io.noties.prism4j.Prism4j
import java.util.regex.Pattern as JPattern

/**
 * prism4j-bundler は 2.0.0 同梱の文法しか生成できない([GrammarLocatorDef])。
 * よく使われるのに未同梱の bash / typescript / rust を手書き文法として重ねるためのラッパー。
 *
 * 生成済み [GrammarLocatorDef] に委譲しつつ、未同梱言語(とその別名)だけ自前の文法を返す。
 * 別名は fence の言語指定(```sh / ```ts など)と [CodeHighlight.languageForFile] の双方を受ける。
 */
class CodeGrammarLocator(
    private val base: GrammarLocator = GrammarLocatorDef(),
) : GrammarLocator {

    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? =
        when (language) {
            "bash", "shell", "sh", "shell-session", "zsh" -> CustomGrammars.bash(prism4j)
            "typescript", "ts", "tsx" -> CustomGrammars.typescript(prism4j)
            "rust", "rs" -> CustomGrammars.rust(prism4j)
            else -> base.grammar(prism4j, language)
        }

    override fun languages(): Set<String> =
        base.languages() + setOf("bash", "typescript", "rust")
}

/**
 * 手書き Prism4j 文法。上流 PrismJS の各 component を Java 正規表現へ移植した実用サブセット。
 *
 * prism4j 規約: lookbehind は java の `(?<=)` ではなく「pattern の第1キャプチャ群＋lookbehind=true」で表現し、
 * マッチからその群を除いて着色する。greedy=true は文字列等の貪欲マッチに使う。
 */
object CustomGrammars {

    private fun p(
        regex: String,
        lookbehind: Boolean = false,
        greedy: Boolean = false,
        alias: String? = null,
        inside: Prism4j.Grammar? = null,
        flags: Int = 0,
    ): Prism4j.Pattern =
        Prism4j.pattern(JPattern.compile(regex, flags), lookbehind, greedy, alias, inside)

    private fun token(name: String, vararg patterns: Prism4j.Pattern): Prism4j.Token =
        Prism4j.token(name, *patterns)

    private fun grammar(name: String, vararg tokens: Prism4j.Token): Prism4j.Grammar =
        Prism4j.grammar(name, *tokens)

    // ---- bash ----------------------------------------------------------------

    /** PrismJS bash component の実用サブセット(string 内補間や演算子の細部は省略)。 */
    fun bash(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "bash",
        token(
            "shebang",
            p("^#!\\s*/.*", alias = "important"),
        ),
        token(
            "comment",
            p("(^|[^\"{\\\\$])#.*", lookbehind = true),
        ),
        token(
            "string",
            // ヒアドキュメント(引用なし=変数展開あり / 引用あり=なし)
            p(
                "((?:^|[^<])<<-?\\s*)(\\w+)\\s[\\s\\S]*?(?:\\r?\\n|\\r)\\2",
                lookbehind = true,
                greedy = true,
            ),
            // 二重引用符・単一引用符
            p(
                "(^|[^\\\\](?:\\\\\\\\)*)\"(?:\\\\[\\s\\S]|\\$\\([^)]+\\)|`[^`]+`|(?!\")[^\\\\])*\"",
                lookbehind = true,
                greedy = true,
            ),
            p(
                "(^|[^$\\\\])'[^']*'",
                lookbehind = true,
                greedy = true,
            ),
        ),
        token(
            "variable",
            p("\\$(?:\\w+|[#?*!@$])"),
            p("\\$\\{[^}]+\\}"),
        ),
        token(
            "function",
            p(
                "(^|[\\s;|&]|[<>]\\()(?:add|apt|apt-get|aptitude|aws|bash|cat|cd|chmod|chown|cp|curl|cut|docker|echo|env|export|find|git|grep|gunzip|gzip|head|kill|ln|ls|make|mkdir|mv|node|npm|npx|printf|pwd|python|python3|read|rm|rmdir|sed|set|sleep|sort|source|ssh|sudo|tail|tar|touch|unzip|wget|which|yarn)(?=$|[)\\s;|&])",
                lookbehind = true,
            ),
        ),
        token(
            "keyword",
            p(
                "(^|[\\s;|&]|[<>]\\()(?:if|then|else|elif|fi|for|while|in|case|esac|function|select|do|done|until)(?=$|[)\\s;|&])",
                lookbehind = true,
            ),
        ),
        token(
            "boolean",
            p("(^|[\\s;|&]|[<>]\\()(?:true|false)(?=$|[)\\s;|&])", lookbehind = true),
        ),
        token(
            "number",
            p("(^|[\\s;|&(])(?:[+-]?(?:0x[\\da-fA-F]+|\\d*\\.?\\d+))(?=$|[)\\s;|&])", lookbehind = true),
        ),
        token(
            "operator",
            p("&&|\\|\\||==|!=|=~|<=|>=|[!<>=&|]|[-+*/%]"),
        ),
        token(
            "punctuation",
            p("\\$?\\(\\(?|\\)\\)?|\\.\\.|[{}\\[\\];]"),
        ),
    )

    // ---- typescript ----------------------------------------------------------

    /** javascript(同梱)を extend し、TS の型キーワード・装飾を追加する。 */
    fun typescript(prism4j: Prism4j): Prism4j.Grammar {
        val ts = GrammarUtils.extend(
            GrammarUtils.require(prism4j, "javascript"),
            "typescript",
            token(
                "keyword",
                p(
                    "(^|[^.]|\\.\\.\\.\\s*)\\b(?:abstract|as|asserts|async|await|break|case|catch|class|const|continue|debugger|declare|default|delete|do|else|enum|export|extends|finally|for|from|function|get|if|implements|import|in|infer|instanceof|interface|is|keyof|let|namespace|new|of|package|private|protected|public|readonly|return|require|satisfies|set|static|super|switch|this|throw|try|type|typeof|undefined|var|void|while|with|yield)\\b",
                    lookbehind = true,
                ),
            ),
            token(
                "builtin",
                p("\\b(?:string|number|boolean|symbol|object|unknown|never|any|bigint|Array|Promise|Record|Partial|Readonly|Pick|Omit|Map|Set)\\b"),
            ),
        )
        // 型注釈の Generic 山括弧などはそのまま javascript 側の punctuation に委ねる。
        return ts
    }

    // ---- rust ----------------------------------------------------------------

    /** PrismJS rust component の実用サブセット。 */
    fun rust(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "rust",
        token(
            "comment",
            p("(^|[^\\\\])/\\*[\\s\\S]*?\\*/", lookbehind = true, greedy = true),
            p("(^|[^\\\\:])//.*", lookbehind = true, greedy = true),
        ),
        token(
            "string",
            // raw string r"...", r#"..."#
            p("b?r(#*)\"(?:\\\\[\\s\\S]|(?!\"\\1)[^\\\\])*\"\\1", greedy = true),
            p("b?\"(?:\\\\[\\s\\S]|[^\\\\\"])*\"", greedy = true),
        ),
        token(
            "char",
            p("b?'(?:\\\\(?:x[0-7][\\da-fA-F]|u\\{(?:[\\da-fA-F]_*){1,6}\\}|.)|[^\\\\'\\n])'", greedy = true),
        ),
        token(
            "attribute",
            p("#!?\\[[^\\[\\]]*\\]", greedy = true, alias = "attr-name"),
        ),
        // 関数呼び出し/マクロ
        token(
            "function-definition",
            p("(\\bfn\\s+)\\w+", lookbehind = true, alias = "function"),
        ),
        token(
            "macro",
            p("\\b\\w+!", alias = "property"),
        ),
        token(
            "keyword",
            p(
                "\\b(?:as|async|await|break|const|continue|crate|dyn|else|enum|extern|fn|for|if|impl|in|let|loop|match|mod|move|mut|pub|ref|return|self|Self|static|struct|super|trait|type|union|unsafe|use|where|while)\\b",
            ),
        ),
        token(
            "type",
            p(
                "\\b(?:[ui](?:8|16|32|64|128|size)|f32|f64|bool|char|str|String|Vec|Box|Option|Result|Rc|Arc|RefCell|Cell|HashMap|HashSet|BTreeMap)\\b",
                alias = "class-name",
            ),
            p("\\b[A-Z][A-Za-z0-9_]*\\b", alias = "class-name"),
        ),
        token(
            "boolean",
            p("\\b(?:true|false)\\b"),
        ),
        token(
            "lifetime",
            p("'(?:[A-Za-z_]\\w*|static)\\b", alias = "symbol"),
        ),
        token(
            "number",
            p(
                "\\b(?:0x[\\da-fA-F_]+|0o[0-7_]+|0b[01_]+|(?:\\d_*)+(?:\\.(?:\\d_*)+)?(?:[eE][+-]?\\d+)?)(?:[iu](?:8|16|32|64|128|size)|f32|f64)?\\b",
            ),
        ),
        token(
            "operator",
            p("->|=>|\\.\\.=?|::|&&|\\|\\||[-+*/%!=<>&|^]=?|[?@~]"),
        ),
        token(
            "punctuation",
            p("[{}\\[\\];(),.:]"),
        ),
    )
}
