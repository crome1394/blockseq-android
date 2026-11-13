# Changelog

All notable changes to Blockseq for Android, newest first. Each entry ends
with the short commit hash it comes from.

## Unreleased (0.1.0)

The first version. Needs a Blockseq server with device pairing.

### Added

- **Scratchpad widget**: the Main workbook's Scratchpad page on the home screen, newest first (or oldest), with date stamps, pictures and a + card to add a note. Notes are kept on the phone at once and sent when there is a connection; a Sync button too. Background colour, transparency, text colour and order are set per widget. Settings → Scratchpad widget can place it and sync. (widget commit)
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
