package jp.lazmix.codeleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RepoAvatarTest {

    @Test
    fun sharedPrefixReposStayDistinct() {
        // 接頭辞 g3- が共通でも末尾で区別できる。
        assertEquals("GIB", repoAvatarLabel("g3-ibss"))
        assertEquals("GDO", repoAvatarLabel("g3-docs"))
        assertEquals("GMA", repoAvatarLabel("g3-manage"))
    }

    @Test
    fun variousNames() {
        assertEquals("GRE", repoAvatarLabel("git-reader"))
        assertEquals("API", repoAvatarLabel("api"))      // 単一セグメントは先頭3文字
        assertEquals("A", repoAvatarLabel("a"))          // 1文字
        assertEquals("WAP", repoAvatarLabel("web_app"))  // _ 区切り(先頭頭文字+末尾2文字)
    }

    @Test
    fun cjkNamesUseTwoChars() {
        // 全角(CJK)は円に収まるよう2文字。
        assertEquals("メモ", repoAvatarLabel("メモ帳ツール"))
        assertEquals("日本", repoAvatarLabel("日本語"))
        assertEquals("テリ", repoAvatarLabel("テスト-リポ")) // 複数セグメント: 先頭頭文字+末尾1文字
        assertEquals("メ", repoAvatarLabel("メ"))            // 1文字はそのまま
    }
}
