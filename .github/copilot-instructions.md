# Copilot Coding Agent Onboarding Guide for Infomaniak/android-kDrive

Before reading this file, please read AGENTS.md to learn more about the project context, structure, and conventions.

## Pull Request Review Instructions

- Ensure strings are localized via `strings.xml` resources.
- When reviewing Realm model changes, check whether the persisted schema changed: added, removed, renamed, or type-changed persisted properties, changed optionality, lists, embedded objects, or object types.
- If the persisted Realm schema changed, ensure the matching `DB_VERSION` (`FileMigration` or `DriveMigration`) was incremented in the corresponding file (`app/src/main/java/com/infomaniak/drive/data/cache/FileMigration.kt` or `DriveMigration.kt`), and that the relevant migration block is updated when existing data needs migration.
- Ensure new UI written in XML follows Material Design guidelines and supports different screen sizes.
- New UI written in Jetpack Compose should use shared Design System components where applicable.
