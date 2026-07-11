# Native Android + Compose stack

## Context

The app targets Android only, sideloaded on a couple of trusted nursery devices. Connectivity is offline-first; barcode scanning must work immediately without Play Services downloads. Cross-platform portability will never be used.

## Decision

Use Kotlin + Jetpack Compose, CameraX, Room (SQLite), DataStore, and bundled ML Kit restricted to Code 128 (with cheap 1D fallbacks). Room over mobile NoSQL: receipts are relational and the cardinal rule is transactional “nothing lost.”

## Considered Options

- **Expo / React Native** — familiar TS, but ML Kit only via a third-party bridge on the riskiest path; Android-only wastes the cross-platform payoff.
- **Flutter** — solid scanner story, but Dart is a new language for no platform gain.
- **Kotlin Multiplatform / PWA** — overkill or unreliable for barcode + offline install on Android-only.
- **Mobile NoSQL (Realm / ObjectBox / Couchbase)** — Realm/Atlas Device SDK is EOL; built-in sync is useless when the destination is Google Sheets.

## Consequences

The team stays on first-party Android tooling. There is no iOS or web client path without a rewrite.
