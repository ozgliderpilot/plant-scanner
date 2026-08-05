#!/usr/bin/env bash
# One-time: create an Android upload keystore + keystore.properties for Play releases.
# Never commit the outputs (gitignored). Back them up offline.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

KEYSTORE="${UPLOAD_KEYSTORE_PATH:-$ROOT/upload-keystore.jks}"
PROPS="${KEYSTORE_PROPERTIES_PATH:-$ROOT/keystore.properties}"
ALIAS="${UPLOAD_KEY_ALIAS:-upload}"
VALIDITY_DAYS="${UPLOAD_KEY_VALIDITY_DAYS:-10000}"

if [[ -f "$KEYSTORE" || -f "$PROPS" ]]; then
  echo "Refusing to overwrite existing files:"
  [[ -f "$KEYSTORE" ]] && echo "  $KEYSTORE"
  [[ -f "$PROPS" ]] && echo "  $PROPS"
  echo "Move or delete them first if you intentionally want a new key."
  exit 1
fi

KEYTOOL="${KEYTOOL:-}"
if [[ -z "$KEYTOOL" ]]; then
  if command -v keytool >/dev/null 2>&1; then
    KEYTOOL="$(command -v keytool)"
  elif [[ -x "/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" ]]; then
    KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe"
  else
    echo "keytool not found. Set KEYTOOL to your JDK keytool path."
    exit 1
  fi
fi

STORE_PASS="${UPLOAD_STORE_PASSWORD:-$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)}"
KEY_PASS="${UPLOAD_KEY_PASSWORD:-$STORE_PASS}"

"$KEYTOOL" -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "${UPLOAD_DNAME:-CN=Nursery Upload, OU=Nursery, O=Nursery, L=Unknown, ST=Unknown, C=AU}"

# storeFile path relative to the app/ module (Gradle signingConfig resolves against app/)
REL_FROM_APP="../$(basename "$KEYSTORE")"
if [[ "$(dirname "$KEYSTORE")" != "$ROOT" ]]; then
  REL_FROM_APP="$KEYSTORE"
fi

cat >"$PROPS" <<EOF
storeFile=$REL_FROM_APP
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo
echo "Created:"
echo "  $KEYSTORE"
echo "  $PROPS"
echo
echo "Back these up offline. Do not commit them."
echo "Build a Play AAB with: ./gradlew :app:bundleProdRelease"
