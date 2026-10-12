pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // کتابخانه‌ی پرداخت کافه‌بازار (Poolakey) فقط در JitPack منتشر می‌شود؛ از JitPack چیز دیگری گرفته نشود
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.cafebazaar(\\..*)?") }
        }
    }
}

rootProject.name = "Jibito"
include(":app")
