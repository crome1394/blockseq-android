# Changelog

All notable changes to Blockseq for Android, newest first. Each entry ends
with the short commit hash it comes from.

## Unreleased (0.1.0)

The first version. Needs a Blockseq server with device pairing.

### Added

- Scratchpad widget: `#tags` are coloured as in the app and the web app (each tag its own colour, on a tinted background).
- Tailscale connects by itself when the server is out of reach: on mobile data, or on Wi-Fi where `/api/health` doesn't answer directly (away from home). It stays up while the app is open and disconnects 30 seconds after you leave; a connection you made yourself is left alone. Settings → General → Tailscale.
- Fixed: an open page stopped following other devices' changes after the connection dropped once; it now reconnects and catches up.
- Fixed: on a slow connection, New task could say it failed after adding the task, so trying again added it twice.
- Fixed: pages with % in their name ("100% done") opened as a different, garbled page: the name was decoded twice.
- Fixed: a change from another device arriving while a block was open reloaded the page under it, and the next keystroke put the old text back over theirs without a conflict. The page now reloads when the block closes.
- Fixed: leaving a page (Back) less than a second after typing could drop the change: the last save was started on the page's own task just as it was being stopped.
- Fixed: saving read and sealed the page on a background thread while typing changed it on the main one (a possible crash, or a save missing the latest keystroke).
- Fixed: a change made while the previous save was on its way could be marked saved and never sent.
- Fixed: typing while a save was on its way cancelled it on the phone after the server had stored it, so the next save reported a conflict with the phone's own change.
- Fixed: locking with a change to an encrypted block still unsaved (a slow or offline save) no longer seals it with an empty key; the page saves before locking, and a change that still cannot be sealed is refused.
- An X at the top right closes the block menu, Add a tag, New page or tag, and the Differences card.
- Pages list: regular pages have a page symbol, beside the calendar for journals and the star for starred pages.
- Fixed: Pages → Filter → All crashed the app (the sort compared a journal's day, a Long, with 0, an Int, for pages).
- Assets (in the ☰ menu), as in the web app: every uploaded file with a thumbnail, size, date, path (copy) and the pages that use it (tap to open); filter, Only unused, the total; Open (pictures in the viewer, other files in a phone app), Download (Pictures/Blockseq or Download/Blockseq), Delete (warns when the file is in use).
- The picture viewer stays open when the phone turns on long pages such as the Scratchpad: it now lives at the top of the app, not in the row whose thumbnail opened it (that row could leave the screen in landscape and take the viewer with it).
- Turning the phone no longer closes the picture viewer (or anything else open): the app handles rotation itself instead of being rebuilt.
- Journals tab: a soft band on every other row; Settings → Display → Journal rows (Off, Subtle (default), Stronger, Accent colour, which follows the phone's theme colour).
- Pages and journals have a floating ⋯ menu: New row (tap: at the end; drag: to a place, with the drop line and bin), Take a photo, Choose a picture, Lock / Unlock. The camera and padlock left the top and bottom bars.
- Left pane: the sections and favorites share the height and grow to fill it (scrolling only when too many to fit); Settings sits just above the calendar. The settings cog in the home top bar is gone (Settings is in the pane).
- Row colours fill the whole row out to the right edge, as on the web, and show at once when picked in the block menu (they used to wait for the page to reload).
- Calendar weekday letters are bright red; Settings → Display → First day of the week (Sunday, Monday or Saturday).
- The Differences card can be dragged around the screen by its title.
- Widget notes go in as children of the day's date row (`[[Nov 13th, 2026]]`, made with the day's first note), so the Scratchpad reads as one block per day with that day's notes under it.
- Conflicts: within a changed row, the words that differ are marked.
- On the Journals tab, a swipe right opens the left pane.
- The left pane's calendar stays at the bottom of the pane.
- Journals: the pencil opens the Quick note card straight away (the Edit journal choice is gone).
- Conflicts: *See the differences* lists theirs and mine line by line (6f753b6).
- Drag a block onto the bin in the bottom-right corner to delete it, with Undo (cdef3c1).
- Tree lines bend into each child's dot with a rounded corner (ea1f8e0).
- Press and hold a block and drag it to move it (sideways changes its depth); tap the strip above a block to add a row; Move up / down, Outdent / Indent and Add row above / below in the block menu; haptic feedback (ed7590c).
- The block menu opens half way and can be dragged up or down from anywhere on it, not only the handle.
- Lines join the bullets in pages, as in the web app: from a block's dot down to its last child.
- Search: tags have a tag symbol.
- Widget notes: the day's first note gets a date row above it (`[[Nov 13th, 2026]]`); later notes that day go in without a stamp. The widget uses the date rows as day headings (older stamped notes still group by their stamp).
- The widget's padlock is coloured like the app's: amber while locked, green while the app has encrypted blocks unlocked. (The widget still never decrypts.)
- Tasks: a + button opens a card to write a task, pick its status (in its colour) and add a due date; it goes on the last row of today's journal. A due date is written as `📅 [[Nov 20th, 2026]]`, so the task also shows on that day. Each task group has a calendar or page symbol.
- Search results have a calendar beside journal days and a page symbol beside pages (tap to open). The left pane is tighter, so the calendar fits without scrolling.
- Swipe sideways between Journals, Pages, Search and Tasks.
- The Add a tag list shows each tag in its colour.
- Widget: notes are never hidden. An encrypted row shows its own text and a 🔒 line, as in the web app while locked; the padlock (shown when there is one) opens the Scratchpad in the app to unlock it. The hide-everything padlock is gone.
- Horizontal lines: `---` (or `***`, `___`) on its own line draws a line in pages and the widget; the editing toolbar has a button that adds one, as the web's `/hr`.
- Pages: the button opens a small menu (New page or tag, Filter). Filter shows a card with the text field and Pages / Journals / Starred / All; closing it shows every page again.
- The picture viewer hides its bar after 3 seconds in landscape; tap the picture to bring it back.
- Pictures added from the phone (camera or gallery) are set to quarter size (`{:size "quarter"}`), so they show at a quarter of their width in the web app.
- Scratchpad notes are stamped with the date only (`[[Nov 12th, 2026]] …`); the widget still shows the time of older notes that have one.
- ``code`` (double graves) has a copy button after it, as in the web app.
- Opening the app sends Scratchpad notes still waiting (Android can hold the widget's retry back for minutes). (`c38769b`)
- A month calendar in the left pane, as in the web app: dots on days whose journal has notes, today ringed, tap a day to open it. (`2f3b18b`)
- **Settings** open as a card over the current screen, in tabs you can swipe between: General, Display, Code, Widget, Security. Widget has the widget's look (colours, Solid / See-through / Transparent, order, buttons top or bottom), applied to every widget at once. (`225fabe`)
- The quick-note cards (widget + and journal pencil) have an X to close them. (`851a6fb`)
- Widget: notes grouped under a heading for each day, times without seconds; a padlock that hides the notes until tapped again; the buttons at the top or bottom; a Solid / See-through / Transparent background. The look is now one setting for every widget. (`c17696b`)
- **Strikethrough**: in the block menu (the first line; status and tags stay outside) and on the editing toolbar (the selection, or the line the cursor is on), stored as `~~text~~` like the web app. (`38a6eb0`)
- **Scratchpad widget**: the Main workbook's Scratchpad page on the home screen, newest first (or oldest), with date stamps, pictures and a + card to add a note. Notes are kept on the phone at once and sent when there is a connection; a Sync button too. Background colour, transparency, text colour and order are set per widget. Settings → Scratchpad widget can place it and sync. (`07e23a5`)
- Journals: a calendar icon beside each date, row colours and other properties in the previews, encrypted children once unlocked, and a quick-note card on the pencil button that adds to the last row of today. The padlock is on the home screen too, and the left pane shows Connected / Offline and when the app last synced. (`c48b860`)
- Coloured tags (the web's colours), row highlights, alignment, auto headings and numbered lists; the full block menu on press and hold; code blocks wrap and have a copy button. (`cd7f856`)
- A + button on Pages for a new page or tag, a pencil on Journals for a quick note on today's journal; the Pages filter follows the top/bottom setting. (`e07b856`)
- Press and hold any block to add a tag: the workbook's tags, filtered as you type, or a new one. (`a82d7c9`)
- Take a photo works on phones whose camera app refuses capture requests (it crashed before): a built-in camera stands in. Settings → Controls at the bottom, a coloured padlock (amber locked, green open), yellow stars. (`46d6358`, `1c2a044`)
- Linked references have an icon on the left that opens the page they live on (a calendar for a journal), apart from the links in the text. Hidden property lines such as `background-color::` are no longer shown. (`6fead46`)
- Code blocks have syntax colours, and long ones start folded with a *Show all* bar (Settings → Code blocks, the web app's defaults: fold above 20 lines, show 6). ``text`` shows as inline code. (`3894a75`)
- Pictures: thumbnails in journals and pages (inline ones too), a full-screen viewer with zoom, share, save to the phone, remove from block and delete from server, and adding pictures from the camera or the phone. (`70af61a`)
- A left pane (☰): sections, starred pages, workbooks, settings. The search field can sit at the top or bottom. A task whose status changes stays in the list for 6 seconds, so you can tap through to done or none. (`fac2d8c`)
- Pairing with the server address and password once; the phone keeps only its own device token, encrypted with an Android Keystore key. A revoked token sends the app back to pairing with the reason. (`71bf776`, `aedf187`)
- Journals, Pages, Search and Tasks tabs, and a workbook switcher in Settings. (`71bf776`)
- Pages render links, tags, task badges, tables, code, tips and images; links and tags have fixed, readable colours. (`71bf776`, `aedf187`)
- Editing: tap a block, Enter splits it (a new line inside code), and a toolbar indents, outdents, moves, steps the status, adds a line break and deletes. (`71bf776`, `9f7908c`)
- Autosave with the server's version check, *Keep mine* / *Use theirs* on a conflict, and live reload when another device saves. (`71bf776`)
- `#Encrypted` blocks unlock with the encryption password and decrypt on the phone, nested ones too; edits are sealed again in the web app's format. (`04eab38`, `71bf776`)
- Unit tests for the outline format, task statuses and encryption (vectors made with the browser's WebCrypto). (`04eab38`, `9f7908c`)
- `tools/install-release.sh` builds the minified release (~3 MB), signs it with the local debug key and installs it.
- `tools/`: a throwaway demo server for testing on a phone, and a WebCrypto check of what the phone wrote. (`71bf776`)
