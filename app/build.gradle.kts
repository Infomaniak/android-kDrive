plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("realm-android")
    id("de.mannodermaus.android-junit5")
}

android {

    namespace = "com.infomaniak.drive"

    compileSdk = 35

    ndkVersion = "28.0.13004108"

    defaultConfig {
        testInstrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
        applicationId = "com.infomaniak.drive"
        minSdk = 24
        targetSdk = 35
        versionCode = 5_05_000_01
        versionName = "5.5.0"

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

        resValue("string", "CLOUD_STORAGE_AUTHORITY", "com.infomaniak.drive.documents")
        resValue("string", "FILE_AUTHORITY", "com.infomaniak.drive.files")

        resValue("string", "EXPOSED_UPLOAD_DIR", "upload_media")
        resValue("string", "EXPOSED_OFFLINE_DIR", "offline_storage")
        resValue("string", "EXPOSED_PUBLIC_SHARE_DIR", "public_share")

        androidResources.localeFilters += arrayOf("en", "de", "es", "fr", "it")
    }

    val javaVersion = JavaVersion.VERSION_17

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

    gradle.buildFinished {
        try {
            exec {
                commandLine("say", "Ok")
            }
        } catch (_: Throwable) {
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions.unitTests.all {
        it.testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
        }
    }
}

sentry {
    // Enables or disables the automatic upload of mapping files
    // during a build. If you disable this, you'll need to manually
    // upload the mapping files with sentry-cli when you do a release.
    // Default is enabled.
    autoUpload = true

    // Disables or enables the automatic configuration of Native Symbols
    // for Sentry. This executes sentry-cli automatically so
    // you don't need to do it manually.
    // Default is disabled.
    uploadNativeSymbols = true

    // Does or doesn't include the source code of native code for Sentry.
    // This executes sentry-cli with the --include-sources param. automatically so
    // you don't need to do it manually.
    // Default is disabled.
    includeNativeSources = true
}

dependencies {

    implementation(project(":Core"))
    implementation(project(":Core:FragmentNavigation"))
    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:Stores"))
    implementation(project(":Core:MyKSuite"))
    implementation(project(":Core:Network"))
    implementation(project(":Core:Thumbnails"))
    implementation(project(":Core:RecyclerView"))

    val work_version = "2.9.1" // Keep the same version as the one in Core
    implementation("androidx.work:work-runtime-ktx:$work_version")
    androidTestImplementation("androidx.work:work-testing:$work_version")

    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7") // Waiting for Api 35
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.webkit:webkit:1.13.0")

    val splitties_version = "3.0.0"
    implementation("com.louiscad.splitties:splitties-mainthread:$splitties_version")

    val exoplayer_version = "2.19.1"
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayer_version")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayer_version")
    implementation("com.google.android.exoplayer:exoplayer:$exoplayer_version")
    implementation("com.google.android.exoplayer:extension-okhttp:$exoplayer_version")

    implementation("com.airbnb.android:lottie:6.6.6")
    implementation("com.github.Infomaniak:android-pdfview:3.2.11")
    implementation("com.github.MikeOrtiz:TouchImageView:3.7.1")
    implementation("com.github.rubensousa:gravitysnaphelper:2.2.2")
    implementation("com.tbuonomo:dotsindicator:5.1.0")
    implementation("com.wdullaer:materialdatetimepicker:4.2.3")

    implementation("com.github.realm:realm-android-adapters:v4.0.0")

    "standardImplementation"("com.google.firebase:firebase-messaging-ktx:24.1.1")
    "standardImplementation"("com.geniusscansdk:gssdk:5.6.1")

    implementation("com.googlecode.plist:dd-plist:1.28")

    implementation("com.github.hannesa2:paho.mqtt.android:4.2.4") // Doesn't build when bumped to 4.3 (Waiting SDK 35)

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.github.serpro69:kotlin-faker:1.16.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    val espresso_version = "3.6.1"
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espresso_version") {
        // WorkAround for dependencies issue (see https://github.com/android/android-test/issues/861)
        exclude(group = "org.checkerframework", module = "checker")
    }
    androidTestImplementation("androidx.test.espresso:espresso-core:$espresso_version")

    val androidx_test_version = "1.6.1"
    implementation("androidx.test:core-ktx:$androidx_test_version")
    androidTestImplementation("androidx.test:rules:$androidx_test_version")
    androidTestImplementation("androidx.test:runner:1.6.2")

    val jupiter_version = "5.12.2"
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:$jupiter_version")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiter_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiter_version")

    val junit_version = "1.7.0"
    androidTestImplementation("de.mannodermaus.junit5:android-test-core:$junit_version")
    androidTestRuntimeOnly("de.mannodermaus.junit5:android-test-runner:$junit_version")

    implementation("io.coil-kt:coil-gif:2.7.0")

    // Compose
    implementation("androidx.compose.ui:ui-android:1.7.8") // Doesn't build when bumped to 1.8.0 (Waiting SDK 35)
}
