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

## Option B — Command line (debug APK)

From the project root:

```bash
# Windows
gradlew.bat :app:assembleDebug
# macOS/Linux
./gradlew :app:assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

…or copy `app-debug.apk` to the device (email/Drive/USB) and tap it. The device will ask to allow
**"Install unknown apps"** for the app you opened it from — allow once.

> A debug APK is unsigned-for-store but fine for sideloading. For a couple of trusted devices you do
> **not** need a release keystore. (If you ever want one: `:app:assembleRelease` after configuring a
> signing config — out of scope here.)

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
