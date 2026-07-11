#!/usr/bin/env bash
# Build a release APK and install it on a connected device via adb.
# Usage: ./scripts/install-app.sh test|prod
#
#   test  → qa flavor  ("Nursery TEST", com.nursery.scanner.test)
#   prod  → prod flavor ("Nursery",     com.nursery.scanner)
set -euo pipefail
# Git Bash on Windows: ignore CR from accidental CRLF checkouts.
(set -o igncr) 2>/dev/null && set -o igncr

ENV="${1:-}"
if [[ "$ENV" != "test" && "$ENV" != "prod" ]]; then
  echo "Usage: $0 test|prod" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Map user-facing "test" to the Gradle flavor name "qa" (AGP reserves "test").
if [[ "$ENV" == "test" ]]; then
  FLAVOR="qa"
else
  FLAVOR="prod"
fi
FLAVOR_CAP="$(printf '%s' "$FLAVOR" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
TASK=":app:assemble${FLAVOR_CAP}Release"
APK="app/build/outputs/apk/${FLAVOR}/release/app-${FLAVOR}-release.apk"

# Prefer Android Studio's bundled JBR when present (Windows Git Bash path).
if [[ -z "${JAVA_HOME:-}" ]]; then
  CANDIDATES=(
    "/c/Program Files/Android/Android Studio/jbr"
    "/mnt/c/Program Files/Android/Android Studio/jbr"
    "$HOME/android-studio/jbr"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  )
  for candidate in "${CANDIDATES[@]}"; do
    if [[ -d "$candidate" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

echo "==> Starting adb…"
adb start-server
echo "Turn on wireless debugging on the device"
adb wait-for-device
echo "    Device: $(adb get-serialno)"

echo "==> Building $TASK…"
# On Windows/Git Bash prefer the .bat wrapper; plain ./gradlew is for Unix.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    cmd.exe //c gradlew.bat "$TASK"
    ;;
  *)
    if [[ -x ./gradlew ]]; then
      ./gradlew "$TASK"
    else
      sh ./gradlew "$TASK"
    fi
    ;;
esac

if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK" >&2
  exit 1
fi

echo "==> Installing $APK…"
adb install -r "$APK"
echo "==> Done ($ENV)."
