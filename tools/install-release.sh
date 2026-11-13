#!/usr/bin/env bash
# Builds the release APK (minified, ~3 MB), signs it with this computer's
# Android debug key, and installs it on the phone connected over USB.
# For your own phone; a build for others needs a proper release key.
set -euo pipefail
cd "$(dirname "$0")/.."
BT="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" | sort -V | tail -1)"
KS="$HOME/.android/debug.keystore"
OUT=app/build/outputs/apk/release
./gradlew assembleRelease -q
"$BT/zipalign" -f 4 "$OUT/app-release-unsigned.apk" "$OUT/app-release-aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/blockseq-release.apk" "$OUT/app-release-aligned.apk"
adb install -r "$OUT/blockseq-release.apk"
echo "Installed $OUT/blockseq-release.apk"
