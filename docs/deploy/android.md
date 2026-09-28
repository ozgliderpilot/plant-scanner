# Build & install the Android app

The app is built on a computer with the Android SDK. Volunteers can get **prod** via
[Play Internal testing](play.md), or you can still **sideload** APKs (below). Keep **qa**
sideloaded only — it is not published to Play.

## Prerequisites

- **Android Studio** (latest stable; it bundles the right JDK 17 and SDK manager).
  Or the command-line SDK + **JDK 17** with `JAVA_HOME` pointing at it.
- An Android phone/tablet running **Android 6.0 (API 23) or newer** with a camera.
  Marshmallow devices should **sideload** (Play Internal / Play services may no longer
  reach them). CameraX + ML Kit on 2015-era Camera2 hardware is unproven — if the
  preview is black, use **Type number instead**.
- A USB cable (for `adb`), or any way to copy an `.apk` to the device.

The project already contains a Gradle wrapper (`gradlew` / `gradlew.bat`, Gradle 8.9) and pins all
versions in `gradle/libs.versions.toml`. The pure-logic `core` build is pulled in automatically as a
Gradle composite build — you do not build it separately.

## Option A — Android Studio (easiest)

1. **File → Open** and select the project root (`plant-scanner`).
2. Let Gradle sync. If prompted to install **SDK Platform 36 / build-tools**, accept.
3. Plug in the device, enable **Developer options → USB debugging**, accept the RSA prompt.
4. Pick the device in the toolbar and press **Run ▶**. Studio builds, installs, and launches it.

## Product flavors: production vs test

The app ships in **two flavors** so a **test** copy can be installed *next to* the production copy on
the **same device** and run safely without touching live data:

| Flavor | Variant task           | applicationId               | Launcher label  | Icon  |
|--------|------------------------|-----------------------------|-----------------|-------|
| `prod` | `assembleProdRelease`  | `com.nursery.scanner`       | **GF Nursery**      | green |
| `qa`   | `assembleQaRelease`    | `com.nursery.scanner.test`  | **GF Nursery TEST** | red   |

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
app/build/outputs/apk/prod/release/app-prod-release.apk   # production — "GF Nursery"
app/build/outputs/apk/qa/release/app-qa-release.apk       # test       — "GF Nursery TEST"
```

Install **both** on one device (they coexist — neither replaces the other):

```bash
adb install -r app/build/outputs/apk/prod/release/app-prod-release.apk
adb install -r app/build/outputs/apk/qa/release/app-qa-release.apk
```

…or copy each `.apk` to the device (email/Drive/USB) and tap it. The device will ask to allow
**"Install unknown apps"** for the app you opened it from — allow once.

> Without a Play upload keystore, release APKs are **signed with the debug keystore** so they
> sideload out of the box. For Play Internal testing, create an upload keystore and build an AAB —
> see [play.md](play.md). A plain debug build is still available as `:app:assembleProdDebug`.

## First launch

1. Open **GF Nursery**.
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
  restricted to Code 128 on purpose. On Android 6–7 a black preview is possible — use
  **Type number instead**.
