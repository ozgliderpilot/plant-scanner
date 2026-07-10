#!/usr/bin/env bash
# Publish gallery PNGs to ci-screenshots and post a collapsible PR comment.
# Frame captions: .maestro/gallery-frames.txt (id|caption).
# Usage: publish-and-comment.sh <gallery-dir> <pr-number> <short-sha> <run-url> <commit-sha> [frames-file]
set -euo pipefail

GALLERY="${1:?gallery dir}"
PR="${2:?pr number}"
SHA="${3:?short sha}"
RUN_URL="${4:?workflow run url}"
COMMIT="${5:?full commit sha}"
FRAMES_FILE="${6:-.maestro/gallery-frames.txt}"

REPO="${GITHUB_REPOSITORY:?}"
BRANCH="ci-screenshots"
REMOTE_DIR="pr/${PR}"
RAW_BASE="https://raw.githubusercontent.com/${REPO}/ci-screenshots/${REMOTE_DIR}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- captions from frames file ---
declare -A CAPTIONS=()
EXPECTED=0
while IFS='|' read -r frame caption || [[ -n "${frame:-}" ]]; do
  [[ -z "${frame:-}" || "$frame" =~ ^# ]] && continue
  CAPTIONS["$frame"]="$caption"
  EXPECTED=$((EXPECTED + 1))
done < "$FRAMES_FILE"

# --- publish to orphan branch ---
git config --global user.name "github-actions[bot]"
git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"

REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${REPO}.git"

if git ls-remote --exit-code --heads "$REPO_URL" "$BRANCH" >/dev/null 2>&1; then
  git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$WORK/repo"
else
  mkdir -p "$WORK/repo"
  git -C "$WORK/repo" init
  git -C "$WORK/repo" checkout --orphan "$BRANCH"
  git -C "$WORK/repo" remote add origin "$REPO_URL"
  git -C "$WORK/repo" commit --allow-empty -m "ci-screenshots branch"
fi

mkdir -p "$WORK/repo/$REMOTE_DIR"
if [[ -f "$GALLERY/manifest.txt" ]]; then
  while IFS= read -r file || [[ -n "$file" ]]; do
    [[ -z "$file" ]] && continue
    cp "$GALLERY/$file" "$WORK/repo/$REMOTE_DIR/$file"
  done < "$GALLERY/manifest.txt"
  cp "$GALLERY/manifest.txt" "$WORK/repo/$REMOTE_DIR/manifest-${SHA}.txt"
fi

git -C "$WORK/repo" add -A
if ! git -C "$WORK/repo" diff --cached --quiet; then
  git -C "$WORK/repo" commit -m "screenshots: PR #${PR} @ ${SHA}"
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
else
  echo "No screenshot changes to publish"
fi

# --- PR comment ---
COUNT=0
ENTRIES=()
if [[ -f "$GALLERY/manifest.txt" ]]; then
  while IFS= read -r file || [[ -n "$file" ]]; do
    [[ -z "$file" ]] && continue
    COUNT=$((COUNT + 1))
    # 01-actions-abc1234.png → 01-actions
    if [[ "$file" =~ ^(.+)-[0-9a-f]{7}\.png$ ]]; then
      frame="${BASH_REMATCH[1]}"
    else
      frame="${file%.png}"
    fi
    caption="${CAPTIONS[$frame]:-Screen $COUNT}"
    ENTRIES+=("${caption}|${RAW_BASE}/${file}")
  done < "$GALLERY/manifest.txt"
fi

if [[ "$COUNT" -eq 0 ]]; then
  STATUS="**No screenshots captured** this run."
elif [[ "$COUNT" -lt "$EXPECTED" ]]; then
  STATUS="**Partial gallery** — ${COUNT}/${EXPECTED} frames captured (flow stopped early)."
else
  STATUS="**Complete gallery** — ${COUNT}/${EXPECTED} frames."
fi

BODY_IMAGES=""
if [[ "$COUNT" -gt 0 ]]; then
  BODY_IMAGES+=$'\n<table>\n'
  col=0
  for entry in "${ENTRIES[@]}"; do
    caption="${entry%%|*}"
    url="${entry#*|}"
    if [[ "$col" -eq 0 ]]; then
      BODY_IMAGES+="<tr>"$'\n'
    fi
    BODY_IMAGES+="<td align=\"center\" width=\"33%\"><p><b>${caption}</b></p><img src=\"${url}\" alt=\"${caption}\" /></td>"$'\n'
    col=$((col + 1))
    if [[ "$col" -eq 3 ]]; then
      BODY_IMAGES+="</tr>"$'\n'
      col=0
    fi
  done
  if [[ "$col" -ne 0 ]]; then
    while [[ "$col" -lt 3 ]]; do
      BODY_IMAGES+="<td width=\"33%\"></td>"$'\n'
      col=$((col + 1))
    done
    BODY_IMAGES+="</tr>"$'\n'
  fi
  BODY_IMAGES+="</table>"$'\n'
fi

BODY=$(cat <<EOF
<!-- ci-screenshots:${SHA} -->
<details>
<summary>CI screenshots (${COUNT}/${EXPECTED}) — ${SHA}</summary>

${STATUS}

Commit: [\`${SHA}\`](https://github.com/${REPO}/commit/${COMMIT}) · [Workflow run](${RUN_URL})

${BODY_IMAGES}
</details>
EOF
)

gh pr comment "$PR" --repo "$REPO" --body "$BODY"
echo "Commented on PR #${PR}"
