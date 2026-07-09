#!/usr/bin/env bash
# Post a collapsible PR comment with the gallery from this run's manifest.
# Usage: comment-gallery.sh <pr-number> <short-sha> <manifest-path> <run-url> <commit-sha>
set -euo pipefail

PR="${1:?pr number}"
SHORT_SHA="${2:?short sha}"
MANIFEST="${3:?manifest path}"
RUN_URL="${4:?workflow run url}"
COMMIT="${5:?full commit sha}"
REPO="${GITHUB_REPOSITORY:?}"
OWNER="${REPO%%/*}"
NAME="${REPO##*/}"

RAW_BASE="https://raw.githubusercontent.com/${REPO}/ci-screenshots/pr/${PR}"

CAPTIONS=(
  "Actions"
  "Plants"
  "Sell · scan"
  "Sell · line item"
  "Sell · cart"
  "Sell · confirm"
  "Receipts"
  "Culls"
)

EXPECTED=8
COUNT=0
BODY_IMAGES=""

if [[ -f "$MANIFEST" ]]; then
  while IFS= read -r file || [[ -n "$file" ]]; do
    [[ -z "$file" ]] && continue
    COUNT=$((COUNT + 1))
    # Map 01-actions-abc1234.png → caption index 0
    idx=$((10#${file:0:2} - 1))
    caption="${CAPTIONS[$idx]:-Screen $COUNT}"
    BODY_IMAGES+=$'\n'"### ${caption}"$'\n\n'"![${caption}](${RAW_BASE}/${file})"$'\n'
  done < "$MANIFEST"
fi

if [[ "$COUNT" -eq 0 ]]; then
  STATUS="**No screenshots captured** this run."
elif [[ "$COUNT" -lt "$EXPECTED" ]]; then
  STATUS="**Partial gallery** — ${COUNT}/${EXPECTED} frames captured (flow stopped early)."
else
  STATUS="**Complete gallery** — ${COUNT}/${EXPECTED} frames."
fi

BODY=$(cat <<EOF
<!-- ci-screenshots:${SHORT_SHA} -->
<details>
<summary>CI screenshots (${COUNT}/${EXPECTED}) — ${SHORT_SHA}</summary>

${STATUS}

Commit: [\`${SHORT_SHA}\`](https://github.com/${REPO}/commit/${COMMIT}) · [Workflow run](${RUN_URL})

${BODY_IMAGES}
</details>
EOF
)

gh pr comment "$PR" --repo "$REPO" --body "$BODY"
echo "Commented on PR #${PR}"
