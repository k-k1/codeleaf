package com.k1.gitreader.ui

import com.k1.gitreader.data.IconSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileIconsTest {

    @Test
    fun mapsCommonExtensionsToTypeKey() {
        assertEquals("kotlin", FileIcons.typeKey("Main.kt"))
        assertEquals("java", FileIcons.typeKey("App.java"))
        assertEquals("python", FileIcons.typeKey("script.py"))
        assertEquals("javascript", FileIcons.typeKey("index.js"))
        assertEquals("typescript", FileIcons.typeKey("main.ts"))
        assertEquals("react", FileIcons.typeKey("App.tsx"))
        assertEquals("cplusplus", FileIcons.typeKey("a.cpp"))
        assertEquals("html5", FileIcons.typeKey("page.HTML")) // 大文字も判定
    }

    @Test
    fun specialFileNamesOverrideExtension() {
        assertEquals("docker", FileIcons.typeKey("Dockerfile"))
        assertEquals("git", FileIcons.typeKey(".gitignore"))
        // build.gradle.kts は特定名マップで gradle になる(拡張子 kts より優先)
        assertEquals("gradle", FileIcons.typeKey("build.gradle.kts"))
    }

    @Test
    fun unknownExtensionReturnsNullKey() {
        assertNull(FileIcons.typeKey("archive.zip"))
        assertNull(FileIcons.typeKey("noext"))
        assertNull(FileIcons.typeKey("photo.png"))
    }

    @Test
    fun assetUriPointsToSelectedSetDir() {
        assertEquals(
            "file:///android_asset/devicon/kotlin.svg",
            FileIcons.forFile(IconSet.DEVICON, "Main.kt")?.uri,
        )
        assertEquals(
            "file:///android_asset/material/kotlin.svg",
            FileIcons.forFile(IconSet.MATERIAL, "Main.kt")?.uri,
        )
        assertEquals(
            "file:///android_asset/vscode_icons/kotlin.svg",
            FileIcons.forFile(IconSet.VSCODE, "Main.kt")?.uri,
        )
        assertEquals(
            "file:///android_asset/seti/kotlin.svg",
            FileIcons.forFile(IconSet.SETI, "Main.kt")?.uri,
        )
    }

    @Test
    fun deviconTintsBlackLogosWithOnSurface() {
        assertEquals(IconTint.ON_SURFACE, FileIcons.forFile(IconSet.DEVICON, "README.md")?.tint)
        assertEquals(IconTint.ON_SURFACE, FileIcons.forFile(IconSet.DEVICON, "lib.rs")?.tint)
        assertEquals(IconTint.ON_SURFACE, FileIcons.forFile(IconSet.DEVICON, "data.json")?.tint)
        assertEquals(IconTint.ON_SURFACE, FileIcons.forFile(IconSet.DEVICON, "config.yaml")?.tint)
    }

    @Test
    fun deviconColoredLogosAreNotTinted() {
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.DEVICON, "Main.kt")?.tint)
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.DEVICON, "app.py")?.tint)
    }

    @Test
    fun materialAndVscodeAreNeverTinted() {
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.MATERIAL, "README.md")?.tint)
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.MATERIAL, "Main.kt")?.tint)
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.VSCODE, "data.json")?.tint)
        assertEquals(IconTint.NONE, FileIcons.forFile(IconSet.VSCODE, "Main.kt")?.tint)
    }

    @Test
    fun setiUsesFixedPerTypeColor() {
        val java = FileIcons.forFile(IconSet.SETI, "App.java")!!
        assertEquals(IconTint.FIXED, java.tint)
        assertEquals(0xFFCC3E44, java.color) // red
        val json = FileIcons.forFile(IconSet.SETI, "data.json")!!
        assertEquals(0xFFCBCB41, json.color) // yellow
        // 未マッピングのキー(yaml)は Seti デフォルト色になる
        assertEquals(0xFFD4D7D6, FileIcons.forFile(IconSet.SETI, "config.yaml")!!.color)
    }

    @Test
    fun setiOmitsUncoveredKeys() {
        // Seti には groovy / nodejs アイコンが無い → null(汎用フォールバック)
        assertNull(FileIcons.forFile(IconSet.SETI, "build.groovy"))
        assertNull(FileIcons.forFile(IconSet.SETI, "x.node"))
        // 他セットでは収録されている
        assertNotNull(FileIcons.forFile(IconSet.DEVICON, "build.groovy"))
        assertNotNull(FileIcons.forFile(IconSet.MATERIAL, "build.groovy"))
    }

    @Test
    fun forKeyResolvesSubmoduleGitIcon() {
        assertEquals(
            "file:///android_asset/material/git.svg",
            FileIcons.forKey(IconSet.MATERIAL, "git")?.uri,
        )
        assertEquals(IconTint.FIXED, FileIcons.forKey(IconSet.SETI, "git")?.tint)
    }

    @Test
    fun marksAiFilesAndDirs() {
        assertEquals(FileMark.AI, FileIcons.mark("CLAUDE.md"))
        assertEquals(FileMark.AI, FileIcons.mark("AGENTS.md"))
        assertEquals(FileMark.AI, FileIcons.mark(".claude"))
        assertEquals(FileMark.AI, FileIcons.mark(".cursorrules")) // ドット始まりだが AI が優先
        assertEquals(FileMark.AI, FileIcons.mark("copilot-instructions.md"))
    }

    @Test
    fun marksSecretsButNotTemplatesOrPublicKeys() {
        assertEquals(FileMark.SECRET, FileIcons.mark(".env"))
        assertEquals(FileMark.SECRET, FileIcons.mark(".env.local")) // .env 実体は機密(ドットより優先)
        assertEquals(FileMark.SECRET, FileIcons.mark("server.pem"))
        assertEquals(FileMark.SECRET, FileIcons.mark("id_rsa"))
        assertEquals(FileMark.SECRET, FileIcons.mark("app.keystore"))
        // 雛形と公開鍵は機密でない
        assertEquals(FileMark.DOTFILE, FileIcons.mark(".env.example"))
        assertEquals(FileMark.NONE, FileIcons.mark("id_rsa.pub"))
    }

    @Test
    fun marksGeneratedAndLockFiles() {
        assertEquals(FileMark.GENERATED, FileIcons.mark("package-lock.json"))
        assertEquals(FileMark.GENERATED, FileIcons.mark("yarn.lock"))
        assertEquals(FileMark.GENERATED, FileIcons.mark("Cargo.lock"))
        assertEquals(FileMark.GENERATED, FileIcons.mark("go.sum"))
        assertEquals(FileMark.GENERATED, FileIcons.mark("app.min.js"))
        assertEquals(FileMark.GENERATED, FileIcons.mark("bundle.js.map"))
    }

    @Test
    fun marksImportantDocs() {
        assertEquals(FileMark.DOC, FileIcons.mark("README.md"))
        assertEquals(FileMark.DOC, FileIcons.mark("LICENSE"))
        assertEquals(FileMark.DOC, FileIcons.mark("CONTRIBUTING.md"))
        assertEquals(FileMark.DOC, FileIcons.mark("CHANGELOG.md"))
    }

    @Test
    fun marksDotfilesAndLeavesNormalAlone() {
        assertEquals(FileMark.DOTFILE, FileIcons.mark(".gitignore"))
        assertEquals(FileMark.DOTFILE, FileIcons.mark(".editorconfig"))
        assertEquals(FileMark.DOTFILE, FileIcons.mark(".github"))
        assertEquals(FileMark.NONE, FileIcons.mark("Main.kt"))
        assertEquals(FileMark.NONE, FileIcons.mark("build.gradle"))
    }
}
