#!/usr/bin/env bash
# Normalize Maestro screenshots into the gallery filename contract and write a per-run manifest.
# Frame list: .maestro/gallery-frames.txt (id|caption).
# Usage: normalize-screenshots.sh <maestro-output-dir> <dest-dir> <short-sha> [frames-file]
set -euo pipefail

SRC="${1:?maestro output dir}"
DEST="${2:?destination dir}"
SHA="${3:?short sha}"
FRAMES_FILE="${4:-.maestro/gallery-frames.txt}"

mkdir -p "$DEST"
MANIFEST="$DEST/manifest.txt"
: > "$MANIFEST"

if [[ ! -f "$FRAMES_FILE" ]]; then
  echo "frames file missing: $FRAMES_FILE" >&2
  exit 1
fi

# Maestro writes takeScreenshot names under the workspace or --output dir.
while IFS='|' read -r frame caption || [[ -n "${frame:-}" ]]; do
  [[ -z "${frame:-}" || "$frame" =~ ^# ]] && continue
  found="$(find "$SRC" -type f -name "${frame}.png" | head -n 1 || true)"
  if [[ -z "$found" ]]; then
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
done < "$FRAMES_FILE"

echo "manifest written: $MANIFEST"
