# Build & install the Android app

The app is built once on a computer with the Android SDK, then sideloaded onto each device. No Play
Store. (This build machine has no Android SDK, so the app ships as source — build it where the SDK is.)

## Prerequisites

- **Android Studio** (latest stable; it bundles the right JDK 17 and SDK manager).
  Or the command-line SDK + **JDK 17** with `JAVA_HOME` pointing at it.
- An Android phone/tablet running **Android 8.0 (API 26) or newer** with a camera.
- A USB cable (for `adb`), or any way to copy an `.apk` to the device.

The project already contains a Gradle wrapper (`gradlew` / `gradlew.bat`, Gradle 8.9) and pins all
versions in `gradle/libs.versions.toml`. The pure-logic `core` build is pulled in automatically as a
Gradle composite build — you do not build it separately.

## Option A — Android Studio (easiest)

1. **File → Open** and select the project root (`plant-scanner`).
2. Let Gradle sync. If prompted to install **SDK Platform 34 / build-tools**, accept.
3. Plug in the device, enable **Developer options → USB debugging**, accept the RSA prompt.
4. Pick the device in the toolbar and press **Run ▶**. Studio builds, installs, and launches it.

## Product flavors: production vs test

The app ships in **two flavors** so a **test** copy can be installed *next to* the production copy on
the **same device** and run safely without touching live data:

| Flavor | Variant task           | applicationId               | Launcher label  | Icon  |
|--------|------------------------|-----------------------------|-----------------|-------|
| `prod` | `assembleProdRelease`  | `com.nursery.scanner`       | **Nursery**     | green |
| `qa`   | `assembleQaRelease`    | `com.nursery.scanner.test`  | **Nursery TEST**| red   |

Because the two installs have **different `applicationId`s**, Android keeps their local storage (the
Room database and DataStore settings) completely separate — receipts and the pending-export queue in
the test app never touch production and vice versa. The flavor is named `qa` (not `test`) only because
the Android Gradle plugin reserves flavor names starting with `test`; the package id, label, and icon
all say "test" so volunteers can't confuse them.

**The only difference between the two builds is package identity + label/icon.** Both are pointed at
their backend the same way — through the app's **Settings** screen at runtime (`connect.md`). Nothing
is baked in. To isolate the cloud side too, point the test install at a **separate test backend +
Sheet** (see [backend.md → Standing up a test deployment](backend.md)).

## Option B — Command line (release APKs)

From the project root:

```bash
# Windows
gradlew.bat :app:assembleProdRelease :app:assembleQaRelease
# macOS/Linux
./gradlew :app:assembleProdRelease :app:assembleQaRelease
```

The APKs land at:

```
app/build/outputs/apk/prod/release/app-prod-release.apk   # production — "Nursery"
app/build/outputs/apk/qa/release/app-qa-release.apk       # test       — "Nursery TEST"
```

Install **both** on one device (they coexist — neither replaces the other):

```bash
adb install -r app/build/outputs/apk/prod/release/app-prod-release.apk
adb install -r app/build/outputs/apk/qa/release/app-qa-release.apk
```

…or copy each `.apk` to the device (email/Drive/USB) and tap it. The device will ask to allow
**"Install unknown apps"** for the app you opened it from — allow once.

> These release APKs are **signed with the auto-generated debug keystore** (configured in
> `app/build.gradle.kts`) so they install for sideloading out of the box — no keystore secret to
> manage — while still being proper non-debuggable **release** builds (not the developer `debug`
> build). For Play Store / wider distribution, replace that `signingConfig` with a real release
> keystore. A plain debug build is still available as `:app:assembleProdDebug` if you want it.

## First launch

1. Open **Nursery**.
2. On first scan the app asks for **camera permission** — tap **Allow camera → Allow**.
3. Configure the device once: bottom tab **Sync → Settings** (see `connect.md`).

## Troubleshooting

- **`SDK location not found`** → in Studio it's automatic; on CLI create `local.properties` with
  `sdk.dir=/path/to/Android/Sdk` (Studio writes this for you when you open the project once).
- **Gradle/JDK error** → ensure JDK 17 is used (`java -version`). Studio: *Settings → Build Tools →
  Gradle → Gradle JDK = 17*.
- **Version suggestions** → if Studio flags a newer AGP/Kotlin, the pinned versions in
  `libs.versions.toml` are a known-good set; you can accept upgrades but it's not required.
- **Camera won't scan** → labels are **Code 128**; ensure good light and hold steady. The scanner is
  restricted to Code 128 on purpose.
