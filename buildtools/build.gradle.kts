// Инструменты сборки (только JVM, в приложения не попадают):
// генерация ресурсов шаблона циферблата, валидация APK и тесты валидатором Watch Face Push.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.wff.validator.jvm)
    testImplementation(libs.junit)
}

tasks.test {
    dependsOn(":watchface:assembleRelease")
    val templateApk = project(":watchface").layout.buildDirectory
        .file("outputs/apk/release/watchface-release.apk").get().asFile
    systemProperty("templateApk", templateApk.absolutePath)
    systemProperty("keystoreDir", rootProject.file("keystore").absolutePath)
    systemProperty("outDir", layout.buildDirectory.dir("test-apks").get().asFile.absolutePath)
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
