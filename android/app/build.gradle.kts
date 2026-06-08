import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // kapt / parcelize は Kotlin プラグイン経由でクラスパス上にあるため version 指定なしで適用する
    kotlin("kapt")
    kotlin("plugin.parcelize")
}

// OAuth の client_id/secret は local.properties(git 管理外) から読み BuildConfig へ。未設定なら空文字。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// 複数のキー名候補を許容（最初に見つかった非空の値を使う）。
// 値は trim する（Java Properties は値末尾の空白を残すため、貼り付けミスの末尾スペースで認証が壊れるのを防ぐ）。
fun secretProp(vararg names: String): String =
    (names.firstNotNullOfOrNull { localProps.getProperty(it)?.trim()?.takeIf { v -> v.isNotEmpty() } } ?: "")
        .replace("\\", "\\\\").replace("\"", "\\\"")

// バージョンは一箇所で管理し、versionCode は versionName から機械的に算出する(付け忘れ防止)。
// 例: 0.2.0 -> 0*10000 + 2*100 + 0 = 200。配布のたびに versionName を上げれば code も単調増加する。
val appVersionName = "0.2.0"
val appVersionCode = appVersionName.split(".").let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }

// リリース署名情報は local.properties(git管理外)から読む。未設定なら release は未署名のまま(CI等で安全)。
fun localProp(name: String): String? = localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeystorePath = localProp("RELEASE_KEYSTORE")

android {
    namespace = "jp.lazmix.codeleaf"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.lazmix.codeleaf"
        minSdk = 31
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BITBUCKET_OAUTH_CLIENT_ID", "\"${secretProp("BITBUCKET_OAUTH_CLIENT_ID", "BITBUCKET_OAUTH_ID")}\"")
        buildConfigField("String", "BITBUCKET_OAUTH_CLIENT_SECRET", "\"${secretProp("BITBUCKET_OAUTH_CLIENT_SECRET", "BITBUCKET_OAUTH_SECRET")}\"")
        // GitHub Device Flow は client_id のみ（secret 不要・失効しない user token を使う）。
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"${secretProp("GITHUB_OAUTH_CLIENT_ID", "GITHUB_OAUTH_ID")}\"")
    }

    // リリース署名。鍵・パスワードは local.properties から読み、リポには入れない。
    // keytool -genkeypair -v -keystore <path> -alias <alias> -keyalg RSA -keysize 2048 -validity 10000
    signingConfigs {
        if (releaseKeystorePath != null && file(releaseKeystorePath).exists()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = localProp("RELEASE_STORE_PASSWORD")
                keyAlias = localProp("RELEASE_KEY_ALIAS")
                keyPassword = localProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 鍵が未設定なら null のまま=未署名(ビルドは通る。配布には署名が必要)。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // APK のファイル名を codeleaf-<versionName>.apk にする(release は接尾辞なし)。
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = if (variant.buildType.name == "release") {
                "codeleaf-${variant.versionName}.apk"
            } else {
                "codeleaf-${variant.versionName}-${variant.buildType.name}.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // JGit の jar が同梱する META-INF を除外（重複・不要分）
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/eclipse.inf",
            )
        }
    }

    // Gradle Managed Devices: system image は git に入れず、ここでデバイスを宣言する。
    // 実行: ./gradlew pixel6Api35DebugAndroidTest （Gradle が system image を自動取得しヘッドレス実行）
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "google_apis"
                }
            }
        }
    }
}

// prism4j / markwon-syntax-highlight が引き込む旧 annotations-java5 は、Kotlin の
// org.jetbrains:annotations と同一クラスを含み dex 重複になるため全体から除外する。
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // markdown
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image)
    implementation(libs.markwon.syntax.highlight)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.prism4j)
    kapt(libs.prism4j.bundler)
    implementation(libs.emoji.java)

    // file icons (Devicon SVG をアセットから描画)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // テキストの文字コード推定(UTF-8/Shift_JIS/EUC-JP 等)
    implementation(libs.juniversalchardet)

    // git
    implementation(libs.jgit)
    implementation(libs.slf4j.simple)

    // local unit test (純粋ロジックの JVM 検証)
    testImplementation(libs.junit)
    // org.json は Android 同梱だが JVM 単体には無いため OAuth 応答 parse テスト用に追加
    testImplementation("org.json:json:20240303")

    // instrumented test (ランタイムART検証)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
