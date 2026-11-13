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
