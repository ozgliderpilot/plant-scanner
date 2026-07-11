# Native Android + Compose stack

Android-only, sideloaded on a couple of trusted devices — so we use Kotlin + Jetpack Compose, CameraX, Room (SQLite), DataStore, and bundled ML Kit (Code 128–first) rather than cross-platform frameworks. Room wins over mobile NoSQL because receipts are relational and the cardinal rule is transactional “nothing lost”; Realm/Atlas Device SDK is EOL, and ObjectBox/Couchbase’s built-in sync is useless when the destination is Google Sheets.

## Considered Options

- **Expo / React Native** — familiar TS, but ML Kit only via a third-party bridge on the riskiest path; Android-only wastes the cross-platform payoff.
- **Flutter** — solid scanner story, but Dart is a new language for no platform gain.
- **Kotlin Multiplatform / PWA** — overkill or unreliable for barcode + offline install on Android-only.
