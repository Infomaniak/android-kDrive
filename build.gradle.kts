// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {

    extra.apply {
        set("appCompileSdk", 35) // Ensure any extra configChanges are added into Activities' manifests.
        set("appTargetSdk", 35)
        set("appMinSdk", 27)
        set("legacyMinSdk", 27) // Duplicated from `Core/Legacy/build.gradle` : `legacyMinSdk = 27`
        set("javaVersion", JavaVersion.VERSION_17)
    }

    dependencies {
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.0")
        classpath("io.realm:realm-gradle-plugin:10.15.1")
        classpath("com.android.tools.build:gradle:8.8.2")
        classpath("com.google.gms:google-services:4.4.2")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.9.3.0")
    }
}

plugins {
    alias(libs.plugins.sentry) version "5.5.0" apply false
    alias(core.plugins.ksp) version "2.1.21-2.0.1" apply false
    val kotlinVersion = "2.1.21"
    alias(libs.plugins.kotlin.serialization) version kotlinVersion apply false
    alias(core.plugins.compose.compiler) version kotlinVersion apply false
    alias(core.plugins.kotlin.android) version kotlinVersion apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
