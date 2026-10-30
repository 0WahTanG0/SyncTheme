// Приложение для телефона: редактор тем, обои, виджеты и отправка циферблата на часы.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.synctheme"
    compileSdk = 36

    defaultConfig {
        // Тот же applicationId и ключ подписи, что у приложения на часах.
        applicationId = "app.synctheme"
        // Android 9+: работает на любом телефоне и планшете; новые API — через util/Compat.kt.
        minSdk = 28
        targetSdk = 36
        versionCode = 37
        versionName = "1.21.4"
        // Откуда приложение берёт обновления: релизы GitHub owner/repo (-PupdateRepo=... при сборке).
        resValue("string", "update_repo", providers.gradleProperty("updateRepo").getOrElse(""))
        // Galaxy S25+ и все современные телефоны — arm64; так APK заметно меньше.
        ndk { abiFilters += "arm64-v8a" }
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
            // R8 только выбрасывает неиспользуемый код Compose, AndroidX, Kotlin и самого приложения;
            // библиотеки с рефлексией (валидатор циферблатов, ADB, криптография) остаются целиком —
            // см. proguard-rules.pro.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("app")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("previewsDir", layout.buildDirectory.dir("previews").get().asFile.absolutePath)
                it.maxHeapSize = "3g"
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
                // Необязательно: своё зеркало Maven для загрузки android-all (-ProbolectricRepo=...).
                providers.gradleProperty("robolectricRepo").orNull?.let { url ->
                    it.systemProperty("robolectric.dependency.repo.url", url)
                }
                it.testLogging { events("passed", "failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
            }
        }
    }
    packaging {
        // Код и библиотеки в APK — сжатыми: файл обновления почти вдвое меньше. Телефон распакует их при установке.
        dex { useLegacyPackaging = true }
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.kotlin_module", "META-INF/versions/**", "META-INF/INDEX.LIST",
                // Таблицы постквантовой подписи Picnic из BouncyCastle: ADB по Wi‑Fi их не использует.
                "org/bouncycastle/pqc/crypto/picnic/**",
                // Данные ICU: валидатор циферблатов тянет ICU через XPath 2, но для наших циферблатов
                // до него не доходит (RenderingTest проверяет все стили — классы ICU не загружаются).
                "com/ibm/icu/impl/data/**",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":faces"))
    // Эффект глубины: главный объект фото (модель приходит через сервисы Google Play).
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.wff.validator.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.palette)
    implementation(libs.work.runtime)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.wear.remote.interactions)

    // Установка приложения на часы по «Отладке по Wi‑Fi» (протокол ADB с сопряжением по коду).
    implementation(libs.libadb)
    implementation(libs.conscrypt)
    implementation(libs.bouncycastle.pkix)

    // Проверки на JVM (Robolectric): рендер превью, виджетов и сборка циферблата с Android-валидатором.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// --- В assets: шаблон циферблата с ключом для переподписи и приложение для часов ---

val watchfaceAssets = layout.buildDirectory.dir("generated/watchfaceTemplate/assets").get().asFile
val copyWatchFaceTemplate = tasks.register<Copy>("copyWatchFaceTemplate") {
    dependsOn(":watchface:assembleRelease")
    from(project(":watchface").layout.buildDirectory.file("outputs/apk/release/watchface-release.apk")) {
        rename { "watchface_template.apk" }
    }
    from(rootProject.file("keystore/watchface_key.pk8"))
    from(rootProject.file("keystore/watchface_cert.der"))
    into(watchfaceAssets)
}

val copyWatchApp = tasks.register<Copy>("copyWatchApp") {
    dependsOn(":wear:assembleRelease")
    from(project(":wear").layout.buildDirectory.file("outputs/apk/release/wear-release.apk")) {
        rename { "watch_app.apk" }
    }
    into(watchfaceAssets)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(watchfaceAssets.absolutePath)
    }
}

tasks.named("preBuild") { dependsOn(copyWatchFaceTemplate, copyWatchApp) }

// В JVM-тестах (Robolectric) нативной библиотеки conscrypt-android нет — используется встроенный в Robolectric Conscrypt.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.conscrypt", module = "conscrypt-android")
}
