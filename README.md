# Infomaniak kDrive app

## A modern Android application for [kDrive by Infomaniak](https://www.infomaniak.com/kdrive).

### Synchronise, share, collaborate. The Swiss cloud that’s 100% secure.

#### :cloud: All the space you need

Always have access to all your photos, videos and documents. kDrive can store up to 106 TB of data.

#### :globe_with_meridians: A collaborative ecosystem. Everything included.

Collaborate online on Office documents, organise meetings, share your work. Anything is possible!

#### :lock:  kDrive respects your privacy

Protect your data in a sovereign cloud exclusively developed and hosted in Switzerland. Infomaniak doesn’t analyze or resell your
data.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
alt="Download from Google Play" height="100">](https://play.google.com/store/apps/details?id=com.infomaniak.drive)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="100">](https://f-droid.org/packages/com.infomaniak.drive/)

## License & Contributions

This project is under GPLv3 license.
If you see a bug or an enhanceable point, feel free to create an issue, so that we can discuss about it, and once approved, we or
you (depending on the priority of the bug/improvement) will take care of the issue and apply a merge request.
Please, don't do a merge request before creating an issue.

## Architecture

### Overview

The kDrive Android application follows a **modular architecture** designed for maintainability, testability, and scalability. The
project is organized into multiple Gradle modules with clear separation of concerns.

```
android-kDrive/
├── app/                          # Main application module
│   └── src/main/java/com/infomaniak/drive/
│       ├── data/                 # Data layer (API, cache, models)
│       │   ├── api/              # API clients and endpoints (Ktor)
│       │   ├── cache/            # Realm database configuration
│       │   ├── models/           # Data models (File, Drive, User, etc.)
│       │   ├── sync/             # Background synchronization
│       │   └── services/         # Background services
│       ├── ui/                   # Presentation layer (MVVM)
│       │   ├── fileList/         # File browsing and management
│       │   ├── menu/             # Navigation drawer menus
│       │   ├── login/            # Authentication flows
│       │   ├── home/             # Home screen
│       │   └── ...
│       ├── di/                   # Dependency injection (Hilt)
│       ├── utils/                # Utility classes
│       └── extensions/           # Kotlin extensions
│
└── Core/                         # Infomaniak Core libraries (composite build)
    ├── Legacy/                   # Legacy core module
    ├── Legacy/AppLock/           # App lock functionality
    └── Legacy/BugTracker/        # Bug tracking integration
```

### Architecture Pattern

The app follows the **MVVM (Model-View-ViewModel)** architecture pattern:

- **Model**: Data classes and repositories handling data operations
- **View**: Activities, Fragments, and Composables observing ViewModel state
- **ViewModel**: Business logic and state management using LiveData/StateFlow

### Data Flow

```
UI Layer (Activities/Fragments/Composables)
    ↕
ViewModel Layer (StateFlow/LiveData)
    ↕
Repository Layer (Data operations)
    ↕
Data Layer (API/Local Storage)
    ├── API (OkHTTP client)
    ├── Realm (Offline data)
    └── Room (Auth tokens)
```

## Tech Stack

### Languages

- **Kotlin** - Primary language (100%)
- **XML** - Legacy UI layouts (migrating to Compose)

### UI Framework

- **XML Layouts** - Legacy views
- **Jetpack Compose** - Modern declarative UI for new components
- **Material Design 3** - UI components and theming

### Architecture Components

- **Hilt** - Dependency injection
- **Navigation Component** - In-app navigation with Safe Args
- **ViewModel** - UI-related data management
- **WorkManager** - Background work scheduling

### Networking & Data

- **Ktor Client** - HTTP client for API communication
- **Realm** - NoSQL database for offline file data
- **Room** - SQL database for authentication tokens
- **SharedPreference** - Key-value storage for preferences

### Build System

- **Gradle Kotlin DSL** - Build configuration
- **Version Catalogs** - Centralized dependency management
- **Composite Builds** - Core libraries as included builds

## Build Flavors

The project supports multiple build flavors for different distribution channels:

| Flavor       | Description               | Features                                       |
|--------------|---------------------------|------------------------------------------------|
| **standard** | Google Play Store version | Firebase Cloud Messaging, Google Play Services |
| **fdroid**   | F-Droid version           | No proprietary dependencies, fully open source |
| **preprod**  | Pre-production testing    | Points to staging API servers                  |

## Compatibility

- **Minimum SDK**: Android 8.1 (API 27 - Oreo)
- **Target SDK**: Latest stable Android version
- **Recommended**: Android 10+ (API 29+) for best experience

## Cache & Storage

We use [Realm](https://realm.io/) on both platforms (iOS and Android) to store offline data including files, shares, app and user
preferences (in separate database instances).

[Android Room](https://developer.android.com/training/data-storage/room) is used to store API access tokens and basic user data
securely.

The sync mechanism ensures data consistency between the local cache and the server using background workers.

### Permissions

| Permission                             | API Level | Usage                                               |
|----------------------------------------|-----------|-----------------------------------------------------|
| `INTERNET`                             | All       | Network access for API calls and file operations    |
| `READ_EXTERNAL_STORAGE`                | ≤32       | Access files on device for upload (legacy)          |
| `WRITE_EXTERNAL_STORAGE`               | ≤32       | Download files to device (legacy)                   |
| `ACCESS_MEDIA_LOCATION`                | 29+       | Access location metadata in media files             |
| `READ_MEDIA_IMAGES`                    | 33+       | Access photos for upload (Android 13+)              |
| `READ_MEDIA_VIDEO`                     | 33+       | Access videos for upload (Android 13+)              |
| `READ_MEDIA_VISUAL_USER_SELECTED`      | 34+       | Access user-selected photos/videos (Android 14+)    |
| `FOREGROUND_SERVICE`                   | All       | Background file sync and download                   |
| `FOREGROUND_SERVICE_DATA_SYNC`         | 34+       | Foreground service for data synchronization         |
| `RECEIVE_BOOT_COMPLETED`               | All       | Restart sync service after device reboot            |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | All       | Allow background file downloads                     |
| `REQUEST_INSTALL_PACKAGES`             | All       | Install APK files from kDrive                       |
| `CAMERA`                               | All       | Document scanning (optional, ChromeBook compatible) |
| `WAKE_LOCK`                            | All       | Keep screen on during video playback                |

**Note:** Storage permissions have been updated for Android 13+ (API 33+) with granular media permissions (`READ_MEDIA_*`)
replacing the broad storage access.

## Tests

In order to test the app with Unit and UI tests, you have to copy `Env-Example` class in AndroidTest package and name it `Env`.\
⚠️ Don't forget to disable 2FA on your Infomaniak account if you want to execute tests, this feature is not supported for AddUser
test.\
Replace values contained in file by yours and launch the tests 👍
