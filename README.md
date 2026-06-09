# Plant Nursery Scanner

An offline-first Android app for the Royal Botanic Gardens Melbourne volunteer nursery: scan a plant
barcode, build a receipt, save it locally, and auto-export sales to Google Sheets. Built for elderly
volunteers (big buttons, big text, high contrast, no flicker, tap-not-gesture).

> Status: **Phase 1 complete** (Sell · Sync plant list · Export sales). Phase 2 (accession, label,
> repot, death) is sketched in the spec but not built.

## What it does

- **Sell** — scan Code 128 (or type the accession), auto-fill the plant, key pots/price/discount %,
  add to a receipt, finish & save. Works **fully offline**. Not-found scans are sold "as unknown" and
  kept for later reconciliation — never lost.
- **Auto-export** — pending receipts push to Google Sheets every ~1 minute when online, silently. A
  manual **Export now** and **Update plant list** are one tap each.
- **Receipts** — local sales history, grouped by receipt.
- Receipt numbers are `PP-NNN` with a per-device 2-digit prefix, so multiple devices never collide.

## Architecture (three independently-buildable parts)

```
core/      Pure Kotlin/JVM — ALL business logic (money, receipt #, plant lookup, sync, export, config).
           No Android types. Unit-tested with `gradle test`. (28 tests.)
app/       Android (Jetpack Compose + Room + CameraX + ML Kit + Retrofit + DataStore). Thin glue over
           `core`. Consumes core via a Gradle composite build.
backend/   Google Apps Script web app (getPlants / appendSales, shared-secret auth, dedupe). Pure
           logic mirrored as Node-testable JS. (6 tests.)
```

Why the split: the build machine had no Android SDK, so every decision that's easy to get wrong was
pushed into `core/` and tested there; the Android module is declarative UI compiled with the SDK.

```
Android app ──HTTPS+JSON──► Apps Script /exec ──► Google Sheet (Plants / Sales)
```

## Build & test

```bash
# Business logic (no Android SDK needed)
cd core && gradle test

# Backend logic (Node)
node --test backend/test/logic.test.js

# Android app (needs Android Studio / Android SDK + JDK 17)
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

## Deploy

See **[docs/deploy/README.md](docs/deploy/README.md)** — backend → app → connect, in that order.

## Key tech choices

- **Kotlin + Jetpack Compose** — native, since the target is Android-only and sideloaded.
- **ML Kit Barcode Scanning** (bundled, offline), restricted to **Code 128**.
- **Room** for offline-first storage; the receipt `status` column *is* the sync queue.
- **In-app coroutine ticker** for the 1-minute auto-export (WorkManager's floor is 15 min).
- **Apps Script + shared secret** instead of the Sheets API + OAuth.

See `docs/tech-stack.md` for the full rationale and `docs/superpowers/specs/` for the design spec.

## Project docs

- `docs/superpowers/specs/2026-06-09-plant-scanner-screen-flows-design.md` — the approved design spec.
- `docs/tech-stack.md` — technology decisions.
- `docs/superpowers/plans/2026-06-09-plant-scanner-implementation.md` — the implementation plan.
- `docs/deploy/` — deployment & wiring instructions.
