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
| `net/AutoTailscale.kt` | Connects Tailscale while the app is open and the server is out of reach ([below](#tailscale)) |
| `net/ServerProbe.kt`, `net/Tailscale.kt` | The direct-reach check (`/api/health`), and Tailscale's connect/disconnect intents |
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
  *See the differences* lists the two versions line by line (`editor/Diff.kt`:
  − only on the other device, + only on this phone; long unchanged runs fold;
  children of encrypted blocks are one 🔒 line, as theirs are still sealed;
  the card sits in a full-screen window and is dragged by its title).
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
- Tapping one opens `ImageViewer`, through `ImageViewerHost` at the top of
  the app (so it outlives the list row that opened it) (decoded at up to 4096 px): pinch to zoom,
  double-tap to reset, the bar hides after 3 s in landscape (tap to show it), **Share** (a copy in the app cache, offered through a
  `FileProvider`), **Save to phone** (MediaStore, `Pictures/Blockseq`, no
  storage permission), **Remove from block** (only in the page editor; the
  file stays) and **Delete from server** (`DELETE /api/assets`, after a
  confirmation; the picture is also taken out of the block).
- **Add a picture** (the page's ⋯ menu or the camera icon on the editing
  toolbar): *Take a photo* (the system camera writes into the app cache, no
  camera permission) or *Choose from phone* (the system photo picker, no
  storage permission). The original file is uploaded with
  `POST /api/assets` and its Markdown (with `{:size "quarter"}`, the web app's quarter size) goes at the cursor of the block being
  edited, or into a new block at the end.

## Assets

`ui/AssetsScreen.kt`, from the ☰ menu: `GET /api/assets` listed like the web
app's Assets page (filter, *Only unused*, total; per file a thumbnail, size,
date, path with copy, pages that use it). *Open* shows pictures in the viewer
and hands other files to a phone app (`ACTION_VIEW` on a cached copy through
the `FileProvider`); *Download* saves through MediaStore (pictures to
`Pictures/Blockseq`, others to `Download/Blockseq`, no permission); *Delete*
(`DELETE /api/assets`) confirms first and names the pages that would show a
broken link.

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
- `---` / `***` / `___` alone on a line is `Part.Rule`, a horizontal line, as in the web app; the editing toolbar inserts one.
- The editing toolbar (`EditBar` in `ui/PageScreen.kt`) is the page's bottom bar, riding on the keyboard, or, with Settings → Display → Editing bar → Inline (`Store.editBarInline`), an item in the list between the edited block's row and the row above. The page content takes the keyboard's height itself (`imePadding`), so the inline bar and the field stay above the keyboard.
- Moving and adding rows: press and hold a block, then drag it up or down
  (sideways changes its depth; its children come along; the page scrolls at
  the edges; `depthRange` and `placeNode` in `editor/Reorder.kt` decide where
  it may land). While a block is lifted a bin shows in the bottom-right
  corner: dropping on it deletes the block and its children, with *Undo*
  (10 s) putting it back in place (`placeOf` / `restore`). Let go without
  moving for the menu. A short tap on the thin
  strip above a block adds a row there; the list catches it before the rows,
  whose touch areas Compose stretches to 48 dp. Haptics: a buzz on lift, a
  tick when the landing place changes, a confirm (or reject) on drop.
- The page's floating ⋯ (`PageMenuFab`, hidden while a block is edited):
  *New row* (tap: a row at the end; drag: a chip follows the finger and the
  same drop line places it, moving left outdents, the bin cancels;
  `newRowAfter`), *Take a photo*, *Choose a picture*, *Lock* / *Unlock*.
- Press and hold any block for `BlockMenu`, a bottom sheet: move up / down,
  outdent / indent, add row above / below, colours,
  headings, alignment, strikethrough (`toggleLine`, the web editor's rule), tag (`TagDialog`), timestamp, copy ref / URL /
  Markdown / plain text, share, cut, numbered list, delete.
- In `TagDialog`: the workbook's tags (from
  `GET /api/pages`, most used first, not `#task`), filtered as you type, or a
  new one. `PageEditor.addTag` puts it at the end of the first line
  (`#[[two words]]` when needed; after the text of a block that starts with
  code) unless the block already has it.
- Settings → Controls at the bottom (on by default) moves action buttons
  (the picture viewer's) to the bottom edge (`LocalControlsAtBottom`).
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
| `scratch/ScratchpadWidget.kt` (Glance) | Draws the copy: newest or oldest first, a heading per day and the time on a small line, encrypted rows as their own text plus a 🔒 line (the key never reaches the widget; its padlock opens the app), pictures, ⏳ for waiting notes, Sync and + buttons; tapping a row opens the page in the app. `#tags` keep the app's colour per tag (`tagColors`, dark or light set from the widget's text colour): Glance `Text` can't colour part of a string, so text with tags is a `TextView` (`res/layout/widget_rich_text.xml`, through `AndroidRemoteViews`) with colour spans. Redrawn through a revision key in each widget's state |
| `scratch/QuickAddActivity.kt` | The +: a card over the home screen; queued at once for today; `withPending` puts each note as the last child of the day's top-level date row `[[Nov 13th, 2026]]` (made if missing), both when sending and when drawing waiting notes |
| `scratch/WidgetPrefs.kt` | The look, shared by every widget: background (with alpha), text colour, order, buttons top or bottom |
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

## Tailscale

With Settings → General → **Connect Tailscale when needed** on (default) and
Tailscale installed, `AutoTailscale.ensure()` runs when the app comes to the
front and before each live-stream (re)connect, so leaving home Wi-Fi mid-use
is caught too. When no VPN is up it decides:

| Network | Connects Tailscale when |
|---|---|
| Mobile data | Always |
| Wi-Fi, Ethernet, other | `GET /api/health` (no login, redirects not followed) on that network doesn't answer 200 or 503 within about 3 s |

So home Wi-Fi stays direct, while another Wi-Fi, a proxy refusing outside
addresses, or a captive portal uses Tailscale. Connecting sends Tailscale's
`CONNECT_VPN` intent (the one Tasker uses), waits up to 15 s for the VPN,
then reloads the open views and sends any notes the widget queued.

The live stream keeps the connection busy while the app is open. A connection
the app made (remembered in `Store`, so an app restart doesn't orphan it) is
dropped in two cases:

| When | How |
|---|---|
| The phone joins a Wi-Fi or Ethernet network with working internet while the app is open (`Tailscale.watchWifi` → `networkJoined`) | Tailscale is disconnected and `ensure()` asks again on that network: at home `/api/health` answers and it stays off; on any other Wi-Fi it reconnects. With the VPN up there is no reliable way to ask "would the server answer without it?", and reading the Wi-Fi's name needs the location permission, so it is dropped first and asked then. A network it already decided on is not asked again |
| The app leaves the screen | A system alarm sends `DISCONNECT_VPN` straight to Tailscale 30 s later, even if Android froze or killed the app; coming back first cancels it |

Only a connection the app made is ever disconnected.

The key in the left pane (`TailscaleItem`, hidden with Settings → General →
**Show Tailscale button**) shows the VPN state and connects or disconnects by
hand (`AutoTailscale.toggle`). What it does is the user's: a connection made
with it is left alone like one made in Tailscale, and after turning Tailscale
off with it the app doesn't turn it back on until the app is opened again or
the phone joins a Wi-Fi network.

The widget's background sync doesn't connect Tailscale; its queued notes go
out the next time the app opens.

## Privacy

No analytics, no crash reporting, no third-party services. The app talks
only to the server you paired with (and, when set to, tells the Tailscale app
on the phone to connect or disconnect). HTTPS is required; plain HTTP is allowed
only to `localhost`, for testing through `adb reverse`.

## Not done yet

Offline reading, a home-screen widget or share target for quick capture,
files other than pictures, page history, rendering maths (shown as
source for now), and creating or renaming pages and workbooks.
