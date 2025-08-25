pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
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

rootProject.name = "kDrive"
include(
    ":app",
    ":Core:AppIntegrity",
    ":Core:Auth",
    ":Core:Avatar",
    ":Core:Coil",
    ":Core:Compose:BasicButton",
    ":Core:Compose:Basics",
    ":Core:Compose:Margin",
    ":Core:Compose:MaterialThemeFromXml",
    ":Core:CrossAppLogin:Back",
    ":Core:CrossAppLogin:Front",
    ":Core:FragmentNavigation",
    ":Core:Ktor",
    ":Core:Legacy",
    ":Core:Legacy:AppLock",
    ":Core:Legacy:BugTracker",
    ":Core:Legacy:Stores",
    ":Core:Matomo",
    ":Core:kSuite",
    ":Core:kSuite:kSuitePro",
    ":Core:kSuite:MyKSuite",
    ":Core:Network",
    ":Core:Network:Models",
    ":Core:Onboarding",
    ":Core:RecyclerView",
    ":Core:Sentry",
    ":Core:Thumbnails",
)
