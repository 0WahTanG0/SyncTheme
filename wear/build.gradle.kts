// Приложение для часов на Wear OS 6+: принимает циферблат от телефона
// и ставит его через Watch Face Push.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.synctheme"
    compileSdk = 36

    defaultConfig {
        // Тот же applicationId и ключ, что у телефона, — иначе Data Layer не свяжет приложения.
        applicationId = "app.synctheme"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        create("app") {
            storeFile = rootProject.file("keystore/app.p12")
            storePassword = "synctheme"
            keyAlias = "synctheme"
            keyPassword = "synctheme"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("app") }
        release {
            // Сжатие уменьшает APK, который телефон передаёт на часы по Wi‑Fi (без переименования классов).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("app")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.watchface.push)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.wear.remote.interactions)
}

// --- Циферблат по умолчанию: APK шаблона + токен валидации, сгенерированный при сборке ---

val buildtools: Configuration by configurations.creating
dependencies { buildtools(project(":buildtools")) }

val watchfaceApk = project(":watchface").layout.buildDirectory
    .file("outputs/apk/release/watchface-release.apk").get().asFile
val defaultWfAssets = layout.buildDirectory.dir("generated/defaultWatchFace/assets").get().asFile
val defaultWfRes = layout.buildDirectory.dir("generated/defaultWatchFace/res").get().asFile
val tokenFile = layout.buildDirectory.file("intermediates/defaultWatchFace/token.txt").get().asFile

val copyDefaultWatchFace = tasks.register<Copy>("copyDefaultWatchFace") {
    dependsOn(":watchface:assembleRelease")
    from(watchfaceApk) { rename { "default_watchface.apk" } }
    into(defaultWfAssets)
}

val validateDefaultWatchFace = tasks.register<JavaExec>("validateDefaultWatchFace") {
    dependsOn(":watchface:assembleRelease")
    classpath = buildtools
    mainClass.set("app.synctheme.buildtools.ValidateApkKt")
    args(watchfaceApk.absolutePath, "app.synctheme", tokenFile.absolutePath)
    inputs.file(watchfaceApk)
    outputs.file(tokenFile)
}

val defaultWatchFaceTokenRes = tasks.register("defaultWatchFaceTokenRes") {
    dependsOn(validateDefaultWatchFace)
    inputs.file(tokenFile)
    outputs.dir(defaultWfRes)
    doLast {
        val out = defaultWfRes.resolve("values/wf_token.xml")
        out.parentFile.mkdirs()
        out.writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="default_wf_token" translatable="false">${tokenFile.readText().trim()}</string>
            |</resources>
            |""".trimMargin()
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(defaultWfAssets.absolutePath)
        variant.sources.res?.addStaticSourceDirectory(defaultWfRes.absolutePath)
    }
}

tasks.named("preBuild") { dependsOn(copyDefaultWatchFace, defaultWatchFaceTokenRes) }
