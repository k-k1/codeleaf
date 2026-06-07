package jp.lazmix.codeleaf.render

import io.noties.prism4j.Prism4j
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手書き文法(bash/typescript/rust)と新規同梱(git/makefile/latex/clojure)の JVM 単体テスト。
 * Prism4j は純 Java(Android 非依存)なので JVM で tokenize でき、正規表現の破綻も検出できる。
 */
class CodeGrammarLocatorTest {

    private val prism4j = Prism4j(CodeGrammarLocator())

    /** tokenize 結果(ネスト含む)に現れる Syntax の type 名を全て集める。 */
    private fun tokenTypes(language: String, code: String): Set<String> {
        val grammar = prism4j.grammar(language)
            ?: throw AssertionError("grammar '$language' must resolve")
        val types = mutableSetOf<String>()
        fun walk(nodes: List<Prism4j.Node>) {
            for (node in nodes) {
                if (node.isSyntax) {
                    val s = node as Prism4j.Syntax
                    types += s.type()
                    walk(s.children())
                }
            }
        }
        walk(prism4j.tokenize(code, grammar))
        return types
    }

    @Test
    fun locator_exposes_custom_and_bundled_languages() {
        val langs = CodeGrammarLocator().languages()
        // 手書き
        assertTrue(
            langs.containsAll(
                listOf(
                    "bash", "typescript", "rust", "toml", "ini", "dockerfile", "diff",
                    "ruby", "php", "lua", "hcl",
                ),
            ),
        )
        // 新規同梱(bundler が @PrismBundle から生成)
        assertTrue(langs.containsAll(listOf("git", "makefile", "latex", "clojure")))
        // 既存同梱も引き続き解決できる
        assertNotNull(prism4j.grammar("kotlin"))
    }

    @Test
    fun unknown_language_resolves_to_null() {
        assertNull(prism4j.grammar("no-such-language"))
    }

    @Test
    fun bash_highlights_core_tokens() {
        val types = tokenTypes(
            "bash",
            """
            #!/usr/bin/env bash
            # build the project
            set -euo pipefail
            NAME="world"
            cd ${'$'}HOME
            if [ -n "${'$'}NAME" ]; then
              echo "hello ${'$'}NAME"
            fi
            """.trimIndent(),
        )
        assertTrue("shebang", "shebang" in types)
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (if/then/fi)", "keyword" in types)
        assertTrue("string", "string" in types)
        assertTrue("variable", "variable" in types)
    }

    @Test
    fun typescript_highlights_type_keywords() {
        val types = tokenTypes(
            "typescript",
            """
            interface User { name: string; age: number }
            const greet = (u: User): string => `hi ${'$'}{u.name}`
            """.trimIndent(),
        )
        assertTrue("keyword (interface/const)", "keyword" in types)
        assertTrue("string template", types.any { it == "string" || it == "template-string" })
        // javascript 由来のトークンも引き継ぐ
        assertTrue("punctuation", "punctuation" in types)
    }

    @Test
    fun rust_highlights_core_tokens() {
        val types = tokenTypes(
            "rust",
            """
            // entry point
            fn main() {
                let name: &str = "world";
                let count: u32 = 42;
                println!("hello {}", name);
            }
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (fn/let)", "keyword" in types)
        assertTrue("string", "string" in types)
        assertTrue("number", "number" in types)
        assertTrue("macro (println!)", "macro" in types)
        assertTrue("type (u32/&str)", "type" in types || "class-name" in types)
    }

    @Test
    fun toml_highlights_core_tokens() {
        val types = tokenTypes(
            "toml",
            """
            # config
            [server]
            host = "localhost"
            port = 8080
            enabled = true
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("table", "table" in types)
        assertTrue("key", "key" in types)
        assertTrue("string", "string" in types)
        assertTrue("number", "number" in types)
        assertTrue("boolean", "boolean" in types)
    }

    @Test
    fun ini_highlights_core_tokens() {
        val types = tokenTypes(
            "ini",
            """
            ; comment
            [section]
            key = value
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("section", "section" in types)
        assertTrue("key", "key" in types)
        assertTrue("value", "value" in types)
    }

    @Test
    fun dockerfile_highlights_instructions() {
        val types = tokenTypes(
            "dockerfile",
            """
            # base image
            FROM alpine:3.19
            RUN apk add --no-cache curl
            ENV NAME="world"
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("instruction", "instruction" in types)
        assertTrue("string", "string" in types)
    }

    @Test
    fun diff_highlights_added_and_removed() {
        val types = tokenTypes(
            "diff",
            """
            @@ -1,3 +1,3 @@
            -val old = 1
            +val new = 2
             unchanged
            """.trimIndent(),
        )
        assertTrue("coord", "coord" in types)
        assertTrue("inserted", "inserted" in types)
        assertTrue("deleted", "deleted" in types)
    }

    @Test
    fun ruby_highlights_core_tokens() {
        val types = tokenTypes(
            "ruby",
            """
            # a greeter
            class Greeter
              def initialize(name)
                @name = name
              end

              def greet
                puts "hello #{'$'}{@name}"
                :done
              end
            end
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (class/def/end)", "keyword" in types)
        assertTrue("string", "string" in types)
        assertTrue("variable (@name)", "variable" in types)
        assertTrue("symbol (:done)", "symbol" in types)
        assertTrue("class-name (Greeter)", "class-name" in types)
    }

    @Test
    fun php_highlights_core_tokens() {
        val types = tokenTypes(
            "php",
            """
            <?php
            // greet
            function greet(${'$'}name) {
                ${'$'}msg = "hello ${'$'}name";
                return ${'$'}msg;
            }
            ?>
            """.trimIndent(),
        )
        assertTrue("delimiter", "delimiter" in types)
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (function/return)", "keyword" in types)
        assertTrue("variable (${'$'}name)", "variable" in types)
        assertTrue("string", "string" in types)
    }

    @Test
    fun lua_highlights_core_tokens() {
        val types = tokenTypes(
            "lua",
            """
            -- greet
            local function greet(name)
              local msg = "hello " .. name
              print(msg)
              return true
            end
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (local/function/end)", "keyword" in types)
        assertTrue("string", "string" in types)
        assertTrue("boolean (true)", "boolean" in types)
        assertTrue("function (print)", "function" in types)
    }

    @Test
    fun hcl_highlights_core_tokens() {
        val types = tokenTypes(
            "hcl",
            """
            # an instance
            resource "aws_instance" "web" {
              ami           = "ami-123456"
              instance_type = "t3.micro"
              count         = 2
              enabled       = true
            }
            """.trimIndent(),
        )
        assertTrue("comment", "comment" in types)
        assertTrue("keyword (resource)", "keyword" in types)
        assertTrue("property (ami/count)", "property" in types)
        assertTrue("string", "string" in types)
        assertTrue("number", "number" in types)
        assertTrue("boolean (true)", "boolean" in types)
    }

    @Test
    fun languageForFile_maps_new_extensions() {
        assertEquals("typescript", CodeHighlight.languageForFile("app.ts"))
        assertEquals("typescript", CodeHighlight.languageForFile("App.tsx"))
        assertEquals("rust", CodeHighlight.languageForFile("main.rs"))
        assertEquals("bash", CodeHighlight.languageForFile("build.sh"))
        assertEquals("makefile", CodeHighlight.languageForFile("Makefile"))
        assertEquals("makefile", CodeHighlight.languageForFile("src/Makefile"))
        assertEquals("bash", CodeHighlight.languageForFile(".bashrc"))
        assertEquals("latex", CodeHighlight.languageForFile("paper.tex"))
        assertEquals("clojure", CodeHighlight.languageForFile("core.clj"))
        assertEquals("diff", CodeHighlight.languageForFile("fix.patch"))
        assertEquals("toml", CodeHighlight.languageForFile("Cargo.toml"))
        assertEquals("ini", CodeHighlight.languageForFile("app.ini"))
        assertEquals("dockerfile", CodeHighlight.languageForFile("Dockerfile"))
        assertEquals("dockerfile", CodeHighlight.languageForFile("docker/Dockerfile"))
        assertEquals("ruby", CodeHighlight.languageForFile("app.rb"))
        assertEquals("ruby", CodeHighlight.languageForFile("Gemfile"))
        assertEquals("ruby", CodeHighlight.languageForFile("lib/Rakefile"))
        assertEquals("php", CodeHighlight.languageForFile("index.php"))
        assertEquals("lua", CodeHighlight.languageForFile("init.lua"))
        assertEquals("hcl", CodeHighlight.languageForFile("main.tf"))
        assertEquals("hcl", CodeHighlight.languageForFile("vars.tfvars"))
        assertNull(CodeHighlight.languageForFile("notes.unknownext"))
    }
}
