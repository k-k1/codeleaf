package com.k1.gitreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RepoAvatarTest {

    @Test
    fun sharedPrefixReposStayDistinct() {
        // 接頭辞 g3- が共通でも末尾で区別できる。
        assertEquals("GI", repoAvatarLabel("g3-ibss"))
        assertEquals("GD", repoAvatarLabel("g3-docs"))
        assertEquals("GM", repoAvatarLabel("g3-manage"))
    }

    @Test
    fun variousNames() {
        assertEquals("GR", repoAvatarLabel("git-reader"))
        assertEquals("AP", repoAvatarLabel("api"))      // 単一セグメントは先頭2文字
        assertEquals("A", repoAvatarLabel("a"))         // 1文字
        assertEquals("WA", repoAvatarLabel("web_app"))  // _ 区切り
    }
}
