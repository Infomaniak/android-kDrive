pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    includeBuild("Core/build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // mavenLocal() // Only used when we want to use a local version of a library (./gradlew publishToMavenLocal)
        maven(url = "https://jitpack.io")
        maven(url = "https://s3.amazonaws.com/tgl.maven")
    }
    versionCatalogs {
        create("core") { from(files("Core/gradle/core.versions.toml")) }
    }
}

plugins {
    id("com.infomaniak.core.composite")
}

rootProject.name = "kDrive"
include(
    ":app",
    ":Core:Legacy",
    ":Core:Legacy:AppLock",
)
