#!/usr/bin/env bash
# Normalize Maestro screenshots into the gallery filename contract and write a per-run manifest.
# Usage: normalize-screenshots.sh <maestro-output-dir> <dest-dir> <short-sha>
set -euo pipefail

SRC="${1:?maestro output dir}"
DEST="${2:?destination dir}"
SHA="${3:?short sha}"

mkdir -p "$DEST"
MANIFEST="$DEST/manifest.txt"
: > "$MANIFEST"

# Expected frames in gallery order (basename without extension as Maestro wrote them).
FRAMES=(
  "01-actions"
  "02-plants"
  "03-sell-scan"
  "04-sell-line"
  "05-sell-cart"
  "06-sell-confirm"
  "07-receipts"
  "08-culls"
)

# Maestro writes takeScreenshot names under ~/.maestro/tests/... or the --output dir.
# Search recursively for each frame name as .png.
for frame in "${FRAMES[@]}"; do
  found="$(find "$SRC" -type f -name "${frame}.png" | head -n 1 || true)"
  if [[ -z "$found" ]]; then
    # Also accept names Maestro may suffix.
    found="$(find "$SRC" -type f -name "${frame}*.png" | head -n 1 || true)"
  fi
  if [[ -n "$found" ]]; then
    out="${frame}-${SHA}.png"
    cp "$found" "$DEST/$out"
    echo "$out" >> "$MANIFEST"
    echo "captured $out"
  else
    echo "missing $frame (partial gallery)" >&2
  fi
done

echo "manifest written: $MANIFEST"
