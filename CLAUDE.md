# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An offline-first Android app for a volunteer plant nursery: scan a Code 128 barcode, build a receipt,
save locally, auto-export sales to Google Sheets. Phase 1 (Sell · Sync plant list · Export sales) is
complete; Phase 2 (accession/label/repot/death) is specced but unbuilt. UI is tuned for elderly
volunteers: big buttons/text, high contrast, no flicker, tap-not-gesture.

## The one architectural idea that matters

**All business logic that is easy to get wrong lives in `core/` — a pure Kotlin/JVM module with no
Android types — and is unit-tested there.** The Android `app/` module is thin, declarative glue.

This split exists so the easy-to-break logic gets **fast, isolated JVM unit tests** in `core/`. The
Android `app/` module *can* be assembled on this machine (see Build & test — the Android SDK and the
Android Studio JBR are installed), but it is Compose/Room/UI glue with no unit tests runnable here (no
emulator), so logic buried in a ViewModel or Composable can't be verified the way `core/` can. **When
you add or change logic (money math, receipt numbering, sync selection, export shaping, validation,
search/filter), put it in `core/` and cover it with a `core/` test.**

```
core/      Pure Kotlin/JVM — money, receipt numbering, plant lookup, sync selection, export rows,
           device config, plant search. No Android imports. Consumed by app/ via a Gradle composite
           build (root settings.gradle.kts: includeBuild("core"); app depends on com.nursery:core).
app/       Android (Compose + Room + CameraX + ML Kit + DataStore + OkHttp). Wraps core/.
backend/   Google Apps Script web app. shared.js is pure logic (auth, plant parsing, dedupe) mirrored
           into the GAS project as shared.gs; Code.gs is the doGet/doPost entry point.
```

Data flow: `Android app ──HTTPS+JSON──► Apps Script /exec ──► Google Sheet (Plants / Sales)`.

## Build & test

```bash
# core/ business logic — JUnit5, no Android SDK needed. Uses a system `gradle`, NOT ./gradlew.
cd core && gradle test
cd core && gradle test --tests "com.nursery.core.MoneyTest"     # single test class

# backend/ logic — Node's built-in test runner
node --test backend/test/logic.test.js
node --test --test-name-pattern "isAuthorized" backend/test/logic.test.js   # single test

# app/ Android — assembles on THIS machine. The SDK is wired via local.properties
# (sdk.dir=C:\Users\vital\AppData\Local\Android\Sdk). Build with the Android Studio JBR (JDK 21);
# the machine's default JAVA_HOME is a non-LTS/EOL JDK 19, so point JAVA_HOME at the JBR:
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
#   -> app/build/outputs/apk/debug/app-debug.apk
```

`core/` and `app/` target JVM 17 bytecode. `core/` is its own standalone Gradle build (it has its own
`settings.gradle.kts`/`build.gradle.kts` and is run with a system `gradle`); the root `./gradlew` build
drives the Android app and pulls `core/` in as an included build.

## Domain rules that span multiple files

- **The receipt `status` column IS the sync queue.** `OPEN` (cart) → `SAVED` (pending export) →
  `EXPORTED`. `Sync.pending()` returns only `SAVED`; export flips them to `EXPORTED` **only on
  success**. This is the whole "nothing lost / no double-counting" guarantee — preserve it.
- **All money is integer cents** (`Money.kt`); never floats. `lineTotal = pots × unitPrice ×
  (1 − discountPct/100)`, net rounded half-up. `discountPct` is a percentage 0..100.
- **accession == barcode.** The Code 128 label encodes the accession number itself; there is no
  separate barcode field, and the plant sheet has **no price column** — unit price is keyed at sale on
  the line item only.
- **Not-found scans are never lost.** A scan that misses the plant list becomes a "sell as unknown"
  line (`name = "unknown"`, scanned code kept as accession) for later reconciliation — see `PlantBook`.
- **Receipt numbers** are `PP-<epochSeconds>-<seq>` (`ReceiptNumbering.kt`): `PP` is the per-device
  2-digit prefix from settings (namespaces devices in the shared Sheet), `seq` resets daily. Note the
  prose in older docs says `PP-NNN`; the code is the source of truth.
- **Export column order** (`Export.HEADER`) is relied on by the Apps Script backend — keep it stable.
  Columns: `receipt, date, accession, name, qty, unit, unit_price, discount_pct, line_total`. `qty` is
  the count of `unit`s (Pots/Tubes/Misc); `unit` is the sale-unit chosen on the line-item screen,
  defaulted from the plant's `*InNursery` counts via `SaleUnit.defaultFor`.

## app/ structure notes

- **Manual DI, no Hilt** — `di/AppContainer.kt` builds the Room DB, repositories, the auto-export
  ticker, and a `NurseryViewModelFactory`. One instance per process, created in `NurseryApplication`.
- **`SyncRepository` is the only place that talks to the cloud.** Both the silent auto-export ticker
  and the manual "Export now"/"Update plant list" buttons funnel through it; a `cloudMutex` serializes
  every cloud op so they can't run concurrently and double-push.
- **Auto-export is an in-app coroutine ticker** (`sync/AutoExportTicker.kt`), NOT WorkManager —
  WorkManager's periodic floor is 15 min and the requirement is ~60s. It runs only while the app is
  open, swallows all results/errors (no flicker), and reuses `SyncRepository.exportPending()`.
- **Navigation** (`ui/NurseryRoot.kt`, `ui/nav/Destinations.kt`): three bottom tabs (Actions /
  Receipts / Sync). The Sell flow is a **nested nav graph** so one `SellViewModel` is shared across
  Scan → LineItem → Cart → Confirm. Full-screen sub-flows hide the top/bottom bars (`TabRoutes`).
- **Room** is at `version = 2` (`NurseryDatabase.MIGRATION_1_2`: additive — plant stock counts +
  `line_items.unit`). The plant list is replaced wholesale on "Update plant list".

## Gotchas

- **`core/bin/` and `app/build/` are generated** — `core/bin/main/...` contains copies of the `.kt`
  sources that show up in searches. The real sources are under `core/src/`. Edit `core/src/`, never
  `core/bin/`.
- `app/` **assembles** here (`:app:assembleDebug` with the Android Studio JBR — see Build & test), but
  there's no emulator, so app-level/instrumented tests don't run. Validate business logic via `core/`
  tests, confirm the app compiles with `assembleDebug`, and exercise it on a device per
  `docs/deploy/connect.md`.
- **The Android toolchain is on KSP2** (Kotlin 2.2.10 + KSP `2.2.10-2.0.2`). KSP2 needs **Room ≥ 2.7**
  (`room-compiler` 2.6.x throws `unexpected jvm signature V`), and the KSP version's `<kotlin>` prefix
  must match `kotlin` in `libs.versions.toml`. Keep those three in lockstep when bumping any of them.

## Docs

- `docs/superpowers/specs/2026-06-09-plant-scanner-screen-flows-design.md` — approved design spec.
- `docs/superpowers/specs/2026-06-15-access-google-sheets-plant-sync-design.md` — Access → Sheets plant
  sync design (full-mirror `replacePlants`, raw 43-col Batches+Species view, env-var-gated VBA push).
- `docs/tech-stack.md` — technology decisions and rationale (why Room, why not WorkManager, etc.).
- `docs/deploy/` — deployment in order: `backend.md` → `android.md` → `connect.md` → `access.md`
  (the nursery-PC Access → Sheets sync).
