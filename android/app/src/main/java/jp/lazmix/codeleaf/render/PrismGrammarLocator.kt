package jp.lazmix.codeleaf.render

import io.noties.prism4j.annotations.PrismBundle

/**
 * prism4j-bundler(kapt の Java アノテーションプロセッサ)に対応文法を伝えるためのマーカー。
 * ビルド時に同パッケージへ `GrammarLocatorDef`(GrammarLocator 実装)を生成する。
 *
 * include に挙げた言語のみバンドルされる(prism4j 2.0.0 に同梱される文法名に限る。
 * bash/typescript/rust 等は未同梱 → 手書き文法を [CodeGrammarLocator] で重ねる)。
 * 依存文法(clike/markup 等)は bundler が自動解決する。
 */
@PrismBundle(
    include = [
        "clike", "c", "cpp", "csharp", "java", "kotlin", "groovy", "scala",
        "javascript", "json", "css", "markup", "python", "go", "sql", "yaml",
        "markdown", "swift", "dart", "git", "makefile", "latex", "clojure",
    ],
    grammarLocatorClassName = ".GrammarLocatorDef",
)
internal interface PrismGrammarLocator
