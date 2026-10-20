// Приложение для телефона: редактор тем, обои, виджеты и отправка циферблата на часы.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.synctheme"
    compileSdk = 36

    defaultConfig {
        // Тот же applicationId и ключ подписи, что у приложения на часах.
        applicationId = "app.synctheme"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
            // Валидатор циферблатов использует рефлексию (xerces и др.), поэтому без R8.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
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
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.kotlin_module", "META-INF/versions/**", "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
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

    // Проверки на JVM (Robolectric): рендер превью, виджетов и сборка циферблата с Android-валидатором.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// --- Шаблон циферблата и ключ для его переподписи кладём в assets ---

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

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(watchfaceAssets.absolutePath)
    }
}

tasks.named("preBuild") { dependsOn(copyWatchFaceTemplate) }
