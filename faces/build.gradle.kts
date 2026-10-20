// Отрисовка циферблатов на Canvas: превью в приложении телефона и классический циферблат
// на часах с Wear OS 3–5 (там нет Watch Face Push) — одни и те же стили и шрифты.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.synctheme.faces"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.core.ktx)
}
