#!/usr/bin/env bash
# Push backend/ to Google Apps Script and redeploy the existing Web App.
# Usage: ./scripts/deploy-gas.sh test|prod
set -euo pipefail

ENV="${1:-}"
if [[ "$ENV" != "test" && "$ENV" != "prod" ]]; then
  echo "Usage: $0 test|prod" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT_FILE=".clasp.${ENV}.json"
DEPLOY_CONFIG="gas-deploy.json"

if [[ ! -f "$PROJECT_FILE" ]]; then
  echo "Missing $PROJECT_FILE — copy .clasp.${ENV}.json.example and set scriptId." >&2
  exit 1
fi

if [[ ! -f "$DEPLOY_CONFIG" ]]; then
  echo "Missing $DEPLOY_CONFIG — copy gas-deploy.json.example and set deploymentId." >&2
  exit 1
fi

DEPLOYMENT_ID="$(node -e "
  const cfg = require('./${DEPLOY_CONFIG}');
  const id = cfg['${ENV}']?.deploymentId;
  if (!id || id.startsWith('PASTE_')) {
    console.error('Set ${ENV}.deploymentId in ${DEPLOY_CONFIG}');
    process.exit(1);
  }
  process.stdout.write(id);
")"

echo "Running backend logic tests..."
npm test

DESC="${ENV} $(git -C "$ROOT/.." rev-parse --short HEAD 2>/dev/null || echo local)-$(date -u +%Y%m%d%H%M%S)"
echo "Pushing to Apps Script (${ENV})..."
npx clasp push --force --project "$PROJECT_FILE"

echo "Redeploying Web App (${ENV}, deployment ${DEPLOYMENT_ID})..."
npx clasp deploy \
  --project "$PROJECT_FILE" \
  --deploymentId "$DEPLOYMENT_ID" \
  --description "$DESC"

echo "Done. ${ENV} backend deployed."
