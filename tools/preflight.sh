#!/usr/bin/env bash
# Pre-upload check for SideKeys.
#
# Covers the failures this project has actually hit: a version code Play had
# already seen, release notes over the 500-character limit, a string added in
# one language but not the other, and code that compiled while a test was red.
# It cannot press a button on a phone -- see VERIFICATION.md for what that
# leaves uncovered.
set -uo pipefail
cd "$(dirname "$0")/.."

# apksigner and Gradle both need a JDK; the release build needs the keystore.
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
: "${SIDEKEYS_KEYSTORE_DIR:=$HOME/.android-keys/sidekeys}"
export JAVA_HOME ANDROID_HOME SIDEKEYS_KEYSTORE_DIR

FAIL=0
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAIL=1; }
warn() { printf '  \033[33mNOTE\033[0m %s\n' "$1"; }

echo "== Build, tests and lint =="
if ./gradlew --no-daemon -q testReleaseUnitTest lintRelease assembleRelease bundleRelease >/tmp/sidekeys-preflight.log 2>&1; then
  ok "compiles, unit tests pass, lint clean"
else
  bad "build/test/lint failed -- see /tmp/sidekeys-preflight.log"
  grep -E "^e: |FAILED|Error:" /tmp/sidekeys-preflight.log | head -10
fi

echo "== Version =="
VC=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
VN=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
echo "  versionName $VN, versionCode $VC"

LAST_TAG=$(git tag --sort=-v:refname | head -1)
if [ -n "$LAST_TAG" ]; then
  if [ "v$VN" = "$LAST_TAG" ]; then
    bad "versionName $VN already released as $LAST_TAG -- bump it"
  else
    ok "versionName is newer than the last tag ($LAST_TAG)"
  fi
fi

# Play rejects a version code it has seen before. Record every uploaded one.
USED=distribution/used-version-codes.txt
touch "$USED"
if grep -qx "$VC" "$USED"; then
  bad "versionCode $VC is already recorded as uploaded -- bump it"
else
  ok "versionCode $VC not recorded as used yet"
fi

echo "== Release notes =="
for f in distribution/whatsnew/whatsnew-*; do
  # Characters, not bytes: an umlaut costs two bytes but one character, and
  # Play counts characters. Measuring bytes rejects perfectly valid German.
  N=$(python3 -c "import sys,io; print(len(io.open(sys.argv[1],encoding='utf-8').read()))" "$f")
  if [ "$N" -gt 500 ]; then
    bad "$(basename "$f"): $N chars, Play allows 500"
  else
    ok "$(basename "$f"): $N/500 chars"
  fi
done

echo "== Translations =="
python3 - <<'PY'
import re, sys
def names(p):
    s = open(p, encoding="utf-8").read()
    return set(re.findall(r'<(?:string|plurals) name="([^"]+)"', s))
en = names("app/src/main/res/values/strings.xml")
de = names("app/src/main/res/values-de/strings.xml")
missing_de, missing_en = sorted(en - de), sorted(de - en)
if missing_de: print("  \033[31mFAIL\033[0m missing in German: " + ", ".join(missing_de)); sys.exit(1)
if missing_en: print("  \033[31mFAIL\033[0m missing in English: " + ", ".join(missing_en)); sys.exit(1)
print(f"  \033[32mOK\033[0m   {len(en)} strings present in both languages")
PY
[ $? -ne 0 ] && FAIL=1

echo "== Nothing secret staged =="
if git status --porcelain | awk '{print $NF}' | grep -qiE '\.jks$|\.keystore$|keystore\.properties$|\.apk$|\.aab$|^\.claude'; then
  bad "a keystore, bundle or private file is untracked/staged -- do not commit"
  git status --porcelain | awk '{print $NF}' | grep -iE '\.jks$|\.keystore$|keystore\.properties$|\.apk$|\.aab$|^\.claude'
else
  ok "no keystore, apk, aab or private file in the working tree"
fi

echo "== Artefacts =="
APK=app/build/outputs/apk/release/app-release.apk
AAB=app/build/outputs/bundle/release/app-release.aab
for f in "$APK" "$AAB"; do
  [ -f "$f" ] && ok "$(basename "$f") $(wc -c < "$f" | tr -d ' ') bytes" || bad "$f missing"
done

if [ -f "$APK" ]; then
  AAPT=$(ls "$HOME"/Library/Android/sdk/build-tools/*/aapt2 2>/dev/null | head -1)
  if [ -n "$AAPT" ]; then
    BADGE=$("$AAPT" dump badging "$APK" 2>/dev/null | head -1)
    echo "$BADGE" | grep -q "versionCode='$VC'" && ok "built artefact carries versionCode $VC" \
      || bad "artefact version does not match build.gradle.kts -- stale build"
  fi
  SIGNER=$(ls "$HOME"/Library/Android/sdk/build-tools/*/apksigner 2>/dev/null | head -1)
  if [ -n "$SIGNER" ]; then
    FP=$("$SIGNER" verify --print-certs "$APK" 2>/dev/null | grep -i "SHA-256 digest" | head -1 | awk '{print $NF}')
    EXPECTED=ce1a7fac78293fed0cb76a487f7c09fb81ea4489ad36752972279ccd8b34f9d3
    [ "$FP" = "$EXPECTED" ] && ok "signed with the release key" \
      || bad "signing key mismatch -- users could not update over their install"
  fi
fi

echo
if [ $FAIL -eq 0 ]; then
  printf '\033[32mPreflight passed.\033[0m After a successful Play upload, record the code:\n'
  printf '  echo %s >> %s\n' "$VC" "$USED"
else
  printf '\033[31mPreflight failed -- do not upload.\033[0m\n'
fi
exit $FAIL
