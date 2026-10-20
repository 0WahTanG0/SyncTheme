// Шаблон циферблата в формате Watch Face Format (без кода).
// Его APK кладётся в телефон (как шаблон для генерации) и в часы (как циферблат по умолчанию).
plugins {
    alias(libs.plugins.android.application)
}

android {
    // Watch Face Push требует имя <пакет приложения>.watchfacepush.<имя>.
    namespace = "app.synctheme.watchfacepush.theme"
    compileSdk = 36
    enableKotlin = false

    defaultConfig {
        applicationId = "app.synctheme.watchfacepush.theme"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Отдельный ключ: Watch Face Push требует, чтобы циферблаты были подписаны не ключом приложения.
        create("watchface") {
            storeFile = rootProject.file("keystore/watchface.p12")
            storePassword = "watchface"
            keyAlias = "watchface"
            keyPassword = "watchface"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug {
            // Убирает classes.dex — в WFF-циферблате не должно быть кода.
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("watchface")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("watchface")
        }
    }
}

val buildtools: Configuration by configurations.creating
dependencies {
    buildtools(project(":buildtools"))
}

val generatedRes = layout.buildDirectory.dir("generated/wfDefaults/res").get().asFile
val generateDefaults = tasks.register<JavaExec>("generateWatchFaceDefaults") {
    description = "Генерирует watchface.xml, фон, превью и стрелки для темы по умолчанию."
    classpath = buildtools
    mainClass.set("app.synctheme.buildtools.DefaultAssetsKt")
    args(generatedRes.absolutePath)
    outputs.dir(generatedRes)
    inputs.files(buildtools)
    jvmArgs("-Djava.awt.headless=true")
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory(generatedRes.absolutePath)
    }
}

tasks.named("preBuild") { dependsOn(generateDefaults) }
