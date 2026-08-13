#!/usr/bin/env bash
# Veröffentlicht SideKeys auf GitHub (öffentliches Repo + Release mit APK).
# Voraussetzung: einmalig `gh auth login` ausführen.
set -euo pipefail

REPO_NAME="${1:-sidekeys-hibreak}"
cd "$(dirname "$0")"

echo "==> Prüfe GitHub-Login…"
gh auth status >/dev/null 2>&1 || { echo "Bitte zuerst: gh auth login"; exit 1; }

echo "==> Erstelle öffentliches Repo '$REPO_NAME' und pushe main…"
gh repo create "$REPO_NAME" \
  --public \
  --source=. \
  --remote=origin \
  --description="Belegt die Seitentasten des Bigme HiBreak Pro (E-Ink-Phone) frei mit Funktionen — Assistant, Wallet, App-Start u. v. m." \
  --push

echo "==> Baue frische signierte Release-APK…"
export SIDEKEYS_KEYSTORE_DIR="${SIDEKEYS_KEYSTORE_DIR:-$HOME/.android-keys/sidekeys}"
./gradlew --quiet assembleRelease
cp app/build/outputs/apk/release/app-release.apk SideKeys-release.apk

echo "==> Erstelle Release v1.0.1 und hänge die APK an…"
gh release create v1.0.1 SideKeys-release.apk \
  --title "SideKeys v1.0.1" \
  --notes-file RELEASE_NOTES.md

echo "==> Fertig:"
gh repo view --web >/dev/null 2>&1 || true
gh repo view --json url --jq .url
