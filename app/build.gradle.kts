import java.util.Properties

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(core.plugins.android.application)
    alias(libs.plugins.junit5)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.realm.android)
    alias(core.plugins.ksp)
    alias(core.plugins.dagger.hilt)
    alias(core.plugins.compose.compiler)
    alias(core.plugins.kotlin.parcelize)
    alias(core.plugins.sentry.plugin)
}

val appCompileSdk: Int by rootProject.extra
val appTargetSdk: Int by rootProject.extra
val appMinSdk: Int by rootProject.extra
val javaVersion: JavaVersion by rootProject.extra

android {

    namespace = "com.infomaniak.drive"

    compileSdk = appCompileSdk

    ndkVersion = "28.0.13004108"

    defaultConfig {
        testInstrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
        applicationId = "com.infomaniak.drive"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = 5_012_003_01
        versionName = "5.12.3"

        setProperty("archivesBaseName", "kdrive-$versionName ($versionCode)")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLIENT_ID", "\"9473D73C-C20F-4971-9E10-D957C563FA68\"")

        buildConfigField("String", "BUGTRACKER_DRIVE_BUCKET_ID", "\"app_drive\"")
        buildConfigField("String", "BUGTRACKER_DRIVE_PROJECT_NAME", "\"drive\"")
        buildConfigField("String", "GITHUB_REPO", "\"android-kdrive\"")
        buildConfigField("String", "GITHUB_REPO_URL", "\"https://github.com/Infomaniak/android-kdrive\"")

        resValue("string", "CLOUD_STORAGE_AUTHORITY", "com.infomaniak.drive.documents")
        resValue("string", "FILE_AUTHORITY", "com.infomaniak.drive.files")

        resValue("string", "EXPOSED_UPLOAD_DIR", "upload_media")
        resValue("string", "EXPOSED_OFFLINE_DIR", "offline_storage")
        resValue("string", "EXPOSED_PUBLIC_SHARE_DIR", "public_share")

        androidResources.localeFilters += arrayOf("en", "de", "es", "fr", "it")
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions { jvmTarget = javaVersion.toString() }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            // TODO: Move the line below in the plugins block, or find an alternative, because this is not actually applied conditionally.
            apply(plugin = "com.google.gms.google-services")

            isDefault = true
        }
        create("fdroid")
        create("preprod") {
            buildConfigField("String", "DRIVE_API_V1", "\"https://kdrive.preprod.dev.infomaniak.ch/drive/\"")
            buildConfigField("String", "DRIVE_API_V2", "\"https://kdrive.preprod.dev.infomaniak.ch/2/drive/\"")
            buildConfigField("String", "DRIVE_API_V3", "\"https://kdrive.preprod.dev.infomaniak.ch/3/drive/\"")
            matchingFallbacks += "standard"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    packaging {
        // There is a conflict between 'pdfium' and 'dotlottie' libs, which both have 'libc++_shared.so'
        jniLibs.pickFirsts.add("**/libc++_shared.so")
    }

    lint {
        // Temporary fix waiting for the gradual update of some libs (androidx lifecycle, mqtt)
        disable += "NullSafeMutableLiveData"
    }

    testOptions.unitTests.all {
        it.testLogging { events("passed", "skipped", "failed", "standardOut", "standardError") }
    }
}

val isRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

val envProperties = rootProject.file("env.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().also { it.load(file.reader()) } }

val sentryAuthToken = envProperties?.getProperty("sentryAuthToken")
    .takeUnless { it.isNullOrBlank() }
    ?: if (isRelease) error("The `sentryAuthToken` property in `env.properties` must be specified (see `env.example.properties`).") else ""

configurations.configureEach {
    // The Matomo SDK logs network issues to Timber, and the Sentry plugin detects the Timber dependency,
    // and adds its integration, which generates noise.
    // Since we're not using Timber for anything else, it's safe to completely disabled it,
    // as specified in Sentry's documentation: https://docs.sentry.io/platforms/android/integrations/timber/#disable
    exclude(group = "io.sentry", module = "sentry-android-timber")
}

sentry {
    autoInstallation.sentryVersion.set(core.versions.sentry)
    org = "sentry"
    projectName = "kdrive-android"
    authToken = sentryAuthToken
    url = "https://sentry-mobile.infomaniak.com"
    includeDependenciesReport = false
    includeSourceContext = isRelease

    // Enables or disables the automatic upload of mapping files during a build.
    // If you disable this, you'll need to manually upload the mapping files with sentry-cli when you do a release.
    // Default is enabled.
    autoUploadProguardMapping = true

    // Disables or enables the automatic configuration of Native Symbols for Sentry.
    // This executes sentry-cli automatically so you don't need to do it manually.
    // Default is disabled.
    uploadNativeSymbols = isRelease

    // Does or doesn't include the source code of native code for Sentry.
    // This executes sentry-cli with the --include-sources param. automatically so you don't need to do it manually.
    // Default is disabled.
    includeNativeSources = isRelease
}

dependencies {

    implementation(core.infomaniak.core.auth)
    implementation(core.infomaniak.core.avatar)
    implementation(core.infomaniak.core.coil)
    implementation(core.infomaniak.core.common)
    implementation(core.infomaniak.core.crossapplogin)
    implementation(core.infomaniak.core.fragmentnavigation)
    implementation(core.infomaniak.core.inappreview)
    implementation(core.infomaniak.core.inappupdate)
    implementation(core.infomaniak.core.ksuite)
    implementation(core.infomaniak.core.ksuite.myksuite)
    implementation(core.infomaniak.core.ksuite.pro)
    implementation(core.infomaniak.core.ktor)
    implementation(core.infomaniak.core.matomo)
    implementation(core.infomaniak.core.network)
    implementation(core.infomaniak.core.notifications)
    implementation(core.infomaniak.core.recyclerview)
    implementation(core.infomaniak.core.sentry)
    implementation(core.infomaniak.core.thumbnails)
    implementation(core.infomaniak.core.twofactorauth.back)
    implementation(core.infomaniak.core.twofactorauth.front)
    implementation(core.infomaniak.core.ui)
    implementation(core.infomaniak.core.ui.compose.basics)
    implementation(core.infomaniak.core.ui.compose.margin)
    implementation(core.infomaniak.core.ui.compose.materialthemefromxml)
    implementation(core.infomaniak.core.ui.view.edgetoedge)

    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:BugTracker"))

    // Compose
    implementation(platform(core.compose.bom))
    implementation(core.activity.compose)
    implementation(core.compose.foundation)
    implementation(core.compose.material3)
    implementation(core.compose.ui.tooling.preview)

    implementation(core.ktor.client.okhttp)
    implementation(core.ktor.client.core)
    implementation(core.ktor.client.json)
    implementation(core.ktor.client.content.negociation)

    implementation(core.androidx.work.runtime)
    androidTestImplementation(core.androidx.work.testing)

    implementation(core.androidx.datastore.preferences)

    implementation(core.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)

    implementation(core.splitties.mainthread)

    implementation(core.hilt.android)
    implementation(core.hilt.work)
    ksp(core.hilt.compiler)
    ksp(core.hilt.androidx.compiler)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    implementation(libs.extension.okhttp)

    implementation(libs.android.pdfview)
    implementation(libs.gravity.snap.helper)
    implementation(core.lottie)
    implementation(libs.material.date.time.picker)
    implementation(libs.touch.image.view)

    implementation(libs.realm.android.adapters)

    "standardImplementation"("com.infomaniak.core:Notifications.Registration")
    "standardImplementation"(libs.firebase.messaging.ktx)
    "standardImplementation"(libs.gs.sdk)

    implementation(libs.dd.plist)

    implementation(libs.paho.mqtt.android)

    testImplementation(libs.kotlin.faker)
    testImplementation(libs.mock.web.server)
    androidTestImplementation(libs.androidx.ui.automator)

    androidTestImplementation(libs.androidx.espresso.contrib) {
        // WorkAround for dependencies issue (see https://github.com/android/android-test/issues/861)
        exclude(group = libs.androidx.espresso.checker.get().group, module = libs.androidx.espresso.checker.get().name)
    }
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(core.androidx.core.ktx)
    androidTestImplementation(core.androidx.rules)
    androidTestImplementation(core.androidx.runner)

    androidTestImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    androidTestImplementation(libs.android.test.core)
    androidTestRuntimeOnly(libs.android.test.runner)

    implementation(core.coil.gif)

    // Compose
    implementation(libs.androidx.ui.android)
}
