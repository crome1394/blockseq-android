# Architecture

Blockseq for Android is a thin client: every note lives on the Blockseq
server, and the app reads and writes them through the server's JSON API
(documented in the server repo's
[ARCHITECTURE.md](https://github.com/crome1394/blockseq/blob/master/ARCHITECTURE.md)).
Nothing is cached on the phone between launches except the pairing.

## Layout

| Path | What it does |
|---|---|
| `app/src/main/java/app/blockseq/android/MainActivity.kt` | Navigation: pairing, home, a page, settings |
| `AppModel.kt` | App-wide state: the API client, task statuses, the encryption key while unlocked |
| `data/Api.kt` | The HTTP API (OkHttp), live updates (server-sent events), device pairing |
| `data/Store.kt` | Server address, workbook, and the device token (encrypted with an Android Keystore key) |
| `outline/Outline.kt` | The block tree, its flat API form, managed properties, the outline text format |
| `outline/Tasks.kt` | Task statuses (`TODO`, `DONE`, …), matching the web app |
| `crypto/BlockCrypto.kt` | `#Encrypted` blocks: PBKDF2 + AES-256-GCM, byte-compatible with the web app |
| `editor/PageEditor.kt` | One page being edited: edits, autosave with the version check, conflicts, sealing |
| `render/Markdown.kt` | Inline Markdown to styled, tappable text; a block split into parts |
| `render/BlockView.kt` | Drawing one block: task badge, text, code, tables, tips, images |
| `render/ImageViewer.kt` | A picture full screen: zoom, share, save, remove, delete |
| `ui/` | Compose screens: pairing, home (left pane with a month calendar, tabs), page, settings (a dialog card with swipeable tabs), unlock |
| `app/src/test/` | Unit tests: outline format, tasks, encryption (vectors made with WebCrypto) |
| `tools/` | A throwaway demo server with made-up notes, and a WebCrypto decrypt check |

## Pairing

The app never stores the password. `POST /api/devices/pair` with the
password and a device name returns a token (`bsq_…`), shown once. The app
keeps it in `SharedPreferences`, encrypted with an AES key that lives in the
Android Keystore and cannot be read out of the phone. Every request sends
`Authorization: Bearer <token>`.

A `401 {"error":"device"}` means the token was revoked in the web app
(Settings → Security) or the server's password changed. Any screen that gets
it sends the app back to pairing, with that reason shown.

App data is excluded from cloud backup and device transfer, so the token
stays on this phone.

## Saving

The same model as the web editor:

- A page is a tree of blocks. A save sends the whole page with the version it
  was loaded at (`PUT /api/page`, `base_version`).
- Edits schedule a save 800 ms later; leaving the page saves at once.
- `409` with the other version: the page shows *Keep mine* (saved again with
  `overwrite`, the server keeps theirs in the page history) or *Use theirs*.
  `409 deleted`: *Leave it deleted* or *Keep mine*. `409 foreign_ids`: pasted
  blocks get new ids and the save runs again.
- A failed save retries after 4 s.
- Live updates (`GET /api/events`) reload an open page when another device
  saves it, unless this phone has unsaved changes (the next save reports the
  conflict instead).
- Task status taps in the task list use `PUT /api/block` with the text the
  app last saw, so a task changed elsewhere is not overwritten. They are sent
  one at a time, and a changed task stays in the list for 6 seconds with its
  new status (even done or none), as in the web app, so a few taps step
  through the statuses.

Enter in a block splits it at the cursor (inside an open code fence it adds
a line). The keyboard can report the same edit twice from the old field
after a split; edits from a field that is no longer the one being edited are
ignored.

## Pictures

- Blocks show uploaded pictures (`../assets/…`, alone on a line or inside
  text) as thumbnails at most 220 dp tall, decoded at up to 800 px and kept
  in a 24 MB memory cache; a size the block asks for (`{:width …}`) is
  ignored on the phone. Pictures are fetched with the device token.
- Tapping one opens `ImageViewer` (decoded at up to 4096 px): pinch to zoom,
  double-tap to reset, **Share** (a copy in the app cache, offered through a
  `FileProvider`), **Save to phone** (MediaStore, `Pictures/Blockseq`, no
  storage permission), **Remove from block** (only in the page editor; the
  file stays) and **Delete from server** (`DELETE /api/assets`, after a
  confirmation; the picture is also taken out of the block).
- **Add a picture** (the camera icon in the page's top bar or the editing
  toolbar): *Take a photo* (the system camera writes into the app cache, no
  camera permission) or *Choose from phone* (the system photo picker, no
  storage permission). The original file is uploaded with
  `POST /api/assets` and its Markdown goes at the cursor of the block being
  edited, or into a new block at the end.

## Reading and tagging

- Code blocks wrap, have a copy button, and are coloured by `render/Highlight.kt` (in-house: keywords,
  strings, comments, numbers, type names) and long ones fold like the web
  app (Settings → Code blocks; those settings live in each browser, so the
  phone keeps its own copy with the same defaults).
- Linked references start each group with an icon that opens the page they
  live on (a calendar for a journal). Property lines the web app hides are
  never shown.
- Tags are coloured with the web app's `tagHue` (the same FNV-style hash,
  checked against the web's values in a unit test). Row highlights, alignment,
  auto headings and numbered lists render from the same properties the web app
  stores (`background-color::`, `text-align::`, `heading::`,
  `logseq.order-list-type::`).
- Press and hold any block for `BlockMenu`, a bottom sheet: colours,
  headings, alignment, strikethrough (`toggleLine`, the web editor's rule), tag (`TagDialog`), timestamp, copy ref / URL /
  Markdown / plain text, share, cut, numbered list, delete.
- In `TagDialog`: the workbook's tags (from
  `GET /api/pages`, most used first, not `#task`), filtered as you type, or a
  new one. `PageEditor.addTag` puts it at the end of the first line
  (`#[[two words]]` when needed; after the text of a block that starts with
  code) unless the block already has it.
- Settings → Controls at the bottom (on by default) moves action buttons
  (picture viewer, page actions) to the bottom edge (`LocalControlsAtBottom`).
- *Take a photo* uses the phone's camera app; on phones whose camera app does
  not take capture requests it falls back to `CameraScreen` (CameraX), which
  asks for the camera permission the first time.

## Scratchpad widget

The only part that works offline: the page **Scratchpad** in Main.

| Piece | Role |
|---|---|
| `scratch/ScratchpadStore.kt` | The phone's copy (`files/scratchpad.json`): rows as last synced, notes waiting to be sent, last sync time or error; thumbnails in `files/scratch-img/` |
| `scratch/ScratchpadSync.kt` | Reads the page, appends waiting notes as new top-level rows and saves with the version check (on a conflict: read again, append again, up to 4 tries, so nothing on the server is overwritten); caches rows and up to 12 thumbnails (480 px) |
| `SyncWorker` (WorkManager) | Runs a sync as soon as there is a network after a note is added (retrying with backoff), when the app opens with notes waiting, and every 30 minutes while a widget exists |
| `scratch/ScratchpadWidget.kt` (Glance) | Draws the copy: newest or oldest first, a heading per day and the time on a small line, a padlock (`ToggleLock`) that hides the notes, pictures, ⏳ for waiting notes, Sync and + buttons; tapping a row opens the page in the app. Redrawn through a revision key in each widget's state |
| `scratch/QuickAddActivity.kt` | The +: a card over the home screen; the note is stamped `[[Nov 12th, 2026]] …` (the date only) and queued at once |
| `scratch/WidgetPrefs.kt` | The look, shared by every widget: background (with alpha), text colour, order, buttons top or bottom, padlock |
| `scratch/WidgetConfigActivity.kt` | On placing (and *Reconfigure*): `WidgetLookEditor`, also shown in Settings → Widget |

## Encrypted blocks

Byte-compatible with `web/js/crypto.js` and `src/crypto.rs` in the server
repo:

- One random AES-256-GCM key encrypts every block. The server stores it
  wrapped with a key derived from the encryption password (PBKDF2-SHA256,
  600,000 rounds, refused above that).
- A block tagged `#Encrypted` keeps its own text readable; its children are
  stored as one blob, `encrypted:: v1:<iv>:<ciphertext>`, holding the
  children as Logseq-style outline text with `id::` lines.
- Unlocking (about 1.8 s on a Pixel 9) keeps the key in memory only, until
  *Lock* or the app closes. Pages decrypt their blobs after loading, nested
  blobs too.
- Before every save, encrypted blocks are sealed innermost first; unchanged
  children keep their old blob.

`tools/decrypt-check.ts` opens what the phone wrote with WebCrypto, the
browser's own implementation, as an end-to-end check.

## Privacy

No analytics, no crash reporting, no third-party services. The app talks
only to the server you paired with. HTTPS is required; plain HTTP is allowed
only to `localhost`, for testing through `adb reverse`.

## Not done yet

Offline reading, a home-screen widget or share target for quick capture,
files other than pictures, page history, rendering maths (shown as
source for now), and creating or renaming pages and workbooks.
