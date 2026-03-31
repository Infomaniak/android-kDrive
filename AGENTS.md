# kDrive Android

## Project Snapshot

Modular Android application for kDrive cloud storage by Infomaniak. Uses MVVM architecture with Hilt DI, Realm for offline data, Ktor for API calls. Supports 3 build flavors (standard, fdroid, preprod) via Gradle composite builds with Infomaniak Core library.

**Languages**: Kotlin (100%), XML layouts (migrating to Compose)

**Key Architecture**: MVVM + Repository pattern with Hilt dependency injection

## Root Setup Commands

```bash
# Build entire project
./gradlew assembleStandardDebug

# Run all unit tests
./gradlew test

# Build specific flavor
./gradlew assembleFdroidDebug
./gradlew assemblePreprodDebug

# Clean build
./gradlew clean

# Install debug on connected device
./gradlew installStandardDebug
```

## Universal Conventions

- **Kotlin code style**: Official style guide (enforced via `.editorconfig`)
- **Commit format**: Conventional commits recommended
- **Branch strategy**: Feature branches → main, create issue before PR
- **PR requirements**: CI must pass, at least one review for significant changes
- **Never commit**: API tokens, credentials, or local.properties values

## Security & Secrets

- Never commit `.env`, `local.properties`, or API keys to repository
- Drive API tokens stored in Room database (encrypted)
- User files cached in Realm with offline support
- Bug tracker credentials defined in build.gradle.kts (safe values only)

## JIT Index

### Module Structure

- **Main App**: `app/` → [see app/AGENTS.md](app/AGENTS.md)
- **Core Libraries**: `Core/` → [see Core/AGENTS.md](Core/AGENTS.md)

### Core Sub-modules (Composite Build)

Key modules consumed via Gradle composite builds:

- `Core:Auth` - OAuth2 authentication flow
- `Core:Network` - Ktor HTTP client configuration
- `Core:Ui:View` - Shared UI components (XML-based)
- `Core:Ui:Compose` - Shared Compose components
- `Core:Common` - Extensions and utilities
- `Core:Legacy` - Legacy support module

### Quick Find Commands

```bash
# Find ViewModels
rg -n "class.*ViewModel" app/src/ Core/

# Find Hilt-injected components
rg -n "@AndroidEntryPoint|@HiltViewModel" app/src/main/

# Search API endpoints
rg -n "ApiRepository|suspend fun.*Api" app/src/main/

# Find Realm models
rg -n "open class.*RealmObject|@RealmClass" app/src/main/

# Locate UI fragments
rg -n "class.*Fragment" app/src/main/java/com/infomaniak/drive/ui/

# Find test files
find . -name "*Test.kt" -o -name "*Test.java"

# Search string resources
rg -n "R\.string\." app/src/main/
```

## Build Flavors

| Flavor   | Description                    | API Endpoints                                         |
|----------|--------------------------------|-------------------------------------------------------|
| standard | Google Play (default)          | Production kDrive API                                 |
| fdroid   | F-Droid release                | Production API, no proprietary dependencies           |
| preprod  | Pre-production testing         | kdrive.preprod.dev.infomaniak.ch staging servers      |

## Definition of Done

- [ ] Code compiles without warnings: `./gradlew compileStandardDebugKotlin`
- [ ] Unit tests pass: `./gradlew test`
- [ ] No hardcoded strings in UI (use `strings.xml`)
- [ ] New features include proper ViewModel separation
- [ ] Hilt DI used for dependencies (no manual instantiation)
- [ ] Background work uses WorkManager (not raw threads)
- [ ] File operations use kDrive-specific utilities
