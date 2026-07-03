# AGENTS.md

Agent instructions for working with code in this repository.

## What this is

An offline-first Android app for a volunteer plant nursery: scan barcodes, record sales and culls
locally, sync to Google Sheets. UI is tuned for elderly volunteers: big buttons/text, high contrast,
no flicker, tap-not-gesture.

**Before naming domain concepts** (in issues, tests, commits, or UI copy), read [`CONTEXT.md`](./CONTEXT.md).

## The one architectural idea that matters

**All business logic that is easy to get wrong lives in `core/` — a pure Kotlin/JVM module with no
Android types — and is unit-tested there.** The Android `app/` module is thin, declarative glue.

When you add or change logic (money math, receipt numbering, sync selection, export shaping,
validation, search/filter), put it in `core/` and cover it with a `core/` test.

```
core/      Pure Kotlin/JVM. No Android imports. See core/AGENTS.md.
app/       Android glue (Compose, Room, CameraX). See app/AGENTS.md.
backend/   Google Apps Script web app. See backend/AGENTS.md.
```

Data flow: `Android app ──HTTPS+JSON──► Apps Script /exec ──► Google Sheet`.

## Build & test

```bash
cd core && gradle test
node --test backend/test/logic.test.js
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleQaDebug
```

`core/` uses a system `gradle`, not `./gradlew`. `app/` build needs JDK 21 (Android Studio JBR on this
machine) — see [`docs/deploy/android.md`](./docs/deploy/android.md). Device/emulator setup:
[`docs/deploy/connect.md`](./docs/deploy/connect.md).

Per-module detail: [`core/AGENTS.md`](./core/AGENTS.md), [`backend/AGENTS.md`](./backend/AGENTS.md),
[`app/AGENTS.md`](./app/AGENTS.md).

## Invariants (do not break)

These are behavioural guarantees, not reference data — see `Sync`, `CullSync`, `Money`, `Export`,
`CullExport`, and `PlantBook` in `core/` for specifics.

- **Local `status` is the sync queue** for receipts and culls. Export only pending rows; flip to
  exported **only on HTTP success**. Nothing lost, no double-counting.
- **Money is integer cents** — never floats.
- **Not-found scans are never dropped** — record as unknown with the scanned code kept.
- **Export `HEADER` column order is a backend contract** — keep stable; coordinate `core/` and
  `backend/` changes together.

## Verify changes

Before finishing:

1. `cd core && gradle test` — always, when `core/` or behaviour it owns changed
2. `node --test backend/test/logic.test.js` — when `backend/` or `shared.js` changed
3. `JAVA_HOME="…" ./gradlew :app:assembleQaDebug` — when `app/` changed
4. Do not commit unless the user asks

## Agent skills

### Issue tracker

GitHub Issues via `gh`; external PRs are not a triage surface. See [`docs/agents/issue-tracker.md`](./docs/agents/issue-tracker.md).

### Triage labels

Default vocabulary (`needs-triage`, `ready-for-agent`, …). See [`docs/agents/triage-labels.md`](./docs/agents/triage-labels.md).

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/`. See [`docs/agents/domain.md`](./docs/agents/domain.md).

## Docs

- [`docs/tech-stack.md`](./docs/tech-stack.md) — technology decisions
- [`docs/deploy/`](./docs/deploy/) — backend → android → connect → access
- [`docs/superpowers/specs/`](./docs/superpowers/specs/) — approved feature designs

## Gotchas

- **`core/bin/` is generated** — edit `core/src/`, never `core/bin/`.
- **Do not commit secrets** — `.clasp.*.json`, credentials, deploy tokens (see `.gitignore`).
