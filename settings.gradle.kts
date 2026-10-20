pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: зависимость валидатора Watch Face Push (AXML) и libadb-android (установка на часы по Wi‑Fi).
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.xgouchet")
                includeGroupByRegex("com\\.github\\.MuntashirAkon.*")
            }
        }
    }
}

rootProject.name = "SyncTheme"

include(":core")
include(":buildtools")
include(":watchface")
include(":faces")
include(":wear")
include(":mobile")
