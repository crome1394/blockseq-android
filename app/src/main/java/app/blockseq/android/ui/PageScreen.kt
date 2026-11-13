package app.blockseq.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.editor.Conflict
import app.blockseq.android.editor.PageEditor
import app.blockseq.android.outline.Node
import app.blockseq.android.render.BlockContent
import app.blockseq.android.render.Target
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(model: AppModel, name: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val api = model.api ?: return
    val scope = rememberCoroutineScope()
    val editor = remember(name, model.generation, api) {
        PageEditor(api, name, model.statuses, model.key, scope, model::handle)
    }
    LaunchedEffect(editor) { editor.load() }
    // Live updates: another device saved this page.
    LaunchedEffect(editor) {
        api.events().catch { }.collect { ev ->
            val type = ev["type"]?.jsonPrimitive?.content
            val page = ev["name"]?.jsonPrimitive?.content
            val client = ev["client"]?.jsonPrimitive?.content
            if (type == "resync" || (page != null && page.equals(name, true) && client != api.clientId)) editor.remoteChanged()
        }
    }
    DisposableEffect(editor) { onDispose { scope.launch { editor.save() } } }

    var editing by remember(editor) { mutableStateOf<Node?>(null) }
    val editingId = editing?.id
    var field by remember(editor) { mutableStateOf(TextFieldValue("")) }
    var unlocking by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val onTap: (Target) -> Unit = { t ->
        when (t) {
            is Target.Page -> onOpen(t.name)
            is Target.Url -> runCatching { uri.openUri(t.url) }
            is Target.BlockRef -> scope.launch { runCatching { api.blockPage(t.id) }.getOrNull()?.let(onOpen) }
        }
    }
    fun startEdit(n: Node, caret: Int = n.content.length) {
        editing = n
        field = TextFieldValue(n.content, TextRange(caret.coerceIn(0, n.content.length)))
    }
    fun stopEdit() {
        editing = null
        scope.launch { editor.save() }
    }

    // Pictures from the phone: uploaded to the workbook's assets, then put at
    // the cursor of the block being edited, or in a new block at the end.
    val context = LocalContext.current
    var uploading by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    fun insertPicture(uri: android.net.Uri) {
        uploading = true
        scope.launch {
            try {
                val (bytes, fileName, mime) = withContext(Dispatchers.IO) { readPicture(context, uri) }
                val path = api.uploadAsset(bytes, fileName, mime)
                val md = "![${fileName.substringBeforeLast('.')}]($path)"
                val target = editing
                if (target != null) {
                    val sel = field.selection
                    val t = field.text.substring(0, sel.start) + md + field.text.substring(sel.end)
                    field = TextFieldValue(t, TextRange(sel.start + md.length))
                    editor.setText(target, t)
                } else {
                    editor.appendWith(md)
                }
            } catch (e: Exception) {
                model.handle(e)
                editor.error = "Could not add the picture: ${e.message}"
            } finally {
                uploading = false
                cameraFile?.delete()
            }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::insertPicture) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) insertPicture(FileProvider.getUriForFile(context, "${context.packageName}.files", f)) else f?.delete()
    }
    var builtInCamera by remember { mutableStateOf(false) }
    fun openCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        cameraFile = f
        // Some camera apps do not take "capture a photo" requests (this crashed
        // before); the app's own camera stands in for them.
        try {
            takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
        } catch (e: android.content.ActivityNotFoundException) {
            builtInCamera = true
        }
    }
    fun choosePhoto() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // The page's buttons: in the top bar, or along the bottom (Settings → Controls at the bottom).
    val pageActions: @Composable () -> Unit = {
        PictureButton(onCamera = ::openCamera, onGallery = ::choosePhoto, tag = "page-picture")
        if (model.setup != null) LockButton(model.key != null, onLock = { stopEdit(); model.lock() }, onUnlock = { unlocking = true })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = when {
                            uploading -> "Uploading picture…"
                            editor.conflict != null -> "Choose a version"
                            editor.error != null -> editor.error!!
                            editor.saving || editor.dirty -> "Saving…"
                            editor.lastSaved != null -> "Saved"
                            else -> ""
                        }
                        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("save-status"))
                    }
                },
                navigationIcon = { IconButton(onClick = { stopEdit(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (!LocalControlsAtBottom.current) pageActions() },
            )
        },
        bottomBar = {
            val node = editing
            if (node == null && LocalControlsAtBottom.current) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { pageActions() }
                }
            }
            if (node != null) {
                EditBar(
                    onOutdent = { editor.outdent(node) },
                    onIndent = { editor.indent(node) },
                    onUp = { editor.move(node, -1) },
                    onDown = { editor.move(node, 1) },
                    onStatus = { editor.cycleStatus(node); field = TextFieldValue(node.content, TextRange(node.content.length)) },
                    onLineBreak = {
                        val t = field.text.substring(0, field.selection.start) + "\n" + field.text.substring(field.selection.end)
                        field = TextFieldValue(t, TextRange(field.selection.start + 1))
                        editor.setText(node, t)
                    },
                    onDelete = {
                        val above = editor.delete(node)
                        if (above != null) startEdit(above) else stopEdit()
                    },
                    onDone = { stopEdit() },
                    picture = { PictureButton(onCamera = ::openCamera, onGallery = ::choosePhoto, tag = "edit-picture") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            editor.conflict?.let { ConflictBar(it, editor) }
            if (!editor.loaded) {
                Text("Loading…", Modifier.padding(16.dp))
                return@Column
            }
            val rows = remember(editor.revision, editor) { editor.rows }
            LazyColumn(Modifier.fillMaxSize().testTag("outline"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
                items(rows, key = { it.node.id }) { row ->
                    val n = row.node
                    Row(Modifier.fillMaxWidth().padding(start = (12 + row.depth * 20).dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
                        Bullet(n, editor) { editor.toggleCollapse(n) }
                        Spacer(Modifier.width(8.dp))
                        if (n.id == editingId) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(n.id) { focus.requestFocus() }
                            TextField(
                                value = field,
                                onValueChange = onChange@{ v ->
                                    // A newline the keyboard just added splits the block, like Enter
                                    // on a computer (inside a code block it stays a new line).
                                    // After a split, the old block's field reports the same edit again,
                                    // while the text state already belongs to the new block: ignore it.
                                    if (editing?.id != n.id) return@onChange
                                    val at = newlineTyped(field.text, v.text)
                                    if (at != null && !insideFence(v.text, at)) {
                                        val fresh = editor.split(n, v.text.substring(0, at), v.text.substring(at + 1))
                                        startEdit(fresh, 0)
                                    } else {
                                        field = v
                                        editor.setText(n, v.text)
                                    }
                                },
                                modifier = Modifier.weight(1f).focusRequester(focus).testTag("block-editor"),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            )
                        } else {
                            Box(Modifier.weight(1f).clickable { if (!editor.isLocked(n)) startEdit(n) }.testTag("block")) {
                                if (editor.isLocked(n)) {
                                    Column {
                                        BlockContent(n.content, model.statuses, api, onTap, null)
                                        Text(
                                            if (n.decryptError) "🔒 Encrypted with a different password" else "🔒 Encrypted. Tap the lock to unlock.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.clickable { if (model.key == null) unlocking = true },
                                        )
                                    }
                                } else if (n.content.isBlank()) {
                                    Text(" ", style = MaterialTheme.typography.bodyLarge)
                                } else {
                                    BlockContent(n.content, model.statuses, api, onTap, { editor.cycleStatus(n) }, onRemoveImage = { src -> editor.removeImage(n, src) })
                                }
                            }
                        }
                    }
                }
                item {
                    TextButtonRow("Add a block") { startEdit(editor.addAtEnd()) }
                }
                editor.backlinks?.backlinks?.takeIf { it.isNotEmpty() }?.let { groups ->
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("${groups.sumOf { it.blocks.size }} linked references", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(groups, key = { "ref:" + it.pageName }) { g ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            // A clear target for opening the page the reference lives on (a calendar for a
                            // journal), away from the links inside the text, which open other pages.
                            Row(Modifier.fillMaxWidth().clickable { onOpen(g.pageName) }.testTag("ref-open"), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isJournalName(g.pageName)) Icons.Filled.CalendarMonth else Icons.AutoMirrored.Filled.Article,
                                    "Open ${g.pageName}",
                                    tint = app.blockseq.android.render.inlineColors().link,
                                    modifier = Modifier.padding(end = 8.dp).size(22.dp),
                                )
                                Text(g.pageName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            g.blocks.forEach { b -> BlockContent(b.content, model.statuses, api, onTap, null, Modifier.padding(start = 8.dp, top = 4.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (unlocking) UnlockDialog(model) { unlocking = false }
    val shot = cameraFile
    if (builtInCamera && shot != null) {
        CameraScreen(shot) { taken ->
            builtInCamera = false
            if (taken) insertPicture(FileProvider.getUriForFile(context, "${context.packageName}.files", shot)) else shot.delete()
        }
    }
}

@Composable
private fun Bullet(n: Node, editor: PageEditor, onToggle: () -> Unit) {
    val hasKids = n.children.isNotEmpty() || editor.isLocked(n)
    Box(Modifier.padding(top = 9.dp).size(16.dp).clickable(enabled = hasKids, onClick = onToggle), contentAlignment = Alignment.Center) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(if (n.collapsed && hasKids) 10.dp else 6.dp)
                .background(if (n.collapsed && hasKids) color.copy(alpha = 0.5f) else color, CircleShape),
        )
    }
}

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    Text(
        "+ $label",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 36.dp, vertical = 12.dp).testTag("add-block"),
    )
}

@Composable
private fun EditBar(
    onOutdent: () -> Unit, onIndent: () -> Unit, onUp: () -> Unit, onDown: () -> Unit,
    onStatus: () -> Unit, onLineBreak: () -> Unit, onDelete: () -> Unit, onDone: () -> Unit,
    picture: @Composable () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
        // Nine buttons are wider than a phone: these scroll sideways, Done stays put on the right.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            IconButton(onClick = onOutdent) { Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent") }
            IconButton(onClick = onIndent) { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent") }
            IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
            IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
            IconButton(onClick = onStatus) { Icon(Icons.Filled.CheckBox, "Next task status") }
            IconButton(onClick = onLineBreak) { Icon(Icons.Filled.KeyboardReturn, "New line in this block") }
            picture()
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete block") }
          }
            IconButton(onClick = onDone, modifier = Modifier.testTag("done")) { Icon(Icons.Filled.Check, "Done") }
        }
    }
}

@Composable
private fun ConflictBar(c: Conflict, editor: PageEditor) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (c is Conflict.Deleted) "This page was deleted on another device while you were editing it."
                else "This page was changed on another device while you were editing it.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c is Conflict.Deleted) OutlinedButton(onClick = editor::leaveDeleted) { Text("Leave it deleted") }
                else OutlinedButton(onClick = editor::useTheirs, modifier = Modifier.testTag("use-theirs")) { Text("Use theirs") }
                Button(onClick = editor::keepMine, modifier = Modifier.testTag("keep-mine")) { Text("Keep mine") }
            }
        }
    }
}

/**
 * Where a newline was just inserted, comparing the text before and after one
 * keyboard edit (which may also commit an autocorrection). Null when the edit
 * added no newline.
 */
internal fun newlineTyped(old: String, new: String): Int? {
    if (new.length <= old.length) return null
    var p = 0
    while (p < old.length && old[p] == new[p]) p++
    var s = 0
    while (s < old.length - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    val inserted = new.substring(p, new.length - s)
    val i = inserted.indexOf('\n')
    return if (i == -1) null else p + i
}

/** The position is inside a ``` code fence that is still open there. */
internal fun insideFence(text: String, at: Int): Boolean =
    text.substring(0, at).lines().count { it.trimStart().startsWith("```") } % 2 == 1

/** A camera icon that offers: take a photo, or choose one from the phone. */
@Composable
private fun PictureButton(onCamera: () -> Unit, onGallery: () -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) { Icon(Icons.Filled.AddAPhoto, "Add a picture") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = { open = false; onCamera() })
            DropdownMenuItem(text = { Text("Choose from phone") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) }, onClick = { open = false; onGallery() })
        }
    }
}

/** A picture's bytes, file name and type from a content uri (camera or photo picker). */
private fun readPicture(context: android.content.Context, uri: android.net.Uri): Triple<ByteArray, String, String> {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    var name: String? = null
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0)
    }
    val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
    val fileName = name?.takeIf { it.contains('.') } ?: "photo_${System.currentTimeMillis()}.$ext"
    val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
    return Triple(bytes, fileName, mime)
}

/**
 * The padlock for #Encrypted blocks, coloured so its state reads at a glance:
 * amber and closed while locked, green and open while unlocked.
 */
@Composable
private fun LockButton(unlocked: Boolean, onLock: () -> Unit, onUnlock: () -> Unit) {
    if (unlocked) {
        IconButton(onClick = onLock, modifier = Modifier.testTag("lock")) { Icon(Icons.Filled.LockOpen, "Lock encrypted blocks", tint = Color(0xFF43A047)) }
    } else {
        IconButton(onClick = onUnlock, modifier = Modifier.testTag("unlock-button")) { Icon(Icons.Filled.Lock, "Unlock encrypted blocks", tint = Color(0xFFFFA000)) }
    }
}

/** "Oct 29th, 2026": the server's journal page names. */
internal fun isJournalName(name: String) = Regex("""^[A-Z][a-z]{2} \d{1,2}(st|nd|rd|th), \d{4}$""").matches(name)
