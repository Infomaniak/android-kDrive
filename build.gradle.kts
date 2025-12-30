// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {

    extra.apply {
        set("appCompileSdk", 36) // Ensure any extra configChanges are added into Activities' manifests.
        set("appTargetSdk", 35)
        set("appMinSdk", 27)
        set("legacyMinSdk", 27) // Duplicated from `Core/Legacy/build.gradle` : `legacyMinSdk = 27`
        set("javaVersion", JavaVersion.VERSION_17)
    }

    dependencies {
        classpath(libs.realm.gradle.plugin)
        classpath(libs.gradle)
        classpath(libs.google.services)
        classpath(libs.android.junit5)
    }
}

plugins {
    alias(core.plugins.compose.compiler) apply false
    alias(core.plugins.kotlin.android) apply false
    alias(core.plugins.kotlin.serialization) apply false
    alias(core.plugins.dagger.hilt) apply false
    alias(core.plugins.ksp) apply false
    alias(core.plugins.navigation.safeargs) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
