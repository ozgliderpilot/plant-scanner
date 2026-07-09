#!/usr/bin/env bash
# Delete pr/<number>/ from the orphan ci-screenshots branch when a PR closes.
# Usage: cleanup-pr-screenshots.sh <pr-number>
set -euo pipefail

PR="${1:?pr number}"
BRANCH="ci-screenshots"
REMOTE_DIR="pr/${PR}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git config --global user.name "github-actions[bot]"
git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"

REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"

if ! git ls-remote --exit-code --heads "$REPO_URL" "$BRANCH" >/dev/null 2>&1; then
  echo "Branch ${BRANCH} does not exist; nothing to clean"
  exit 0
fi

git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$WORK/repo"

if [[ ! -d "$WORK/repo/$REMOTE_DIR" ]]; then
  echo "No ${REMOTE_DIR}/ on ${BRANCH}; nothing to clean"
  exit 0
fi

rm -rf "$WORK/repo/$REMOTE_DIR"
git -C "$WORK/repo" add -A
if git -C "$WORK/repo" diff --cached --quiet; then
  echo "Nothing to commit"
  exit 0
fi

git -C "$WORK/repo" commit -m "cleanup: remove screenshots for PR #${PR}"

attempts=0
until git -C "$WORK/repo" push origin "HEAD:${BRANCH}"; do
  attempts=$((attempts + 1))
  if [[ "$attempts" -ge 5 ]]; then
    echo "Failed to push cleanup after ${attempts} attempts" >&2
    exit 1
  fi
  git -C "$WORK/repo" pull --rebase origin "$BRANCH" || true
  sleep 2
done

echo "Removed ${REMOTE_DIR}/ from ${BRANCH}"
