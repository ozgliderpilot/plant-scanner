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
  "Sell · scan"
  "Sell · line item"
  "Sell · cart"
  "Sell · confirm"
  "History"
  "Receipts"
  "Receipt detail"
  "Cull · scan"
  "Cull · enter info"
  "Cull · success"
  "Culls"
  "Plants"
)

EXPECTED=13
COUNT=0
# Collect "caption|url" rows, then render as HTML table (3 per row) for GitHub comments.
ENTRIES=()

if [[ -f "$MANIFEST" ]]; then
  while IFS= read -r file || [[ -n "$file" ]]; do
    [[ -z "$file" ]] && continue
    COUNT=$((COUNT + 1))
    # Map 01-actions-abc1234.png → caption index 0
    idx=$((10#${file:0:2} - 1))
    caption="${CAPTIONS[$idx]:-Screen $COUNT}"
    ENTRIES+=("${caption}|${RAW_BASE}/${file}")
  done < "$MANIFEST"
fi

if [[ "$COUNT" -eq 0 ]]; then
  STATUS="**No screenshots captured** this run."
elif [[ "$COUNT" -lt "$EXPECTED" ]]; then
  STATUS="**Partial gallery** — ${COUNT}/${EXPECTED} frames captured (flow stopped early)."
else
  STATUS="**Complete gallery** — ${COUNT}/${EXPECTED} frames."
fi

# GitHub markdown collapses images vertically; an HTML table keeps three thumbs per row.
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
