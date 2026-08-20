import org.gradle.kotlin.dsl.maven

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
        maven {
            name = "infomaniakReposiliteRepository"
            url = uri("https://maven.infomaniak.app/releases")
            content { includeGroup("com.infomaniak.pdfview") }
            content { includeGroup("com.infomaniak.pdfiumandroid") }
        }
        maven {
            name = "infomaniakReposiliteRepositorySnapshots"
            url = uri("https://maven.infomaniak.app/snapshots")
            content { includeGroup("com.infomaniak.pdfview") }
            content { includeGroup("com.infomaniak.pdfiumandroid") }
        }
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
)
