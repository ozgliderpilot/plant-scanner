# Plant Nursery App — Tech Stack

**Date:** 2026-06-09
**Status:** Decided
**Related:** `superpowers/specs/2026-06-09-plant-scanner-screen-flows-design.md`

## Context & constraints

| Constraint | Value |
|---|---|
| Target platform | **Android only** |
| Distribution | **Sideloaded** (manually installed on ~2 devices, not on Play Store) |
| Connectivity | **Offline-first** — selling works fully offline; **cloud sync every ~1 min when online** (export sync queue, then import plant list) |
| Developer background | Comfortable in Java/Kotlin/TypeScript/Python; **new to mobile dev** |
| Guiding priority | **Simplicity** |

Because the target is a single platform that is hand-installed on a couple of trusted
devices, cross-platform frameworks add an abstraction tax for portability that will never
be used. The stack below favors first-party, best-documented, most-reliable tools.

## Decision: Native Android (Kotlin + Jetpack Compose)

| Concern | Choice | Why |
|---|---|---|
| Language + UI | **Kotlin + Jetpack Compose** | Kotlin already known; Compose is declarative (React-like) and far simpler than legacy XML Android |
| Camera | **CameraX** | Standard Jetpack camera API; integrates directly with ML Kit |
| Barcode | **Google ML Kit Barcode Scanning** | Best *free* scanner, on-device/offline, smoothest live-camera UX; first-party on Android |
| Local DB | **Room** (SQLite) | Official, transactional, relational; models receipts cleanly with a sync-state flag |
| Async | **Kotlin coroutines + Flow** | Native to Kotlin/Room/Compose; clean reactive UI updates |
| Settings/small state | **DataStore** | API secret, **2-digit device prefix**, auto-export interval, last-synced timestamp, next local receipt sequence |
| Google Sheets | **Google Apps Script web app** + plain HTTPS/JSON (Retrofit or Ktor client) | Avoids OAuth/Cloud-project setup; a shared secret is enough for 2 trusted devices |

### Options considered (and why not)

- **Expo / React Native (TypeScript)** — TS is familiar, but ML Kit is reached only through a
  third-party native bridge (`react-native-vision-camera`). That puts JS↔native friction on the
  single riskiest part of the app — a poor trade for a first mobile project. Cross-platform
  payoff is wasted (Android-only).
- **Flutter (Dart)** — technically strong (`google_mlkit_barcode_scanning` is solid), but Dart is
  a new language and buys nothing for an Android-only app where Kotlin is already known.
- **Kotlin Multiplatform** — overkill; only one platform.
- **PWA / web app** — barcode + offline-install reliability on Android is fiddly.

## Barcode engine: Google ML Kit

**"Free" and "open source" are different questions, and the best differs.** ML Kit is the best
**free** scanner but is **proprietary** (not OSS). For a sideloaded internal tool with no
licensing scrutiny, that is an easy trade.

- ML-based detection → robust to angle/motion/lighting; best live-viewfinder UX (matches the Sell flow).
- Runs fully **on-device** → satisfies offline-first.
- **Use the *bundled* model variant** (ships inside the APK) so scanning works the instant the
  app is sideloaded — do not rely on the Play-Services-downloaded variant on hand-configured devices.
- Known weak spot is dense PDF417/EAN-13, which this app does not scan.

> If a hard "must be open source" rule ever applies, the best OSS engine is **ZXing-C++**
> (`zxing-cpp`, Apache 2.0). Not chosen here.

**Symbology: 1D linear, highly likely Code 128.** Restrict ML Kit to the expected 1D formats via
`BarcodeScannerOptions.setBarcodeFormats(FORMAT_CODE_128, …)` — narrowing formats speeds detection and
avoids false reads from stray 2D codes. Keep a couple of common 1D fallbacks (Code 39, EAN-13) on hand;
they're a one-line change if a label surprises us. Verify against a real printed label before locking it down.

## Database: Room

Data is naturally **relational** (a receipt has many line items; each references a cached plant),
and the spec's cardinal rule is *"nothing lost"* → a transactional SQLite store, not a document DB.

```
PlantEntity(accession PK, name, group, light)                        ← replaced wholesale on cloud sync plant-list import (accession == barcode; sheet has no price column)
ReceiptEntity(localId PK, receiptNo, createdAt, status: OPEN | SAVED | EXPORTED)
LineItemEntity(id PK, receiptId FK, accession, name, pots, unitPrice, discountPct)
```

Field notes:

- **`accession`** is the scanned/typed code. The Code 128 label **encodes the accession**, so there
  is no separate barcode field; a not-found scan keeps its accession with `name = "unknown"` (#7, #12).
- **`receiptNo`** = `PP-<epochSeconds>-<seq>` (e.g. `07-1718000000-1`) — the device's configured
  **2-digit prefix** (`PP`, from DataStore), the creation time in epoch seconds, and a per-device
  sequence that resets daily. The prefix keeps numbers unique across devices in the shared Sheet, and
  the epoch-seconds segment keeps a reset sequence from colliding after a reinstall; no central
  coordination.
- **`unitPrice`** lives only on the line item and is **keyed at sale** — the plant sheet has no price
  column, so `PlantEntity` carries none.
- **`discountPct`** is a **percentage** (0–100); `lineTotal = pots × unitPrice × (1 − discountPct/100)`.

The `status` column **is** the sync queue: *export* grabs all `SAVED` receipts, pushes them to
Sheets, and flips them to `EXPORTED` only on success. That delivers the spec's
"no double-counting / nothing lost" behavior via a plain SQL transaction.

### Why not a NoSQL / "MongoDB for mobile"

- **MongoDB has no supported mobile DB anymore.** Realm / Atlas Device SDK reached
  **end-of-life on 2025-09-30**; the local DB survives only as an unsupported OSS fork. Avoid.
- ObjectBox / Couchbase Lite exist, but their headline feature is **built-in sync**, which is
  useless here — this app syncs to **Google Sheets** via its own code, not to a generic backend.
  They would add a heavier, smaller-community dependency for zero benefit.

## Google Sheets integration

Skip the official Sheets API (OAuth, Cloud project, consent screens, token refresh — overkill for
2 trusted devices). Instead:

- A **Google Apps Script** bound to the Sheet, deployed as a Web App, with:
  - `doGet` / `getPlants` → returns the plant list (cloud sync import).
  - `doPost` → appends sales/cull rows (cloud sync export).
- Protected by a **shared secret** string sent by the app.
- The app talks to it with plain **HTTPS + JSON**. All Sheet formatting logic lives in the script.

## Cloud sync (the ~1-minute round trip)

**Cloud sync** exports the sync queue then imports the plant list (see
`docs/superpowers/specs/2026-07-10-unified-cloud-sync-design.md`). Default interval
**60s**, configurable via DataStore.

- **Not WorkManager.** `PeriodicWorkRequest` has a **15-minute minimum** interval, so it cannot do a
  1-minute cadence. Use an **in-app coroutine ticker** — a `while (isActive) { syncCloud(); delay(interval) }`
  loop on a lifecycle-aware scope that runs while the app is open.
- **Why that's enough:** volunteers keep the app open during a selling session — exactly when receipts
  accrue — so no background execution is needed.
- **Silent + safe:** each tick runs `SyncRepository.syncCloud` (export then import); rows flip to
  `EXPORTED` only on HTTP success; failures are swallowed and retried next tick (no popups/flicker).
  History/Plants ↻ reuse the same `syncCloud()` and surface Done/Error visibly.
- **If background export is ever required** (app closed), promote to a **foreground Service** with a
  persistent notification — deferred; not needed for current usage.

## Full stack summary

- **Kotlin** + **Jetpack Compose** (UI)
- **CameraX** + **ML Kit Barcode Scanning** (bundled model, offline)
- **Room** (SQLite) + **Kotlin coroutines/Flow**
- **DataStore** (settings/secret/timestamps)
- **Retrofit** or **Ktor** client → **Google Apps Script** web app (HTTPS/JSON)

## Resolved questions

- **Label symbology:** 1D linear, highly likely **Code 128** → restrict ML Kit to 1D formats
  (`FORMAT_CODE_128` + cheap fallbacks); confirm against a real printed label. The barcode **encodes
  the accession number** (no separate barcode field).
- **Unit price:** no price column in the sheet → keyed at sale, on the line item only.
- **Source of truth:** Google Sheets (the upstream MS Access → Sheets pipeline is out of scope).
- **Receipt #:** per-device `PP-<epochSeconds>-<seq>`, purely local; 2-digit prefix from config,
  sequence resets daily.
- **Multiple devices:** assumed yes → collisions avoided by the prefix; no sync-merge logic.

*No open questions remain — pending only a sanity-check scan of a real printed label.*
