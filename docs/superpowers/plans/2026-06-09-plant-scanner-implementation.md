# Plant Nursery App — Implementation Plan

> **For agentic workers:** This plan is executed in the same session by the author, autonomously,
> with a self-review against `docs/superpowers/specs/2026-06-09-plant-scanner-screen-flows-design.md`
> after every task. Steps use checkbox (`- [ ]`) tracking.

**Goal:** Build an offline-first Android sales app for the volunteer nursery (scan Code 128 → build a
receipt → save locally → auto-export to Google Sheets every ~1 min), plus the Google Apps Script
backend and full deployment instructions.

**Architecture:** Three independently-buildable parts.
1. `core/` — standalone **pure-Kotlin/JVM** Gradle build holding *all* business logic (money, receipt
   numbering, plant lookup, sync selection, export rows, config validation). No Android types → unit
   tested on the JVM here.
2. `app/` — **Android** (Kotlin + Jetpack Compose + Room + CameraX + ML Kit + Retrofit + DataStore),
   consuming `core` via a Gradle **composite build** (`includeBuild("core")`). Thin glue around `core`.
3. `backend/` — **Google Apps Script** web app (`doGet` plant pull, `doPost` sales append, shared-secret
   auth, dedupe by receipt #). Pure logic mirrored as Node-testable JS.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, CameraX, ML Kit Barcode Scanning
(`FORMAT_CODE_128`), Retrofit + kotlinx-serialization, DataStore Preferences, Kotlin coroutines/Flow,
Google Apps Script, JUnit5/kotlin.test, Node built-in test runner.

---

## Why this split (the hard constraint)

This build machine has **JDK 19 + Gradle 8.4 + Node + Python but NO Android SDK**. The Android module
cannot compile here. By pushing every non-trivial decision into `core/` (plain JVM), the logic that is
easy to get wrong (money rounding, percent discount, receipt-# namespacing, "no double-count" sync) is
compiled and tested here; the Android module is only declarative glue + UI, compiled by the user.

## File structure

```
plant-scanner/
├─ core/                         # standalone Kotlin JVM build (tested HERE)
│  ├─ settings.gradle.kts        # rootProject.name = "core"
│  ├─ build.gradle.kts           # kotlin("jvm") 1.9.24, group=com.nursery
│  └─ src/
│     ├─ main/kotlin/com/nursery/core/
│     │  ├─ Models.kt            # Plant, LineItem, Receipt, ReceiptStatus
│     │  ├─ Money.kt             # cents math, percent discount, AUD formatting
│     │  ├─ ReceiptNumbering.kt  # PP-NNN, prefix validation, sequence
│     │  ├─ PlantBook.kt         # scan lookup (found / not-found→unknown)
│     │  ├─ Sync.kt              # pending selection, status transitions
│     │  ├─ Export.kt            # ExportRow + buildExportRows + row strings
│     │  └─ DeviceConfig.kt      # prefix/url/secret/interval validation
│     └─ test/kotlin/com/nursery/core/  # one test file per source file
├─ app/                          # Android (compiled by USER with SDK)
│  ├─ build.gradle.kts ; proguard-rules.pro
│  └─ src/main/{AndroidManifest.xml, java/com/nursery/scanner/**, res/**}
├─ backend/                      # Apps Script + Node tests (tested HERE)
│  ├─ Code.gs ; appsscript.json ; shared.js ; test/logic.test.js
├─ docs/deploy/{backend.md, android.md, connect.md, README.md}
├─ settings.gradle.kts           # Android root: includeBuild("core"); include(":app")
├─ build.gradle.kts ; gradle.properties ; gradle/libs.versions.toml
└─ README.md
```

## Spec-conformance matrix (every requirement → where it lives)

| Spec item | Implemented by |
|---|---|
| Action-first home (#1) | `ui/home/HomeScreen.kt` — big Sell tile + dimmed Phase-2 tiles |
| Offline-first selling (#2) | Room writes first; scan reads cached `PlantEntity`; no network in Sell path |
| Payment not captured (#3) | No payment fields anywhere; receipt stores total only |
| Receipt = one customer (#4) | One `ReceiptEntity` per Sell tap; many `LineItemEntity` |
| Discount = percent (#5) | `core/Money.lineTotalCents` uses `discountPct` 0–100 |
| Unit price keyed, no pre-fill (#6) | `LineItem.unitPriceCents` keyed in `LineItemScreen`; no price on `Plant` |
| Not-found → sell as unknown (#7) | `core/PlantBook.toUnknownLine` stores `scannedCode`, name "unknown" |
| Google Sheets only (#8) | `backend` + `data/remote/SheetsClient` |
| One-tap manual Update/Export (#9) | `SyncScreen` buttons → progress → Done/Error |
| Auto-export default 1 min, silent (#10) | `sync/AutoExportTicker` (60s default), no UI on tick |
| Receipt # `PP-NNN`, per-device prefix (#11) | `core/ReceiptNumbering` + prefix in DataStore |
| Big buttons/text, high contrast (a11y) | `ui/theme` (large type, AA colors), `BigButton` (≥56dp) |
| No flicker, stable layout (a11y) | auto-export silent; Compose stable state; no flashing |
| Tap-not-gesture, no swipe-to-delete (a11y) | line edit/remove via explicit buttons in `CartScreen` |
| Status chip + pending count | `StatusChip` driven by `SyncRepository.state` |
| Code 128 symbology | `scanner/BarcodeAnalyzer` restricts to `FORMAT_CODE_128` |

---

## Task 0 — Repo scaffold

**Files:** root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `core/settings.gradle.kts`, `core/build.gradle.kts`.

- [ ] Root settings uses `includeBuild("core")` + `include(":app")`.
- [ ] `core/` is a standalone build: `kotlin("jvm")` 1.9.24, JUnit5, `group = "com.nursery"`.
- [ ] Verify: `cd core && gradle test` runs (no tests yet → BUILD SUCCESSFUL / "no tests").

## Task 1 — core: Models + Money (TDD)

**Files:** `core/.../Models.kt`, `Money.kt`, `test/.../MoneyTest.kt`.

Key signatures:
```kotlin
enum class ReceiptStatus { OPEN, SAVED, EXPORTED }
data class Plant(val accession: String, val barcode: String?, val name: String,
                 val group: String?, val light: String?)
data class LineItem(val accession: String?, val scannedCode: String?, val name: String,
                    val pots: Int, val unitPriceCents: Long, val discountPct: Int)
data class Receipt(val localId: Long, val receiptNo: String, val createdAtEpochMs: Long,
                   val status: ReceiptStatus, val lines: List<LineItem>)

object Money {
    fun lineTotalCents(pots: Int, unitPriceCents: Long, discountPct: Int): Long  // half-up rounding
    fun receiptTotalCents(lines: List<LineItem>): Long
    fun formatAud(cents: Long): String   // "$12.30"
}
```
Tests: pots×price; 10% discount rounding (half-up); 100%→0; 0 pots→0; reject pct<0/>100 and pots<0;
multi-line total; formatAud cents/dollars.

## Task 2 — core: ReceiptNumbering (TDD)

```kotlin
class ReceiptNumbering(val prefix: String) {     // require prefix matches Regex("\\d{2}")
    fun format(seq: Int): String                 // "07-241"
    fun next(current: Int): Int                   // current + 1
}
```
Tests: format "07" + 241 → "07-241"; reject "7", "123", "ab"; next increments.

## Task 3 — core: PlantBook (TDD)

```kotlin
class PlantBook(plants: List<Plant>) {           // index by accession AND barcode
    fun findByScan(code: String): Plant?
    fun toLine(plant: Plant, pots: Int, unitPriceCents: Long, discountPct: Int): LineItem
    fun toUnknownLine(scannedCode: String, pots: Int, unitPriceCents: Long, discountPct: Int): LineItem
}
```
Tests: find by accession; find by barcode; miss → null; `toUnknownLine` keeps `scannedCode`, name
"unknown", accession null; `toLine` copies accession+name.

## Task 4 — core: Sync + Export (TDD)

```kotlin
object Sync {
    fun pending(receipts: List<Receipt>): List<Receipt>      // status == SAVED
    fun markSaved(r: Receipt): Receipt
    fun markExported(r: Receipt): Receipt
}
data class ExportRow(val receiptNo: String, val isoDate: String, val accession: String?,
                     val scannedCode: String?, val name: String, val pots: Int,
                     val unitPriceCents: Long, val discountPct: Int, val lineTotalCents: Long)
object Export {
    fun buildRows(receipts: List<Receipt>, zone: ZoneId): List<ExportRow>   // one row per line
    fun rowAsStrings(row: ExportRow): List<String>                          // Sheet append order
}
```
Tests: `pending` returns only SAVED; after `markExported` it is excluded (no double-count); `buildRows`
emits one row per line with correct `lineTotalCents` + ISO date; row string order stable.

## Task 5 — core: DeviceConfig (TDD)

```kotlin
data class DeviceConfig(val devicePrefix: String, val endpointUrl: String,
                        val sharedSecret: String, val autoExportSeconds: Int) {
    init { require(devicePrefix.matches(Regex("\\d{2}")));
           require(endpointUrl.isNotBlank()); require(autoExportSeconds >= 10) }
    val isComplete: Boolean   // url & secret non-blank
}
```
Tests: valid config ok; bad prefix/empty url/short interval throw; `isComplete` reflects blank secret.

**After Tasks 1–5:** run `cd core && gradle test` → ALL GREEN (the proof gate).

## Task 6 — app: Room layer

**Files:** `data/local/entity/{Plant,Receipt,LineItem}Entity.kt`, `dao/{Plant,Receipt}Dao.kt`,
`NurseryDatabase.kt`, `Mappers.kt`, `Converters.kt`.
Entities mirror core; `Mappers.kt` converts Entity↔core model. `ReceiptDao` has `@Transaction` upsert
of receipt+lines and `markExported(ids)`. `PlantDao.replaceAll()` is `@Transaction` (delete+insert) for
the wholesale plant-list pull.

## Task 7 — app: Settings + Remote + Repositories

**Files:** `data/settings/SettingsRepository.kt` (DataStore: prefix, url, secret, intervalSec, seq,
lastSyncedMs), `data/remote/{SheetsApi,Dtos,SheetsClient}.kt` (Retrofit POST/GET to Apps Script,
kotlinx-serialization), `data/repo/{Plant,Receipt,Sync}Repository.kt`. `SyncRepository` exposes a
`StateFlow<SyncState>` (online?, pending count, lastSynced, busy/error) feeding the status chip.

## Task 8 — app: AutoExportTicker

**Files:** `sync/AutoExportTicker.kt`. `while (isActive) { runCatching { syncRepo.exportPending() };
delay(intervalMs) }` on an app-lifecycle scope; **silent** (no UI). Manual `Export now` calls the same
`syncRepo.exportPending()` but surfaces result.

## Task 9 — app: Scanner

**Files:** `scanner/BarcodeAnalyzer.kt` (ML Kit `BarcodeScannerOptions` → `FORMAT_CODE_128`, debounced
single emit), `scanner/ScannerView.kt` (CameraX `PreviewView` in `AndroidView`, steady viewfinder, torch
button). Permission handled in `ScanScreen`.

## Task 10 — app: Theme + components + nav

**Files:** `ui/theme/{Color,Type,Dimens,Theme}.kt` (Material 3, AA contrast, body ≥18sp, respects font
scale), `ui/components/{BigButton,StatusChip,PlantCard,NurseryScaffold}.kt` (BigButton ≥56dp + label),
`ui/nav/{Destinations,NurseryNavHost}.kt` (3-tab bottom bar: Actions/Receipts/Sync).

## Task 11 — app: Home + Sell flow

**Files:** `ui/home/HomeScreen.kt`, `ui/sell/{SellViewModel,ScanScreen,LineItemScreen,CartScreen,
ConfirmScreen}.kt`. `SellViewModel` holds the in-progress receipt; flow Scan→LineItem→Cart→Confirm
exactly per spec, incl. not-found "Sell as unknown", tap-to-edit/remove (no swipe), discard empty receipt.

## Task 12 — app: Receipts + Sync + Settings

**Files:** `ui/receipts/{ReceiptsViewModel,ReceiptsScreen,ReceiptDetailScreen}.kt`,
`ui/sync/{SyncViewModel,SyncScreen}.kt`, `ui/settings/SettingsScreen.kt` (device prefix, endpoint URL,
secret, interval). Sync tile shows "Auto every N min · pending · last HH:MM" + Update / Export now.

## Task 13 — backend: Apps Script + Node tests (TDD, run HERE)

**Files:** `backend/shared.js` (pure: `checkAuth`, `mapReceiptsToRows`, `dedupeByReceiptNo`,
`plantsToObjects`), `backend/Code.gs` (`doGet`/`doPost` using shared fns), `backend/appsscript.json`,
`backend/test/logic.test.js`. Run `node --test backend/test` → GREEN.

## Task 14 — Deployment docs

**Files:** `docs/deploy/backend.md` (Sheet + tabs, Apps Script paste/clasp, deploy Web App, secret),
`docs/deploy/android.md` (install Studio/SDK, build debug APK, `adb install`, grant camera),
`docs/deploy/connect.md` (enter URL+secret+prefix in Settings, Update plant list, test a sale + export),
`docs/deploy/README.md` (index + end-to-end order).

## Task 15 — Final verification

Re-run `cd core && gradle test` and `node --test backend/test`; confirm both green; check the
spec-conformance matrix line-by-line; write root `README.md`.

## Self-review hooks

After each task: open the spec, find the relevant section, confirm the code matches the decision
(numbered #1–#11, accessibility bullets, sale-flow states, sync semantics). Note conformance or fix.
