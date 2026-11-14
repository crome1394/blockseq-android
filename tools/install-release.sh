#!/usr/bin/env bash
# Builds the release APK (minified, ~3 MB) and installs it on the phone
# connected over USB. Signed with the release key when its env file exists
# (~/.local/share/blockseq-android/credentials.env, see AGENTS.md); otherwise
# with this computer's Android debug key, which is fine for your own phone
# but can't update, or be updated by, a released APK.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/build/outputs/apk/release
ENV="$HOME/.local/share/blockseq-android/credentials.env"
if [ -f "$ENV" ]; then
  set -a; . "$ENV"; set +a
  ./gradlew assembleRelease -q
  APK="$OUT/app-release.apk"
else
  BT="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" | sort -V | tail -1)"
  ./gradlew assembleRelease -q
  "$BT/zipalign" -f 4 "$OUT/app-release-unsigned.apk" "$OUT/app-release-aligned.apk"
  APK="$OUT/blockseq-release.apk"
  "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$APK" "$OUT/app-release-aligned.apk"
fi
adb install -r "$APK"
echo "Installed $APK"
