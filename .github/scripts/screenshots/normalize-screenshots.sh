#!/usr/bin/env bash
# Normalize Maestro screenshots into the gallery filename contract and write a per-run manifest.
# Frame list: .maestro/gallery-frames.txt (id|caption). Manifest lines: id|filename.png
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

while IFS='|' read -r frame _ || [[ -n "${frame:-}" ]]; do
  [[ -z "${frame:-}" || "$frame" =~ ^# ]] && continue
  found=""
  if [[ -f "$SRC/${frame}.png" ]]; then
    found="$SRC/${frame}.png"
  else
    # Maestro may nest under --output; take first exact basename match.
    found="$(find "$SRC" -type f -name "${frame}.png" | head -n 1 || true)"
  fi
  if [[ -n "$found" ]]; then
    out="${frame}-${SHA}.png"
    cp "$found" "$DEST/$out"
    echo "${frame}|${out}" >> "$MANIFEST"
    echo "captured $out"
  else
    echo "missing $frame (partial gallery)" >&2
  fi
done < "$FRAMES_FILE"

echo "manifest written: $MANIFEST"
