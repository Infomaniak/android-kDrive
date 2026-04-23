# kDrive Main App

## Package Identity

Primary kDrive Android application module. Handles cloud storage synchronization, file management, sharing, and collaboration
features. Uses MVVM architecture with Hilt DI, Realm offline cache, and Ktor for API calls.

## Setup & Run

```bash
# Build debug APK
./gradlew app:assembleStandardDebug

# Install on device
./gradlew app:installStandardDebug

# Run unit tests
./gradlew app:test

# Build F-Droid flavor
./gradlew app:assembleFdroidDebug

# Build pre-production
./gradlew app:assemblePreprodDebug
```

## Architecture Patterns

### MVVM Structure

```
app/src/main/java/com/infomaniak/drive/
├── data/           # Repositories, API, cache, models
├── ui/             # Activities, Fragments, ViewModels  
├── di/             # Hilt modules (ApplicationModule, ActivityModule)
├── extensions/     # Kotlin extensions
└── utils/          # Utility classes
```

### Data Layer

- **api/**: Ktor API clients and repository implementations
- **cache/**: Realm database configuration and DAOs
- **models/**: Data classes (File, Drive, User, etc.)
- **sync/**: Background synchronization logic
- **services/**: Foreground services for uploads/downloads

### Key Dependencies Management

- Hilt modules: `di/ApplicationModule.kt`, `di/ActivityModule.kt`
- Token provider: `TokenInterceptorListenerProvider.kt`
- App initialization: `MainApplication.kt`

## Patterns & Conventions

### ViewModel Creation

- **DO**: Inject dependencies via constructor
  ```kotlin
  @HiltViewModel
  class FileListViewModel @Inject constructor(
      private val fileRepository: FileRepository,
      savedStateHandle: SavedStateHandle
  ) : ViewModel()
  ```
- **DON'T**: Use AndroidViewModel unless absolutely necessary (e.g., need Application context)

### Fragment Pattern

- **DO**: Use Navigation Component with Safe Args
    - Example: `ui/fileList/FileListFragment.kt`
- **DON'T**: Use FragmentTransaction directly

### Background Work

- **DO**: Use WorkManager for sync operations
    - Location: `data/sync/` for workers
- **DON'T**: Use raw Threads or AsyncTask

### API Calls

- **DO**: Use repository pattern with Ktor
    - Example: `data/api/ApiRepository.kt`
    - Wrapper: `data/api/ApiService.kt`

### Realm Models

- **DO**: Use `@RealmClass` annotation, open classes
    - Example: `data/models/File.kt`
- **DON'T**: Use Room for file data (Realm only)

### Activity Injection

- **DO**: Annotate with `@AndroidEntryPoint`
    - Example: `ui/MainActivity.kt`
- **DON'T**: Manually instantiate ViewModels

### Image Loading

- **DO**: Use Coil (configured via Core)
- **DON'T**: Use Glide or other loaders

## Key Files Reference

### Entry Points

- Application: `MainApplication.kt` (Hilt entry)
- Main Activity: `ui/MainActivity.kt`
- File List: `ui/fileList/FileListFragment.kt`

### ViewModels (35+ files)

- Main: `ui/MainViewModel.kt`
- File List: `ui/fileList/FileListViewModel.kt`
- Search: `ui/fileList/SearchViewModel.kt`
- Settings: `ui/menu/settings/SettingsViewModel.kt`

### Data Repositories

- API service: `data/api/ApiService.kt`
- File repository: `data/api/ApiRepository.kt`
- Cache manager: `data/cache/FileController.kt`

### Models

- File: `data/models/File.kt`
- Drive: `data/models/Drive.kt`
- User: `data/models/UiSettings.kt`

### Sync & Services

- Sync adapter: `data/sync/SyncAdapter.kt`
- Upload service: `data/services/UploadWorker.kt`

### String resources

- After every change in strings.xml files, raise a warning for the user that he MUST mirror the changes to Loco

## UI Organization

```bash
ui/
├── fileList/           # File browsing (FileListFragment, FileAdapter)
├── menu/               # Navigation drawer, settings, trash
│   └── settings/       # Sync settings, preferences
├── home/               # Home dashboard
├── login/              # Authentication flow
├── dropbox/            # Dropbox creation/management
├── bottomSheetDialogs/ # Bottom sheets (share, move, etc.)
├── addFiles/           # New folder, upload options
├── publicShare/        # Public sharing UI
└── selectPermission/   # Permission selection
```

## JIT Index

### Find ViewModels

```bash
rg -n "@HiltViewModel|class.*ViewModel" app/src/main/
```

### Find API endpoints

```bash
# Repository methods
rg -n "suspend fun.*upload|suspend fun.*delete" app/src/main/java/com/infomaniak/drive/data/api/

# Data models
rg -n "open class" app/src/main/java/com/infomaniak/drive/data/models/
```

### Find UI components

```bash
# Fragments
rg -n "class.*Fragment" app/src/main/java/com/infomaniak/drive/ui/

# Activities
rg -n "class.*Activity" app/src/main/java/com/infomaniak/drive/ui/

# Adapters
rg -n "class.*Adapter" app/src/main/java/com/infomaniak/drive/
```

### Search strings

```bash
rg -n "R\.string\." app/src/main/java/ | head -20
```

### Find extensions

```bash
ls app/src/main/java/com/infomaniak/drive/extensions/
```

## Testing

### Unit Tests

- Location: `app/src/test/`
- Framework: JUnit 5 (via `junit5` plugin)
- Example: `app/src/test/kotlin/RootFileTreeCategoryTest.kt`

### Instrumented Tests

- Location: `app/src/androidTest/`
- Env setup: Copy `Env-Example` to `Env.kt` (disable 2FA for test account)

### Run Commands

```bash
# All tests
./gradlew app:test

# Single test class
./gradlew app:test --tests "com.infomaniak.drive.RootFileTreeCategoryTest"
```

## Common Gotchas

- **Realm transactions**: Always use `realm.executeTransactionAwait()` with coroutines
- **File operations**: All file I/O must use kDrive utilities in `utils/` package
- **Background sync**: Requires `FOREGROUND_SERVICE_DATA_SYNC` permission for Android 14+
- **Media permissions**: Android 13+ uses granular `READ_MEDIA_*` permissions
- **Hilt modules**: Keep provider methods in `di/ApplicationModule.kt`

## Pre-PR Checks

```bash
./gradlew app:assembleStandardDebug && ./gradlew app:testStandardDebugUnitTest
```
