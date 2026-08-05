# Google Play — Internal testing (volunteers only)

Private distribution for nursery volunteers. **Do not** publish to production; Internal testing
avoids the closed-test gate required for production on many personal developer accounts.

Package: **`com.nursery.scanner`** (prod flavor only). Keep **`com.nursery.scanner.test`**
sideloaded for QA — do not upload it to Play.

## Checklist

- [ ] Draft app created in Play Console (`com.nursery.scanner`)
- [ ] Upload keystore generated and backed up (see below)
- [ ] First signed AAB uploaded to **Internal testing**
- [ ] Console blockers cleared (Data safety, privacy policy, content rating, …)
- [ ] Volunteer Google accounts added as internal testers; opt-in link shared
- [ ] *(optional)* GitHub secrets set for [Play release CI](../../.github/workflows/play-release.yml)
      (upload keystore + `PLAY_SERVICE_ACCOUNT_JSON`)

## 1. Create the Play Console app

1. Open [Google Play Console](https://play.google.com/console) (you already have a developer account).
2. **Create app** → name **GF Nursery**, default language, app (not game), **Free**.
3. Accept the declarations Play shows for a new app.
4. Confirm the package name will be **`com.nursery.scanner`** (fixed in
   [`app/build.gradle.kts`](../../app/build.gradle.kts) — you cannot change it after the first
   upload).
5. On first AAB upload, enroll in **Play App Signing** (recommended default). Google holds the app
   signing key; you keep the **upload** keystore created in step 2 below.

## 2. Upload keystore (one-time, local)

Never commit `*.jks`, `*.keystore`, or `keystore.properties` (already gitignored).

From the repo root (Git Bash / macOS / Linux):

```bash
./scripts/create-upload-keystore.sh
```

This writes:

- `upload-keystore.jks` — upload key (back up offline)
- `keystore.properties` — passwords + paths for Gradle (back up with the keystore)

Sideload release APKs still work without these files: Gradle falls back to the debug keystore.

## 3. Build the Play AAB (manual)

Install SDK Platform **36** if prompted. Then:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:bundleProdRelease
```

Artifact:

```
app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

Optional version override (Play requires a higher `versionCode` on every new upload). The same
`versionName` is baked into the AAB and shown on History as `Version {versionName}`
(qa adds a `-test` suffix):

```bash
./gradlew :app:bundleProdRelease -PVERSION_CODE=2 -PVERSION_NAME=1.0.1
```

Or set `VERSION_CODE` / `VERSION_NAME` in the environment (used by Play release CI).

## 4. First Internal testing upload

1. Play Console → your app → **Testing → Internal testing** → **Create new release**.
2. Upload `app-prod-release.aab`. Accept **Play App Signing** if prompted.
3. Clear whatever Play blocks before the release can roll out. Typical for this app:

   | Prompt | Suggested answers (adjust if your practice differs) |
   |--------|-----------------------------------------------------|
   | **Privacy policy** | Public HTTPS URL to [`docs/privacy-policy.html`](../privacy-policy.html) (or the Markdown twin). Host via GitHub Pages, raw+Pages, Drive “anyone with link”, or the nursery site. |
   | **Data safety** | Collects: none or minimal (scanned codes + sale/cull data you already put in Sheets). No shared with third parties for advertising. Data stays in your Sheet backend. |
   | **Camera permission** | Declare barcode scanning for plant labels (core feature). |
   | **Content rating** | Questionnaire → typically Everyone / low. |
   | **Ads / news / COVID** | No ads; not a news app; etc. |

4. **Review and roll out** the internal release (usually live within minutes).

## 5. Add volunteer testers

1. **Testing → Internal testing → Testers** → create an email list.
2. Add each volunteer’s **Google account** email (the one signed into Play on their device).
3. Copy the **opt-in link** and send it to volunteers.
4. Each volunteer opens the link while signed into that Google account → accepts → installs **GF Nursery** from Play.

Cap: **100** internal testers. For more later, use a closed testing track (still not production).

## 6. Play release CI

Workflow: [`.github/workflows/play-release.yml`](../../.github/workflows/play-release.yml).

**Actions → Play release → Run workflow.** Inputs: `version_code`, `version_name`. Builds a signed
prod AAB, keeps it as a workflow artifact, and **deploys it to Internal testing** (not production).

### Secrets — signing

| Secret | Value |
|--------|--------|
| `PLAY_UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` (Git Bash: `base64 -w0` may be `base64` without `-w0` on macOS) |
| `PLAY_UPLOAD_STORE_PASSWORD` | from `keystore.properties` |
| `PLAY_UPLOAD_KEY_ALIAS` | usually `upload` |
| `PLAY_UPLOAD_KEY_PASSWORD` | from `keystore.properties` |

### Secrets — Play API deploy

| Secret | Value |
|--------|--------|
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON key of a Google Cloud service account that can call the Play Developer API |

One-time setup for the service account:

1. In [Google Cloud Console](https://console.cloud.google.com/), create (or pick) a project →
   **IAM & Admin → Service accounts → Create**.
2. Open the account → **Keys → Add key → JSON**. Download the file; paste its entire contents into
   the GitHub secret `PLAY_SERVICE_ACCOUNT_JSON` (do not commit the file).
3. In [Play Console](https://play.google.com/console) → **Users and permissions → Invite user** →
   invite the service account email (`…@….iam.gserviceaccount.com`).
4. Grant at least **Release to testing tracks** (or **Admin** for simplicity on a private app) for
   `com.nursery.scanner`. Accept the invite if prompted.
5. Link the Cloud project to Play if Console asks (Play Console → **API access** / developer
   account settings — follow the on-screen “link Google Cloud project” steps).

Without the signing secrets, the workflow fails rather than signing with the debug key. Without
`PLAY_SERVICE_ACCOUNT_JSON`, the deploy step fails after the AAB is built (artifact is still
available to upload by hand).

## Sideload vs Play

| Path | Flavor | Signing | Install |
|------|--------|---------|---------|
| Sideload (today) | prod + qa | debug keystore if no `keystore.properties` | APK via adb / file copy |
| Play Internal | **prod only** | upload keystore | Play opt-in link |

See also [android.md](android.md) for sideload builds.
