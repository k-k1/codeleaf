package com.k1.gitreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileIconsTest {

    @Test
    fun mapsCommonExtensions() {
        assertEquals("kotlin", FileIcons.forFile("Main.kt")?.asset)
        assertEquals("java", FileIcons.forFile("App.java")?.asset)
        assertEquals("python", FileIcons.forFile("script.py")?.asset)
        assertEquals("javascript", FileIcons.forFile("index.js")?.asset)
        assertEquals("typescript", FileIcons.forFile("main.ts")?.asset)
        assertEquals("react", FileIcons.forFile("App.tsx")?.asset)
        assertEquals("cplusplus", FileIcons.forFile("a.cpp")?.asset)
        assertEquals("html5", FileIcons.forFile("page.HTML")?.asset) // 大文字も判定
    }

    @Test
    fun specialFileNamesOverrideExtension() {
        assertEquals("docker", FileIcons.forFile("Dockerfile")?.asset)
        assertEquals("git", FileIcons.forFile(".gitignore")?.asset)
        // build.gradle.kts は特定名マップで gradle になる(拡張子 kts より優先)
        assertEquals("gradle", FileIcons.forFile("build.gradle.kts")?.asset)
    }

    @Test
    fun monochromeFlagSetForBlackLogos() {
        assertTrue(FileIcons.forFile("README.md")!!.monochrome)
        assertTrue(FileIcons.forFile("lib.rs")!!.monochrome)
        assertTrue(FileIcons.forFile("config.yaml")!!.monochrome)
        assertTrue(FileIcons.forFile("data.json")!!.monochrome)
    }

    @Test
    fun coloredLogosAreNotMonochrome() {
        assertEquals(false, FileIcons.forFile("Main.kt")!!.monochrome)
        assertEquals(false, FileIcons.forFile("app.py")!!.monochrome)
    }

    @Test
    fun unknownExtensionReturnsNull() {
        assertNull(FileIcons.forFile("archive.zip"))
        assertNull(FileIcons.forFile("noext"))
        assertNull(FileIcons.forFile("photo.png"))
    }

    @Test
    fun assetUriPointsToBundledSvg() {
        val icon = FileIcons.forFile("Main.kt")!!
        assertEquals("file:///android_asset/devicon/kotlin.svg", FileIcons.assetUri(icon))
    }
}
