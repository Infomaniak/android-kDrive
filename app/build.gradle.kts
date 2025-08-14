import java.util.Properties

plugins {
    alias(core.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.junit5)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.realm.android)
    alias(libs.plugins.sentry)
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
        versionCode = 5_08_005_01
        versionName = "5.8.5"

        setProperty("archivesBaseName", "kdrive-$versionName ($versionCode)")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLIENT_ID", "\"9473D73C-C20F-4971-9E10-D957C563FA68\"")
        buildConfigField("String", "CREATE_ACCOUNT_URL", "\"https://welcome.infomaniak.com/signup/ikdrive?app=true\"")
        buildConfigField("String", "CREATE_ACCOUNT_SUCCESS_HOST", "\"kdrive.infomaniak.com\"")
        buildConfigField("String", "CREATE_ACCOUNT_CANCEL_HOST", "\"welcome.infomaniak.com\"")
        buildConfigField("String", "DRIVE_API_V1", "\"https://api.kdrive.infomaniak.com/drive/\"")
        buildConfigField("String", "DRIVE_API_V2", "\"https://api.kdrive.infomaniak.com/2/drive/\"")
        buildConfigField("String", "DRIVE_API_V3", "\"https://api.kdrive.infomaniak.com/3/drive/\"")
        buildConfigField("String", "MANAGER_URL", "\"https://manager.infomaniak.com/v3/\"")
        buildConfigField("String", "OFFICE_URL", "\"https://kdrive.infomaniak.com/app/office/\"")
        buildConfigField("String", "SHARE_URL_V1", "\"https://kdrive.infomaniak.com/app/\"")
        buildConfigField("String", "SHARE_URL_V2", "\"https://kdrive.infomaniak.com/2/app/\"")
        buildConfigField("String", "SHARE_URL_V3", "\"https://kdrive.infomaniak.com/3/app/\"")
        buildConfigField("String", "SHOP_URL", "\"https://shop.infomaniak.com/order/\"")
        buildConfigField("String", "SUPPORT_URL", "\"https://support.infomaniak.com/\"")

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
            buildConfigField("String", "CREATE_ACCOUNT_URL", "\"https://welcome.preprod.dev.infomaniak.ch/signup/ikdrive?app=true\"")
            buildConfigField("String", "CREATE_ACCOUNT_SUCCESS_HOST", "\"kdrive.preprod.dev.infomaniak.ch\"")
            buildConfigField("String", "CREATE_ACCOUNT_CANCEL_HOST", "\"welcome.preprod.dev.infomaniak.ch\"")
            buildConfigField("String", "DRIVE_API_V1", "\"https://0-epic272.drive.kdrive.preprod.dev.infomaniak.ch/drive/\"")
            buildConfigField("String", "DRIVE_API_V2", "\"https://0-epic272.drive.kdrive.preprod.dev.infomaniak.ch/2/drive/\"")
            buildConfigField("String", "DRIVE_API_V3", "\"https://0-epic272.drive.kdrive.preprod.dev.infomaniak.ch/3/drive/\"")
            buildConfigField("String", "MANAGER_URL", "\"https://manager.preprod.dev.infomaniak.ch/v3/\"")
            buildConfigField("String", "OFFICE_URL", "\"https://kdrive.preprod.dev.infomaniak.ch/app/office/\"")
            buildConfigField("String", "SHARE_URL_V1", "\"https://kdrive.preprod.dev.infomaniak.ch/app/\"")
            buildConfigField("String", "SHARE_URL_V2", "\"https://kdrive.preprod.dev.infomaniak.ch/2/app/\"")
            buildConfigField("String", "SHARE_URL_V3", "\"https://kdrive.preprod.dev.infomaniak.ch/3/app/\"")
            buildConfigField("String", "SHOP_URL", "\"https://shop.preprod.dev.infomaniak.ch/order/\"")
            buildConfigField("String", "SUPPORT_URL", "\"https://support.preprod.dev.infomaniak.ch/\"")
            matchingFallbacks += "standard"
        }
    }

    // As of Gradle 8, there is no sane replacement for gradle.buildFinished. See this closed issue: https://github.com/gradle/gradle/issues/20151
    // This block is not essential, so we can remove it if needed, or replace it with a trusted Gradle plugin providing the same functionality.
    @Suppress("Deprecation")
    gradle.buildFinished {
        try {
            exec { commandLine("say", "Ok") }
        } catch (_: Throwable) {
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
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

val envProperties = rootProject.file("env.properties").takeIf { it.exists() }?.let { file ->
    Properties().also { it.load(file.reader()) }
}

val sentryAuthToken = envProperties?.getProperty("sentryAuthToken").takeUnless { it.isNullOrBlank() }
    ?: if (isRelease) error("The `sentryAuthToken` property in `env.properties` must be specified (see `env.example.properties`).") else ""

sentry {
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

    implementation(project(":Core"))
    implementation(project(":Core:Auth"))
    implementation(project(":Core:Avatar"))
    implementation(project(":Core:Compose:Basics"))
    implementation(project(":Core:Compose:MaterialThemeFromXml"))
    implementation(project(":Core:CrossAppLogin:Back"))
    implementation(project(":Core:CrossAppLogin:Front"))
    implementation(project(":Core:FragmentNavigation"))
    implementation(project(":Core:Ktor"))
    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:BugTracker"))
    implementation(project(":Core:Legacy:Stores"))
    implementation(project(":Core:Matomo"))
    implementation(project(":Core:kSuite"))
    implementation(project(":Core:kSuite:kSuitePro"))
    implementation(project(":Core:kSuite:MyKSuite"))
    implementation(project(":Core:Network"))
    implementation(project(":Core:RecyclerView"))
    implementation(project(":Core:Sentry"))
    implementation(project(":Core:Thumbnails"))

    implementation(platform(core.compose.bom))
    implementation(core.activity.compose)
    implementation(core.compose.foundation)
    implementation(core.compose.material3)
    implementation(core.compose.ui.tooling.preview)

    implementation(core.ktor.client.okhttp)

    implementation(core.androidx.work.runtime)
    androidTestImplementation(core.androidx.work.testing)

    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)

    implementation(libs.splitties.main.thread)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    implementation(libs.extension.okhttp)

    implementation(libs.android.pdfview)
    implementation(libs.gravity.snap.helper)
    implementation(libs.lottie)
    implementation(libs.material.date.time.picker)
    implementation(libs.touch.image.view)

    implementation(libs.realm.android.adapters)

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

    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)

    androidTestImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    androidTestImplementation(libs.android.test.core)
    androidTestRuntimeOnly(libs.android.test.runner)

    implementation(libs.coil.gif)

    // Compose
    implementation(libs.androidx.ui.android)
}
