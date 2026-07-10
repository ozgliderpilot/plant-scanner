#!/usr/bin/env bash
# Run the Maestro gallery on an already-booted emulator, then normalize/publish/comment.
# Invoked as a *single* command from android-emulator-runner (that action runs each
# script line in a fresh /usr/bin/sh -c, so multi-line inline scripts lose state).
#
# Usage: run-gallery.sh <head-sha> <pr-number> <run-url>
set -euo pipefail

HEAD_SHA="${1:?head sha}"
PR_NUMBER="${2:?pr number}"
RUN_URL="${3:?actions run url}"
SHORT="$(echo "$HEAD_SHA" | cut -c1-7)"

adb wait-for-device
# Cached AVDs (force-avd-creation: false) can retain a prior qaDebug install signed
# with a different debug keystore → INSTALL_FAILED_UPDATE_INCOMPATIBLE on -r.
adb uninstall com.nursery.scanner.test >/dev/null 2>&1 || true
adb install -r apk/app-qa-debug.apk
adb shell pm grant com.nursery.scanner.test android.permission.CAMERA || true

mkdir -p maestro-out gallery
# Maestro launches with CI_MODE (see .maestro/gallery.yaml). May exit non-zero on
# mid-flow failure; still normalize partial captures.
# takeScreenshot paths in gallery.yaml write under ./maestro-out/ (workspace-relative).
# Maestro 1.39.9 has no --test-output-dir; --output is the JUnit report path only.
set +e
maestro test --format JUNIT --output maestro-out/report.xml .maestro/gallery.yaml
MAESTRO_EXIT=$?
set -e

.github/scripts/screenshots/normalize-screenshots.sh maestro-out gallery "$SHORT"

if [[ -s gallery/manifest.txt ]]; then
  .github/scripts/screenshots/publish-and-comment.sh \
    gallery "$PR_NUMBER" "$SHORT" "$RUN_URL" "$HEAD_SHA"
fi

exit "$MAESTRO_EXIT"
