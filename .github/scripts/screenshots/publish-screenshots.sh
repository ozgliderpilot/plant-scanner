#!/usr/bin/env bash
# Push gallery PNGs + manifest to the orphan ci-screenshots branch under pr/<number>/.
# Usage: publish-screenshots.sh <local-gallery-dir> <pr-number> <short-sha>
set -euo pipefail

GALLERY="${1:?gallery dir}"
PR="${2:?pr number}"
SHA="${3:?short sha}"
BRANCH="ci-screenshots"
REMOTE_DIR="pr/${PR}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git config --global user.name "github-actions[bot]"
git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"

REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"

if git ls-remote --exit-code --heads "$REPO_URL" "$BRANCH" >/dev/null 2>&1; then
  git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$WORK/repo"
else
  mkdir -p "$WORK/repo"
  git -C "$WORK/repo" init
  git -C "$WORK/repo" checkout --orphan "$BRANCH"
  git -C "$WORK/repo" remote add origin "$REPO_URL"
  # Empty orphan commit so first push has a base.
  git -C "$WORK/repo" commit --allow-empty -m "ci-screenshots branch"
fi

mkdir -p "$WORK/repo/$REMOTE_DIR"
# Copy only files listed in this run's manifest (supports partial galleries).
if [[ -f "$GALLERY/manifest.txt" ]]; then
  while IFS= read -r file || [[ -n "$file" ]]; do
    [[ -z "$file" ]] && continue
    cp "$GALLERY/$file" "$WORK/repo/$REMOTE_DIR/$file"
  done < "$GALLERY/manifest.txt"
  cp "$GALLERY/manifest.txt" "$WORK/repo/$REMOTE_DIR/manifest-${SHA}.txt"
fi

git -C "$WORK/repo" add -A
if git -C "$WORK/repo" diff --cached --quiet; then
  echo "No screenshot changes to publish"
  exit 0
fi

git -C "$WORK/repo" commit -m "screenshots: PR #${PR} @ ${SHA}"

# Concurrent PRs: pull --rebase retry loop.
attempts=0
until git -C "$WORK/repo" push -u origin "HEAD:${BRANCH}"; do
  attempts=$((attempts + 1))
  if [[ "$attempts" -ge 5 ]]; then
    echo "Failed to push ci-screenshots after ${attempts} attempts" >&2
    exit 1
  fi
  echo "Push rejected; pull --rebase and retry ($attempts)"
  git -C "$WORK/repo" pull --rebase origin "$BRANCH" || true
  sleep 2
done

echo "Published to ${BRANCH}/${REMOTE_DIR}/"
