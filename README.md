# Blockseq for Android

[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Kotlin 2.2](https://img.shields.io/badge/kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Android 10+](https://img.shields.io/badge/android-10%2B-3DDC84?logo=android&logoColor=white)](#install)
[![Server: Blockseq](https://img.shields.io/badge/server-Blockseq-2E6F6F)](https://github.com/crome1394/blockseq)
[![No tracking](https://img.shields.io/badge/tracking-none-2ea44f)](ARCHITECTURE.md#privacy)

A native Android app for [Blockseq](https://github.com/crome1394/blockseq),
the self-hosted, Logseq-style outliner. Read and edit your notes on your
phone, straight from your own server.

## Features

| | |
|---|---|
| **Read** | Journals, pages, links, tags, task badges, tables, code and tips |
| **Pictures** | Shown as thumbnails; tap for full screen with zoom, share, save to the phone, remove or delete |
| **Add pictures** | Take a photo or choose one from the phone, into the block you are editing |
| **Thumb-friendly** | Action buttons along the bottom of the screen (a setting) |
| **Left pane** | The ☰ menu: a calendar of journal days, sections, starred pages, workbooks, settings |
| **Edit** | Tap a block to edit; Enter splits it; indent, outdent, move, delete from a toolbar |
| **Block menu** | Press and hold any block: colour, heading, alignment, strikethrough, tag, timestamp, copy, share, cut, numbered list, delete |
| **Looks like the web** | Coloured tags, row highlights, headings, numbered lists |
| **Quick capture** | A + button for new pages or tags; a pencil for a quick note on today's journal |
| **Code** | Syntax colours, wrapped lines, a copy button; long blocks fold, as in the web app |
| **Tasks** | Every open task in one list; tap a badge to step its status (it stays a few seconds, so you can tap on) |
| **Search** | Full-text search, with the field at the top or bottom (Settings) |
| **Encrypted blocks** | `#Encrypted` blocks unlock with your password and decrypt on the phone |
| **Safe saves** | The server's version check: a conflict shows *Keep mine* / *Use theirs* |
| **Live** | Pages reload when another device saves them |
| **Paired, not logged in** | The password is used once; the phone gets its own token, revocable on the server |
| **Workbooks** | Switch between your server's workbooks |
| **Scratchpad widget** | Your Scratchpad page on the home screen: notes grouped by day, pictures, a + to add, works offline; a padlock to hide notes; colours, transparency and button position in Settings |

## Install

1. Run a [Blockseq server](https://github.com/crome1394/blockseq) (0.1.0 or later with device pairing) behind HTTPS, with `BLOCKSEQ_PASSWORD` set.
2. Build and install the app (see [Building](#building)), or install a release APK.
3. Open it, enter your server address and password, and tap **Pair**.

The phone must reach the server, e.g. on your network or over a VPN such as Tailscale. There is no offline mode.

## Building

```sh
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # unit tests (outline format, tasks, encryption)
tools/install-release.sh                # minified build (~3 MB), signed with your debug key, installed
```

Needs JDK 21 and the Android SDK (platform 36). The Gradle wrapper downloads Gradle itself.

## Documentation

| Guide | What's in it |
|---|---|
| [Architecture](ARCHITECTURE.md) | How the app is built, pairing, saving, encryption, privacy |
| [Changelog](CHANGELOG.md) | What changed, by release |
| [Contributing](AGENTS.md) | How to build, test on a phone, and the rules for changes |

## License

[MIT](LICENSE). A clean-room implementation, like the server: no code from Logseq.
