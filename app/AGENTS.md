# app/

Android UI glue: Compose, Room, CameraX, ML Kit, DataStore, OkHttp. Wraps `core/`.

## Commands

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleQaDebug
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installQaDebug
```

JDK 21 via Android Studio JBR on this machine. Two flavors (`prod` / `qa`) install side by side.
Device/emulator setup: [`docs/deploy/connect.md`](../docs/deploy/connect.md).

## Structure

- **Manual DI** — `di/AppContainer.kt`; no Hilt.
- **`SyncRepository`** — only cloud I/O; serialized by `cloudMutex`.
- **Auto-export** — in-app coroutine ticker (`AutoExportTicker`), not WorkManager; started from
  `MainActivity` after optional CI mode (so CI can leave it off).
- **Room** — no `fallbackToDestructiveMigration`; schema changes need real `Migration`s in
  `Migrations.kt`. Check `NurseryDatabase.kt` for current version.
- **CI mode (qaDebug only)** — `src/qaDebug/` orchestrator + activator; launch extra
  `com.nursery.scanner.CI_MODE`. See [`docs/deploy/screenshots-ci.md`](../docs/deploy/screenshots-ci.md).

## UI constraints

Volunteers are elderly: large text and tap targets, high contrast, no flicker, tap-not-gesture.

## Rules

- No business logic in ViewModels/Composables — delegate to `core/` and test there.
- KSP, Kotlin, and Room compiler versions in `libs.versions.toml` must stay in lockstep.
- Flavor is named `qa`, not `test` (AGP rejects `test` flavor names).

See root [`AGENTS.md`](../AGENTS.md) and [`CONTEXT.md`](../CONTEXT.md).
