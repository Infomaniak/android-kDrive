import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("realm-android")
    id("io.sentry.android.gradle")
    id("de.mannodermaus.android-junit5")
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
        versionCode = 5_06_003_01
        versionName = "5.6.3"

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
    implementation(project(":Core:FragmentNavigation"))
    implementation(project(":Core:Ktor"))
    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:BugTracker"))
    implementation(project(":Core:Legacy:Stores"))
    implementation(project(":Core:MyKSuite"))
    implementation(project(":Core:Network"))
    implementation(project(":Core:Thumbnails"))
    implementation(project(":Core:RecyclerView"))

    implementation(core.ktor.client.okhttp)

    val workVersion = "2.9.1" // Keep the same version as the one in Core
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    androidTestImplementation("androidx.work:work-testing:$workVersion")

    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.webkit:webkit:1.14.0")

    val splittiesVersion = "3.0.0"
    implementation("com.louiscad.splitties:splitties-mainthread:$splittiesVersion")

    val exoplayerVersion = "2.19.1"
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer:$exoplayerVersion")
    implementation("com.google.android.exoplayer:extension-okhttp:$exoplayerVersion")

    implementation("com.airbnb.android:lottie:6.6.7")
    implementation("com.github.Infomaniak:android-pdfview:3.2.11")
    implementation("com.github.MikeOrtiz:TouchImageView:3.7.1")
    implementation("com.github.rubensousa:gravitysnaphelper:2.2.2")
    implementation("com.tbuonomo:dotsindicator:5.1.0")
    implementation("com.wdullaer:materialdatetimepicker:4.2.3")

    implementation("com.github.realm:realm-android-adapters:v4.0.0")

    "standardImplementation"("com.google.firebase:firebase-messaging-ktx:24.1.1")
    "standardImplementation"("com.geniusscansdk:gssdk:5.11.0")

    implementation("com.googlecode.plist:dd-plist:1.28")

    implementation("com.github.hannesa2:paho.mqtt.android:4.2.4") // Doesn't build when bumped to 4.3 (Waiting SDK 35)

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.github.serpro69:kotlin-faker:1.16.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    val espressoVersion = "3.6.1"
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espressoVersion") {
        // WorkAround for dependencies issue (see https://github.com/android/android-test/issues/861)
        exclude(group = "org.checkerframework", module = "checker")
    }
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")

    val androidxTestVersion = "1.6.1"
    implementation("androidx.test:core-ktx:$androidxTestVersion")
    androidTestImplementation("androidx.test:rules:$androidxTestVersion")
    androidTestImplementation("androidx.test:runner:1.6.2")

    val jupiterVersion = "5.13.1"
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")

    val junitVersion = "1.8.0"
    androidTestImplementation("de.mannodermaus.junit5:android-test-core:$junitVersion")
    androidTestRuntimeOnly("de.mannodermaus.junit5:android-test-runner:$junitVersion")

    implementation("io.coil-kt:coil-gif:2.7.0")

    // Compose
    implementation("androidx.compose.ui:ui-android:1.8.2")
}
