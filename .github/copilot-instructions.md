# Copilot Coding Agent Onboarding Guide for Infomaniak/android-kDrive

Before reading this file, please read AGENTS.md to learn more about the project context, structure, and conventions.

## Overview

kDrive for Android — cloud storage app. Fragment + Jetpack Compose hybrid UI, Hilt DI, Realm (offline file cache) + Room (auth
tokens), Ktor networking. Three build flavors: `standard`, `fdroid`, `preprod`. GeniusScan SDK (document scanning, `standard`
only), Paho MQTT (real-time updates). Tests use JUnit 5 via the `junit5` Gradle plugin.

## One-Time Environment Setup

```bash
git submodule update --init --recursive    # pull Core submodule
cp env.example.properties env.properties  # fill sentryAuthToken (dummy value OK locally)
# Files not committed to git (CI auto-generates these):
 touch app/src/androidTest/java/com/infomaniak/drive/utils/Env.kt              # instrumented tests only
 touch app/src/standard/java/com/infomaniak/drive/GeniusScanEnv.kt            # required for `standard` builds (GeniusScan)
```

Missing `env.properties` blocks Gradle configuration only for release tasks (task names containing `release`), because
`sentryAuthToken` is required then.

## Build & Test (CI: `.github/workflows/android.yml`)

CI runs on every non-draft PR:

```bash
./gradlew clean
./gradlew build
./gradlew testDebugUnitTest --stacktrace   # JUnit 5 — use @Test from org.junit.jupiter.api
```

Flavor-specific commands:

```bash
./gradlew assembleStandardDebug
./gradlew assembleFdroidDebug
./gradlew assemblePreprodDebug
```

## Project Layout

```
app/src/main/java/com/infomaniak/drive/
├── MainApplication.kt         # Entry point, Realm init via runBlocking { initRealm() }
├── data/
│   ├── cache/                 # Realm controllers + FileMigration.kt / DriveMigration.kt
│   ├── models/                # Realm entities (File, Drive, User…)
│   └── api/                   # API service interfaces
├── ui/                        # Fragment + Compose screens
└── workers/                   # WorkManager tasks
Core/                          # Git submodule — Infomaniak shared library
gradle/libs.versions.toml
```

## PR Review Instructions

- Ensure strings are localized via `strings.xml` resources.
- When reviewing Realm model changes, check whether the persisted schema changed: added, removed, renamed, or type-changed
  persisted properties, changed optionality, lists, embedded objects, or object types.
- If the persisted Realm schema changed, ensure the matching `DB_VERSION` (`FileMigration` or `DriveMigration`) was incremented in
  the corresponding file (`app/src/main/java/com/infomaniak/drive/data/cache/FileMigration.kt` or `DriveMigration.kt`), and that
  the relevant migration block is updated when existing data needs migration.
- Ensure new UI written in Jetpack Compose uses Material3 components and follows the hybrid approach (Compose for new screens, XML
  with ViewBinding for existing, supports different screen sizes).
- `standard` flavor only: Firebase, GeniusScan — fdroid and preprod builds must compile without them.
- Realm is initialized with `runBlocking { initRealm() }` in `MainApplication` — any Realm access before this completes will
  crash.
- Unit tests use JUnit 5 (`@Test` from `org.junit.jupiter.api`, not JUnit 4).
- When adding/removing a runtime dependency, update `LICENSES.md` at the repo root.
