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
# Skip emulator locale setprops — persist.sys.* fails on API 30 AOSP under CI and
# English UI copy in .maestro/gallery.yaml matches the default en-US image.
adb shell pm grant com.nursery.scanner.test android.permission.CAMERA || true

adb install -r apk/app-qa-debug.apk

mkdir -p maestro-out gallery
# Maestro launches with CI_MODE (see .maestro/gallery.yaml). May exit non-zero on
# mid-flow failure; still normalize partial captures.
set +e
maestro test --output maestro-out .maestro/gallery.yaml
MAESTRO_EXIT=$?
set -e

.github/scripts/screenshots/normalize-screenshots.sh maestro-out gallery "$SHORT"

# Publish + comment even on partial success (manifest may be non-empty).
if [[ -s gallery/manifest.txt ]]; then
  .github/scripts/screenshots/publish-screenshots.sh gallery "$PR_NUMBER" "$SHORT"
  .github/scripts/screenshots/comment-gallery.sh \
    "$PR_NUMBER" "$SHORT" gallery/manifest.txt \
    "$RUN_URL" \
    "$HEAD_SHA"
fi

exit "$MAESTRO_EXIT"
