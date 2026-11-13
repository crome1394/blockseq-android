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
| `ui/` | Compose screens: pairing, home tabs, page, settings, unlock |
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
  app last saw, so a task changed elsewhere is not overwritten.

Enter in a block splits it at the cursor (inside an open code fence it adds
a line). The keyboard can report the same edit twice from the old field
after a split; edits from a field that is no longer the one being edited are
ignored.

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
images and files from the phone, page history, rendering maths (shown as
source for now), and creating or renaming pages and workbooks.
