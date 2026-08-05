# Deployment — start here

Three parts, deploy in this order:

1. **[backend.md](backend.md)** — create the Google Sheet + Apps Script Web App, set the shared
   secret, copy the `/exec` URL. (~10 min, once.)
2. **[android.md](android.md)** — build the app on a machine with the Android SDK; sideload APKs
   and/or follow **[play.md](play.md)** for Play Internal testing (volunteers).
3. **[connect.md](connect.md)** — per device: magic link **or** Settings (URL, access code, unique
   2-digit prefix); pull the plant list; do a test sale.
4. **[access.md](access.md)** — on the nursery PC, set up the Access → Sheets plant sync (import the
   VBA module, set the URL/secret env vars, wire the Form Timer). (Once, on the nursery PC.)
5. **[screenshots-ci.md](screenshots-ci.md)** — PR screenshot gallery (emulator + Maestro); not a
   merge gate. Cursor Cloud cannot run emulators.
6. **[play.md](play.md)** — Play Console Internal testing for prod (`com.nursery.scanner`) only.

## End-to-end checklist

- [ ] Sheet created with a **`Plants`** tab (header: `accession, name, group, light`) and a
      **`Users`** tab (header: `device_prefix, name, secret`).
- [ ] `Code.gs` + `shared.gs` in Apps Script (paste or `clasp push`); `appsscript.json` set.
- [ ] *(clasp)* `backend/.clasp.*.json` + `gas-deploy.json` configured; GitHub secrets set for prod CI.
- [ ] Script Property **`SHARED_SECRET`** set.
- [ ] Web App deployed (**Anyone** access); `/exec` URL copied.
- [ ] `curl` smoke-test returns `{"ok":true,...}` (include `devicePrefix` + `deviceSecret` for
      `getPlants`).
- [ ] App built (`:app:assembleProdRelease`) and installed on each device.
- [ ] *(test environment, optional)* Second Sheet + Apps Script deployed; **test** APK
      (`:app:assembleQaRelease`, "GF Nursery TEST") installed beside prod and pointed at the test URL.
- [ ] Each device: magic link **or** prefix + URL + access code; **Plants ↻** (cloud sync) succeeds.
- [ ] Test sale appears in the **`Sales`** tab.
- [ ] Nursery PC: VBA module imported, `GFRBG_SYNC_URL` / `GFRBG_SYNC_SECRET` set, Form Timer wired;
      **Sync now** writes the `Plants` tab and stamps the **`SyncStatus`** tab.

## Running the automated tests

The business logic and backend logic have tests that run with no Android SDK:

```bash
# Core business logic (Kotlin/JVM) — money math, receipt numbering, sync, export, config
cd core && gradle test

# Backend logic (auth, plant parsing, dedupe)
node --test backend/test/logic.test.js
```

Both should report success. The Android UI layer is compiled by Android Studio / `gradlew` on a
machine with the SDK. App unit tests (`:app:testQaDebugUnitTest`) cover the CI mode seam; volunteer
UI on PRs is reviewed via the [screenshot gallery](screenshots-ci.md). Manual device checks remain
per `connect.md`.
