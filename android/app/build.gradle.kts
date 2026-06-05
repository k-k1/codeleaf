import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // kapt は Kotlin プラグイン経由でクラスパス上にあるため version 指定なしで適用する
    kotlin("kapt")
}

// OAuth の client_id/secret は local.properties(git 管理外) から読み BuildConfig へ。未設定なら空文字。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// 複数のキー名候補を許容（最初に見つかった非空の値を使う）。
fun secretProp(vararg names: String): String =
    (names.firstNotNullOfOrNull { localProps.getProperty(it)?.takeIf { v -> v.isNotBlank() } } ?: "")
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.k1.gitreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.k1.gitreader"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BITBUCKET_OAUTH_CLIENT_ID", "\"${secretProp("BITBUCKET_OAUTH_CLIENT_ID", "BITBUCKET_OAUTH_ID")}\"")
        buildConfigField("String", "BITBUCKET_OAUTH_CLIENT_SECRET", "\"${secretProp("BITBUCKET_OAUTH_CLIENT_SECRET", "BITBUCKET_OAUTH_SECRET")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.image)
    implementation(libs.markwon.syntax.highlight)
    implementation(libs.prism4j)
    kapt(libs.prism4j.bundler)
    implementation(libs.emoji.java)

    // file icons (Devicon SVG をアセットから描画)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

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
