# Deployment — start here

Three parts, deploy in this order:

1. **[backend.md](backend.md)** — create the Google Sheet + Apps Script Web App, set the shared
   secret, copy the `/exec` URL. (~10 min, once.)
2. **[android.md](android.md)** — build the app on a machine with the Android SDK and sideload the
   APK to each device. (Once per app version.)
3. **[connect.md](connect.md)** — in each device's **Settings**, enter the URL, the access code, and a
   unique 2-digit prefix; pull the plant list; do a test sale. (Once per device.)
4. **[access.md](access.md)** — on the nursery PC, set up the Access → Sheets plant sync (import the
   VBA module, set the URL/secret env vars, wire the Form Timer). (Once, on the nursery PC.)

## End-to-end checklist

- [ ] Sheet created with a **`Plants`** tab (header: `accession, name, group, light`).
- [ ] `Code.gs` + `shared.gs` pasted into Apps Script; `appsscript.json` set.
- [ ] Script Property **`SHARED_SECRET`** set.
- [ ] Web App deployed (**Anyone** access); `/exec` URL copied.
- [ ] `curl` smoke-test returns `{"ok":true,...}`.
- [ ] App built (`:app:assembleDebug`) and installed on each device.
- [ ] Each device: prefix + URL + access code entered; **Update plant list** succeeds.
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
machine with the SDK (it has no automated tests here — verify it by running the app per `connect.md`).
