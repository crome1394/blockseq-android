# AGENTS.md

Guidance for AI coding agents (and people) working on Blockseq for Android.
Read [README.md](README.md) for what the app does and
[ARCHITECTURE.md](ARCHITECTURE.md) for how it works. The server lives in a
separate repo, [blockseq](https://github.com/crome1394/blockseq); its
ARCHITECTURE.md documents the API this app uses.

## What this is

A native Kotlin + Jetpack Compose client for a self-hosted Blockseq server.
The owner uses it on a Pixel phone over Tailscale, mainly to read notes, with
full editing. **Data safety and compatibility with the web app matter more
than features**: the phone and the browser edit the same pages.

## Build, run, test

```sh
./gradlew assembleDebug                         # build (app id app.blockseq.android.debug)
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                     # unit tests
tools/demo-server.sh                            # throwaway server + adb reverse
deno run -A tools/decrypt-check.ts http://127.0.0.1:3970 demo-server demo-secret "Garden plan"
```

- Test on a real phone against `tools/demo-server.sh` (made-up notes, port
  3970, server password `demo-server`, encryption password `demo-secret`).
  Pair the app with `http://localhost:3970`. Never test against the owner's
  real server or notes. Test with the debug build: it installs beside the
  owner's app (`app.blockseq.android.debug`, "Blockseq debug"), so never
  `pm clear` or uninstall `app.blockseq.android`. Uninstalling the debug app
  afterwards also removes any test widget it placed.
- The home screen holds the owner's private widgets: never print or keep
  full `uiautomator dump` text or screenshots of it; filter to Blockseq's
  own nodes and crop.
- Compose test tags double as view ids (`testTagsAsResourceId`), so
  `adb shell uiautomator dump` can find controls (`server`, `password`,
  `pair`, `tab-journals`, `block`, `block-editor`, `done`, `keep-mine`, …).
  Dialogs are separate windows: find their fields by label text.
- Finish with the unit tests passing, a clean build, and the change checked
  on the phone.

## Releases

Releases are GitHub Releases with the APK attached, signed with the
maintainer's release key. The key and an env file with its settings live
outside the repo (`~/.local/share/blockseq-android/`; keep a backup: phones
only accept updates signed with the same key).

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`; move the
   CHANGELOG entries under the new version.
2. Commit and tag **before** building, so the APK is built from the tag.
3. Build, check, publish:

```sh
git commit -am "0.1.1: …" && git tag v0.1.1
set -a; . ~/.local/share/blockseq-android/credentials.env; set +a
./gradlew clean :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
cp app/build/outputs/apk/release/app-release.apk Blockseq-Android-0.1.1.apk
git push origin master v0.1.1
gh release create v0.1.1 Blockseq-Android-0.1.1.apk --title "0.1.1" --notes-file notes.md
```

| | |
|---|---|
| Tag | `v<versionName>` |
| Asset | `Blockseq-Android-<versionName>.apk` |
| Signing cert SHA-256 | `a0c2385528acc1fbe5563bf31fbd70b46d2d89627e60e566bdd74387ccd0632f` |

`tools/install-release.sh` builds and installs the same signed APK on the
phone on USB. Without the release key it signs with the Android debug key
instead; that build can't update, or be updated by, a released one.

## Rules

**Data safety**
- Saves always carry `base_version`; a `409` must reach the user as a
  choice (Keep mine / Use theirs), never be overwritten silently.
- Keep the save model in step with the web editor (`editor.js`, `sync.js`,
  `secrets.js` in the server repo): the same flattening rules, the same
  collapsed rule, the same handling of moved and pasted block ids.
- Task-list status changes go through `PUT /api/block` with `expected`.

**Encrypted blocks**
- Plaintext of `#Encrypted` children must never be sent to the server,
  logged, or stored on the phone. The key lives in memory only.
- `crypto/BlockCrypto.kt` and `outline/Outline.kt` must stay byte-compatible
  with `web/js/crypto.js`, `render.js` (`splitProps`, `joinProps`,
  `parseOutline`) and `editor.js` (`subtreeMarkdown`). Change them only with a
  matching test, and check a phone edit with `tools/decrypt-check.ts`.
- Refuse PBKDF2 round counts above 600,000.

**Pairing and privacy**
- Never store the server password. Only the device token, encrypted with the
  Android Keystore key in `Store`.
- No analytics, crash reporting, ads or third-party services. HTTPS only,
  except `localhost` for tests.
- App data stays out of cloud backup and device transfer.

**Code style**
- Kotlin, Compose, Material 3. Few dependencies: AndroidX, OkHttp,
  kotlinx.serialization and coroutines. Ask before adding another; it must be
  permissively licensed (MIT / Apache-2.0 / BSD).
- Android's regex engine (ICU) is stricter than the JVM's that unit tests
  use: escape literal `]`, `}` and `{` in patterns.
- Comments explain *why*, briefly.

**Docs**
- Update README.md (short, tables), ARCHITECTURE.md and CHANGELOG.md in the
  same change as the code.

## Provenance (clean room)

Like the server, this app must not contain code, styles, icons or other
assets copied or translated from Logseq (AGPL-3.0) or from any project whose
license is incompatible with MIT. Build from the Blockseq server's documented
API and observed behaviour.

## Deployment

- Install with `adb install -r`. Do not publish builds, push to remotes, or
  create a GitHub repository unless the owner asks.
- Server changes the app needs go to the server repo first.
