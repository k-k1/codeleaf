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
            "toml" -> CustomGrammars.toml(prism4j)
            "ini", "cfg", "conf", "properties" -> CustomGrammars.ini(prism4j)
            "dockerfile", "docker" -> CustomGrammars.dockerfile(prism4j)
            "diff", "patch" -> CustomGrammars.diff(prism4j)
            "ruby", "rb" -> CustomGrammars.ruby(prism4j)
            "php" -> CustomGrammars.php(prism4j)
            "lua" -> CustomGrammars.lua(prism4j)
            "hcl", "terraform", "tf" -> CustomGrammars.hcl(prism4j)
            else -> base.grammar(prism4j, language)
        }

    override fun languages(): Set<String> =
        base.languages() + setOf(
            "bash", "typescript", "rust", "toml", "ini", "dockerfile", "diff",
            "ruby", "php", "lua", "hcl",
        )
}

/**
 * 手書き Prism4j 文法。上流 PrismJS の各 component を Java 正規表現へ移植した実用サブセット。
 *
 * prism4j 規約: lookbehind は java の `(?<=)` ではなく「pattern の第1キャプチャ群＋lookbehind=true」で表現し、
 * マッチからその群を除いて着色する。greedy=true は文字列等の貪欲マッチに使う。
 */
internal object CustomGrammars {

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

    // ---- toml ----------------------------------------------------------------

    private const val ML = JPattern.MULTILINE
    private const val ML_CI = JPattern.MULTILINE or JPattern.CASE_INSENSITIVE

    /** PrismJS toml component の実用サブセット。 */
    fun toml(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "toml",
        token("comment", p("#.*")),
        token(
            "table",
            p("(^[ \\t]*\\[\\[?)[^\\]\\r\\n]+(?=\\]\\]?)", lookbehind = true, flags = ML, alias = "class-name"),
        ),
        token(
            "key",
            p("(^[ \\t]*)[-\\w.\"']+(?=[ \\t]*=)", lookbehind = true, flags = ML, alias = "property"),
        ),
        token(
            "string",
            p(
                "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\\\\\"\\r\\n])*\"|'[^'\\r\\n]*'",
                greedy = true,
            ),
        ),
        token(
            "date",
            p(
                "\\b\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?)?\\b",
                alias = "number",
            ),
        ),
        token(
            "number",
            p("[+-]?\\b(?:0x[\\da-fA-F_]+|0o[0-7_]+|0b[01_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?|inf|nan)\\b"),
        ),
        token("boolean", p("\\b(?:true|false)\\b")),
        token("punctuation", p("[\\[\\]{}.,=]")),
    )

    // ---- ini -----------------------------------------------------------------

    /** PrismJS ini component の実用サブセット(properties/conf にも流用)。 */
    fun ini(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "ini",
        token("comment", p("(^[ \\t]*)[#;].*", lookbehind = true, flags = ML)),
        token("section", p("^[ \\t]*\\[[^\\]\\r\\n]*\\]", flags = ML, alias = "class-name")),
        token(
            "key",
            p("(^[ \\t]*)[^=\\r\\n\\[#;][^=\\r\\n]*?(?=[ \\t]*=)", lookbehind = true, flags = ML, alias = "property"),
        ),
        token("value", p("(=[ \\t]*)[^\\r\\n]*", lookbehind = true, flags = ML, alias = "string")),
        token("punctuation", p("=")),
    )

    // ---- dockerfile ----------------------------------------------------------

    /** PrismJS dockerfile component の実用サブセット(命令は大文字小文字非依存)。 */
    fun dockerfile(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "dockerfile",
        token("comment", p("#.*")),
        token(
            "instruction",
            p(
                "(^[ \\t]*)(?:ADD|ARG|CMD|COPY|ENTRYPOINT|ENV|EXPOSE|FROM|HEALTHCHECK|LABEL|MAINTAINER|ONBUILD|RUN|SHELL|STOPSIGNAL|USER|VOLUME|WORKDIR)\\b",
                lookbehind = true,
                flags = ML_CI,
                alias = "keyword",
            ),
        ),
        token(
            "string",
            p("\"(?:\\\\.|[^\\\\\"\\r\\n])*\"|'(?:\\\\.|[^\\\\'\\r\\n])*'", greedy = true),
        ),
        token("variable", p("\\$\\{[^}\\r\\n]+\\}|\\$\\w+")),
        token("operator", p("\\\\(?=\\s*$)", flags = ML)),
    )

    // ---- diff ----------------------------------------------------------------

    /** PrismJS diff component の実用サブセット。行頭の +/- と @@/ヘッダで着色する。 */
    fun diff(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "diff",
        token(
            "coord",
            p("^(?:@@[^\\r\\n]*@@|diff [^\\r\\n]*|index [^\\r\\n]*|[-+]{3} [^\\r\\n]*)", flags = ML, alias = "comment"),
        ),
        token("inserted", p("^\\+[^\\r\\n]*", flags = ML)),
        token("deleted", p("^-[^\\r\\n]*", flags = ML)),
    )

    // ---- ruby ----------------------------------------------------------------

    /** PrismJS ruby component の実用サブセット(%リテラル/補間の細部は簡略化)。 */
    fun ruby(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "ruby",
        token(
            "comment",
            p("^=begin[\\s\\S]*?^=end", flags = ML),
            p("#.*"),
        ),
        token(
            "string",
            // %w[] %i() %q{} など(区切りは括弧4種のみ対応)
            p("%[qQwWiIrsx]?(?:\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}|<[^>]*>)", greedy = true),
            p("\"(?:\\\\[\\s\\S]|#\\{[^}]*\\}|[^\\\\\"])*\"", greedy = true),
            p("'(?:\\\\[\\s\\S]|[^\\\\'])*'", greedy = true),
        ),
        token(
            "symbol",
            p("(^|[^:]):[a-zA-Z_]\\w*[?!=]?", lookbehind = true),
        ),
        token(
            "variable",
            p("@@?[a-zA-Z_]\\w*"),
            p("\\$[a-zA-Z_]\\w*"),
        ),
        token(
            "keyword",
            p(
                "\\b(?:alias|and|begin|BEGIN|break|case|class|def|defined|do|else|elsif|end|END|ensure|extend|for|if|in|include|module|new|next|nil|not|or|prepend|private|protected|public|raise|redo|require|require_relative|rescue|retry|return|self|super|then|throw|undef|unless|until|when|while|yield)\\b",
            ),
        ),
        token(
            "boolean",
            p("\\b(?:true|false)\\b"),
        ),
        token(
            "function",
            p("(\\bdef\\s+)[a-zA-Z_]\\w*[?!=]?", lookbehind = true),
            p("\\b[a-zA-Z_]\\w*[?!]?(?=\\s*\\()"),
        ),
        token(
            "class-name",
            p("\\b[A-Z]\\w*\\b"),
        ),
        token(
            "number",
            p("\\b(?:0x[\\da-fA-F_]+|0b[01_]+|0o[0-7_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)\\b"),
        ),
        token(
            "operator",
            p("&&|\\|\\||<=>|===?|=~|!~|=>|\\*\\*=?|\\.\\.\\.?|[-+*/%!=<>&|^~]=?|::|[?&]"),
        ),
        token(
            "punctuation",
            p("[{}\\[\\];(),.]"),
        ),
    )

    // ---- php ------------------------------------------------------------------

    /** PrismJS php component の実用サブセット(markup 混在は扱わずスタンドアロン着色)。 */
    fun php(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "php",
        token(
            "delimiter",
            p("<\\?(?:php|=)?|\\?>", alias = "important"),
        ),
        token(
            "comment",
            p("(^|[^\\\\])(?:/\\*[\\s\\S]*?\\*/|(?://|#).*)", lookbehind = true, greedy = true),
        ),
        token(
            "string",
            // ヒアドキュメント/ナウドキュメント(簡易)
            p("<<<'?\"?(\\w+)\"?'?[\\r\\n][\\s\\S]*?[\\r\\n][ \\t]*\\1\\b", greedy = true),
            p("\"(?:\\\\[\\s\\S]|[^\\\\\"])*\"", greedy = true),
            p("'(?:\\\\[\\s\\S]|[^\\\\'])*'", greedy = true),
        ),
        token(
            "variable",
            p("\\$+[a-zA-Z_]\\w*"),
        ),
        token(
            "keyword",
            p(
                "\\b(?:abstract|and|array|as|break|callable|case|catch|class|clone|const|continue|declare|default|do|echo|else|elseif|empty|enddeclare|endfor|endforeach|endif|endswitch|endwhile|enum|extends|final|finally|fn|for|foreach|function|global|goto|if|implements|include|include_once|instanceof|insteadof|interface|isset|list|match|namespace|new|or|print|private|protected|public|readonly|require|require_once|return|static|switch|throw|trait|try|unset|use|var|while|xor|yield)\\b",
                flags = JPattern.CASE_INSENSITIVE,
            ),
        ),
        token(
            "boolean",
            p("\\b(?:true|false|null)\\b", flags = JPattern.CASE_INSENSITIVE),
        ),
        token(
            "class-name",
            p("(\\b(?:class|interface|trait|extends|implements|new|enum|instanceof)\\s+)[A-Za-z_]\\w*", lookbehind = true),
        ),
        token(
            "function",
            p("\\b[a-zA-Z_]\\w*(?=\\s*\\()"),
        ),
        token(
            "constant",
            p("\\b[A-Z_][A-Z0-9_]*\\b"),
        ),
        token(
            "number",
            p("\\b(?:0x[\\da-fA-F]+|0b[01]+|0o[0-7]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b"),
        ),
        token(
            "operator",
            p("\\?\\?=?|->|=>|::|\\*\\*=?|&&|\\|\\||<=>|===?|!==?|<<|>>|[-+*/%.!=<>&|^~?:]=?"),
        ),
        token(
            "punctuation",
            p("[{}\\[\\];(),\\\\]"),
        ),
    )

    // ---- lua ------------------------------------------------------------------

    /** PrismJS lua component の実用サブセット(長括弧文字列/コメントに対応)。 */
    fun lua(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "lua",
        token(
            "comment",
            p("--\\[(=*)\\[[\\s\\S]*?\\]\\1\\]", greedy = true),
            p("--.*"),
        ),
        token(
            "string",
            p("\\[(=*)\\[[\\s\\S]*?\\]\\1\\]", greedy = true),
            p("\"(?:\\\\[\\s\\S]|[^\\\\\"\\r\\n])*\"", greedy = true),
            p("'(?:\\\\[\\s\\S]|[^\\\\'\\r\\n])*'", greedy = true),
        ),
        token(
            "keyword",
            p(
                "\\b(?:and|break|do|else|elseif|end|for|function|goto|if|in|local|not|or|repeat|return|then|until|while)\\b",
            ),
        ),
        token(
            "boolean",
            p("\\b(?:true|false|nil)\\b"),
        ),
        token(
            "function",
            p("\\b[a-zA-Z_]\\w*(?=\\s*[({\"'])"),
        ),
        token(
            "number",
            p(
                "\\b0x[a-fA-F\\d]+(?:\\.[a-fA-F\\d]*)?(?:[pP][+-]?\\d+)?\\b|\\b\\d+(?:\\.\\d*)?(?:[eE][+-]?\\d+)?\\b|\\.\\d+(?:[eE][+-]?\\d+)?",
            ),
        ),
        token(
            "operator",
            p("==|~=|<=|>=|\\.\\.\\.?|//|[-+*/%^#<>=]"),
        ),
        token(
            "punctuation",
            p("[{}\\[\\]();:,.]"),
        ),
    )

    // ---- hcl / terraform -----------------------------------------------------

    /** PrismJS hcl component の実用サブセット(Terraform 等。ブロック型/属性/補間を着色)。 */
    fun hcl(@Suppress("UNUSED_PARAMETER") prism4j: Prism4j): Prism4j.Grammar = grammar(
        "hcl",
        token(
            "comment",
            p("(?:#|//).*|/\\*[\\s\\S]*?\\*/", greedy = true),
        ),
        token(
            "heredoc",
            p("<<-?(\\w+)[\\s\\S]*?^[ \\t]*\\1", flags = ML, greedy = true, alias = "string"),
        ),
        token(
            "keyword",
            p(
                "(^[ \\t]*)(?:resource|provider|variable|output|module|data|terraform|locals|backend|provisioner|connection|dynamic)\\b",
                lookbehind = true,
                flags = ML,
            ),
        ),
        token(
            "property",
            p("(^[ \\t]*)[\\w-]+(?=[ \\t]*=(?!=))", lookbehind = true, flags = ML),
        ),
        token(
            "string",
            p("\"(?:\\\\[\\s\\S]|\\$\\{[^}]*\\}|[^\\\\\"])*\"", greedy = true),
        ),
        token(
            "boolean",
            p("\\b(?:true|false|null)\\b"),
        ),
        token(
            "number",
            p("\\b\\d+(?:\\.\\d+)?\\b"),
        ),
        token(
            "type",
            p("\\b(?:string|number|bool|list|map|set|object|tuple|any)\\b", alias = "class-name"),
        ),
        token(
            "operator",
            p("=>|==|!=|<=|>=|&&|\\|\\||[-+*/%!=<>?:]"),
        ),
        token(
            "punctuation",
            p("[{}\\[\\](),.]"),
        ),
    )
}
