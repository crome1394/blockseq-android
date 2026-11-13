package app.blockseq.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import android.view.HapticFeedbackConstants
import app.blockseq.android.editor.Row as OutlineRow
import app.blockseq.android.editor.depthRange
import app.blockseq.android.editor.subtreeIds
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.editor.Conflict
import app.blockseq.android.editor.PageEditor
import app.blockseq.android.outline.Node
import app.blockseq.android.render.BlockContent
import app.blockseq.android.render.Target
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PageScreen(model: AppModel, name: String, onOpen: (String) -> Unit, quickNote: Boolean = false, onBack: () -> Unit) {
    val api = model.api ?: return
    val scope = rememberCoroutineScope()
    val editor = remember(name, model.generation, api) {
        PageEditor(api, name, model.statuses, model.key, scope, model::handle, model::markSynced)
    }
    LaunchedEffect(editor) { editor.load() }
    // The screen's own scope is cancelled as it leaves, so the last save runs in the app's.
    DisposableEffect(editor) { onDispose { model.viewModelScope.launch { editor.save() } } }

    var editing by remember(editor) { mutableStateOf<Node?>(null) }
    val editingId = editing?.id
    var field by remember(editor) { mutableStateOf(TextFieldValue("")) }
    // Live updates: another device saved this page. Not while a block is open: its field
    // holds the old text, and the next keystroke would overwrite theirs without a conflict
    // (the next save reports the conflict instead). Missed ones reload when the block closes.
    var missed by remember(editor) { mutableStateOf(false) }
    LaunchedEffect(editor) {
        api.events().catch { }.collect { ev ->
            val type = ev["type"]?.jsonPrimitive?.content
            val page = ev["name"]?.jsonPrimitive?.content
            val client = ev["client"]?.jsonPrimitive?.content
            if (type == "resync" || (page != null && page.equals(name, true) && client != api.clientId)) {
                if (editing == null) editor.remoteChanged() else missed = true
            }
        }
    }
    var unlocking by remember { mutableStateOf(false) }
    var tagging by remember(editor) { mutableStateOf<Node?>(null) }
    var menuFor by remember(editor) { mutableStateOf<Node?>(null) }
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val onTap: (Target) -> Unit = { t ->
        when (t) {
            is Target.Page -> onOpen(t.name)
            is Target.Url -> runCatching { uri.openUri(t.url) }
            is Target.BlockRef -> scope.launch { runCatching { api.blockPage(t.id) }.getOrNull()?.let(onOpen) }
            is Target.Copy -> copyText(context, t.text)
        }
    }
    fun startEdit(n: Node, caret: Int = n.content.length) {
        editing = n
        field = TextFieldValue(n.content, TextRange(caret.coerceIn(0, n.content.length)))
    }
    fun stopEdit() {
        editing = null
        scope.launch {
            editor.save()
            if (missed) { missed = false; editor.remoteChanged() }
        }
    }
    // The journal's quick-note button: once loaded, type on the last row (a new one unless it is empty).
    LaunchedEffect(editor, editor.loaded) {
        if (quickNote && editor.loaded && editing == null) startEdit(editor.noteRow())
    }

    // Pictures from the phone: uploaded to the workbook's assets, then put at
    // the cursor of the block being edited, or in a new block at the end.
    var uploading by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    fun insertPicture(uri: android.net.Uri) {
        uploading = true
        scope.launch {
            try {
                val (bytes, fileName, mime) = withContext(Dispatchers.IO) { readPicture(context, uri) }
                val path = api.uploadAsset(bytes, fileName, mime)
                // Quarter size in the web app (a phone photo at full size fills the screen there).
                val md = "![${fileName.substringBeforeLast('.')}]($path){:size \"quarter\"}"
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

    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
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
            )
        },
        bottomBar = {
            val node = editing
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
                    onStrike = {
                        // A selection is struck through; without one, the line the cursor is on.
                        val (t, a, b) = if (!field.selection.collapsed) {
                            app.blockseq.android.editor.toggleSelection(field.text, field.selection.min, field.selection.max, "~~")
                        } else {
                            val c = field.selection.start
                            val ls = field.text.lastIndexOf('\n', c - 1) + 1
                            val le = field.text.indexOf('\n', c).let { if (it == -1) field.text.length else it }
                            val line = field.text.substring(ls, le)
                            val prefix = if (ls == 0) model.statuses.prefix(line) else ""
                            val nt = field.text.substring(0, ls) + app.blockseq.android.editor.toggleLine(line, "~~", prefix) + field.text.substring(le)
                            val pos = (c + nt.length - field.text.length).coerceIn(ls, nt.length)
                            Triple(nt, pos, pos)
                        }
                        field = TextFieldValue(t, TextRange(a, b))
                        editor.setText(node, t)
                    },
                    onRule = {
                        // On a line of its own, as the web's /hr: here if the line is empty, otherwise after it.
                        val c = field.selection.start
                        val ls = field.text.lastIndexOf('\n', c - 1) + 1
                        val le = field.text.indexOf('\n', c).let { if (it == -1) field.text.length else it }
                        val line = field.text.substring(ls, le)
                        val t = if (line.isBlank()) field.text.substring(0, ls) + "---" + field.text.substring(le)
                            else field.text.substring(0, ls) + line.trimEnd() + "\n---" + field.text.substring(le)
                        val pos = if (line.isBlank()) ls + 3 else ls + line.trimEnd().length + 4
                        field = TextFieldValue(t, TextRange(pos))
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
            val lines = remember(rows) { treeLevels(rows.map { it.depth }) }
            val view = LocalView.current
            val density = LocalDensity.current
            val step = with(density) { 20.dp.toPx() }
            val listState = rememberLazyListState()
            val currentRows by rememberUpdatedState(rows)
            val currentEditing by rememberUpdatedState(editingId)
            // Where the block being dragged would land, from the finger's place in the list.
            var boxCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            var pageMenu by remember(editor) { mutableStateOf(false) }
            fun dropFor(d: Drag): Drop? {
                val skip = subtreeIds(currentRows, d.node)
                val rest = currentRows.filter { it.node.id !in skip }
                val pos = rest.withIndex().associate { (i, r) -> r.node.id to i }
                val seen = listState.layoutInfo.visibleItemsInfo.filter { it.key in pos }
                if (seen.isEmpty()) return null
                val hit = seen.firstOrNull { it.offset + it.size / 2f > d.fingerY }
                val k = hit?.let { pos.getValue(it.key as String) } ?: (pos.getValue(seen.last().key as String) + 1)
                val lineY = hit?.offset?.toFloat() ?: seen.last().let { (it.offset + it.size).toFloat() }
                val range = depthRange(rest, k) { it.collapsed || editor.isLocked(it) }
                // A new row starts as a sibling of the row above; moving left outdents it.
                val base = if (d.isNew) rest.getOrNull(k - 1)?.depth ?: 0 else d.depth
                val depth = (base + Math.round(d.dx / step)).coerceIn(range)
                return Drop(rest.getOrNull(k - 1), depth, lineY)
            }
            var drag by remember(editor) { mutableStateOf<Drag?>(null) }
            var gapPressed by remember(editor) { mutableStateOf<String?>(null) }
            // The bin, shown in the bottom-right corner while a block is lifted.
            val binR = with(density) { 28.dp.toPx() }
            val binM = with(density) { 20.dp.toPx() }
            fun overBin(d: Drag): Boolean {
                val v = listState.layoutInfo.viewportSize
                val c = Offset(v.width - binM - binR, v.height - binM - binR)
                return (Offset(d.fingerX, d.fingerY) - c).getDistance() < binR * 2f
            }
            val onBin = drag?.let { overBin(it) } == true
            LaunchedEffect(onBin) {
                if (drag != null) view.performHapticFeedback(if (onBin) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
            }
            val drop = drag?.takeUnless { onBin }?.let { dropFor(it) }
            // A light tick each time the landing place changes.
            var lastDrop by remember { mutableStateOf<Pair<String?, Int>?>(null) }
            LaunchedEffect(drop?.prev?.node?.id, drop?.depth, drag != null) {
                val now = drop?.let { it.prev?.node?.id to it.depth }
                if (drag != null && lastDrop != null && now != lastDrop) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastDrop = if (drag != null) now else null
            }
            // Near the top or bottom edge, the page scrolls under the finger.
            LaunchedEffect(drag != null) {
                while (drag != null) {
                    val d = drag ?: break
                    val h = listState.layoutInfo.viewportSize.height
                    val edge = with(density) { 72.dp.toPx() }
                    val by = when {
                        overBin(d) -> 0f
                        d.fingerY < edge -> -(edge - d.fingerY) / 4
                        d.fingerY > h - edge -> (d.fingerY - (h - edge)) / 4
                        else -> 0f
                    }
                    if (by != 0f) listState.scrollBy(by)
                    kotlinx.coroutines.delay(16)
                }
            }
            Box(Modifier.fillMaxSize().onGloballyPositioned { boxCoords = it }) {
            LazyColumn(Modifier.fillMaxSize().testTag("outline").pointerInput(editor) {
                // Press and hold a block: let go to open its menu, or drag it to move it
                // (sideways changes its depth). Taken in the Initial pass so the list
                // does not scroll and the block does not count it as a tap.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The thin strip above each block: a tap there adds a row in between.
                    val gapPx = 8.dp.toPx()
                    fun gapAt(y: Float) = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + gapPx }
                        ?.let { item -> currentRows.firstOrNull { it.node.id == item.key } }
                    gapAt(down.position.y)?.let { r ->
                        // Watched in the Initial pass so the row next to it (whose touch area
                        // is stretched to 48 dp) does not take the tap; a drag still scrolls.
                        gapPressed = r.node.id
                        try {
                            while (true) {
                                val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                if (!ch.pressed) {
                                    if (ch.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
                                    ch.consume()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    startEdit(editor.insertBeside(r.node, below = false))
                                    break
                                }
                            }
                        } finally { gapPressed = null }
                        return@awaitEachGesture
                    }
                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { held.position.y >= it.offset && held.position.y < it.offset + it.size } ?: return@awaitEachGesture
                    val row = currentRows.firstOrNull { it.node.id == item.key } ?: return@awaitEachGesture
                    if (row.node.id == currentEditing) return@awaitEachGesture
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    var moved = false
                    var released = false
                    drag = Drag(row.node, row.depth, held.position.y - item.offset, held.position.y, 0f, held.position.x)
                    try {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == held.id } ?: break
                            ch.consume()
                            if (!ch.pressed) { released = true; break }
                            val off = ch.position - held.position
                            if (!moved && off.getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) drag = drag?.copy(fingerY = ch.position.y, dx = off.x, fingerX = ch.position.x)
                        }
                        val d = drag
                        if (released && !moved) menuFor = row.node
                        else if (released && d != null && overBin(d)) {
                            // Dropped on the bin: deleted with its children, with Undo for a few seconds.
                            val at = editor.placeOf(d.node)
                            editor.delete(d.node)
                            view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                            scope.launch { editor.save() }
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val r = snackbar.showSnackbar("Block deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
                                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    editor.restore(d.node, at)
                                    editor.save()
                                }
                            }
                        }
                        else if (released && d != null) {
                            val to = dropFor(d)
                            if (to != null && editor.moveAfter(d.node, to.prev?.node, to.prev?.depth ?: -1, to.depth)) {
                                view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch { editor.save() }
                            } else view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
                        }
                    } finally { drag = null }
                }
            }, state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
                items(rows.size, key = { rows[it].node.id }) { i ->
                    val row = rows[i]
                    val n = row.node
                    val nextDepth = rows.getOrNull(i + 1)?.depth ?: -1
                    val guide = MaterialTheme.colorScheme.outlineVariant
                    val lifted = drag?.node === n
                    val carried = drag != null && !lifted && drag?.let { d -> n.id in subtreeIds(rows, d.node) } == true
                    Column(
                        Modifier.then(if (lifted) Modifier.zIndex(1f).graphicsLayer {
                            val d = drag ?: return@graphicsLayer
                            val at = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == n.id }?.offset ?: 0
                            translationY = d.fingerY - d.grabY - at
                            translationX = d.dx
                            alpha = if (onBin) 0.4f else 1f
                            shadowElevation = 12.dp.toPx()
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            clip = true
                        }.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                            .graphicsLayer { alpha = if (carried) 0.35f else 1f }
                            .treeLines(row.depth, nextDepth, lines[i], guide),
                    ) {
                    // Tap the thin gap above a block to add a row there.
                    GapAbove(row.depth, gapPressed == n.id)
                    Row(Modifier.fillMaxWidth().padding(start = (12 + row.depth * 20).dp, end = 12.dp, bottom = 4.dp)) {
                        Bullet(n, editor, numberOf(n)) { editor.toggleCollapse(n) }
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
                            // Tap to edit; press and hold to add a tag (any block, children too).
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
                                    BlockContent(n.content, model.statuses, api, onTap, { editor.cycleStatus(n) }, onRemoveImage = { src -> editor.removeImage(n, src) }, props = n.props.toMap(), depth = row.depth)
                                }
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
            // The page's ⋯ button: New row (tap: at the end; drag: to a place), pictures, lock.
            if (editingId == null) PageMenuFab(
                open = pageMenu, onToggle = { pageMenu = !pageMenu }, dragging = drag?.isNew == true,
                locked = if (model.setup == null) null else model.key == null,
                onNewRowTap = { pageMenu = false; startEdit(editor.addAtEnd()) },
                onNewRowDrag = { phase, pos ->
                    when (phase) {
                        0 -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); drag = Drag(NEW_ROW, 0, 0f, pos.y, 0f, pos.x, isNew = true) }
                        1 -> drag = drag?.let { it.copy(fingerY = pos.y, fingerX = pos.x, dx = minOf(0f, pos.x - (it.startX ?: pos.x)), startX = it.startX ?: pos.x) }
                        2 -> {
                            val d = drag
                            drag = null
                            pageMenu = false
                            if (d != null && !overBin(d)) {
                                val to = dropFor(d)
                                val fresh = to?.let { editor.newRowAfter(it.prev?.node, it.prev?.depth ?: -1, it.depth) }
                                if (fresh != null) {
                                    view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                                    startEdit(fresh)
                                } else view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
                            }
                        }
                        else -> drag = null
                    }
                },
                toBox = { c, p -> boxCoords?.localPositionOf(c, p) ?: p },
                onCamera = { pageMenu = false; openCamera() },
                onGallery = { pageMenu = false; choosePhoto() },
                // Save first: encrypted children are sealed with the key, which Lock wipes.
                onLock = { pageMenu = false; editing = null; scope.launch { editor.save(); model.lock() } },
                onUnlock = { pageMenu = false; unlocking = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
            // The new row being dragged in: a chip under the finger.
            drag?.takeIf { it.isNew }?.let { d ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 8.dp,
                    color = if (onBin) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((d.fingerX - 140.dp.toPx()).toInt(), (d.fingerY - 56.dp.toPx()).toInt()) },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New row", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (drag != null) {
                val grow by androidx.compose.animation.core.animateFloatAsState(if (onBin) 1.25f else 1f, label = "bin")
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                        .graphicsLayer { scaleX = grow; scaleY = grow }
                        .background(if (onBin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .testTag("drag-bin"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Delete, "Drop here to delete", tint = if (onBin) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The line where the dragged block would land, indented to its new depth.
            drop?.let { to ->
                val color = MaterialTheme.colorScheme.primary
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val x = (12 + to.depth * 20).dp.toPx()
                    drawCircle(color, 4.dp.toPx(), Offset(x + 4.dp.toPx(), to.lineY))
                    drawLine(color, Offset(x + 4.dp.toPx(), to.lineY), Offset(size.width - 12.dp.toPx(), to.lineY), 3.dp.toPx())
                }
            }
            }
        }
    }
    if (unlocking) UnlockDialog(model) { unlocking = false }
    menuFor?.let { n ->
        BlockMenu(editor, n, model.store.server, onTag = { menuFor = null; tagging = n }, onAddRow = { fresh -> menuFor = null; startEdit(fresh) }, onClose = { menuFor = null; scope.launch { editor.save() } })
    }
    tagging?.let { n ->
        TagDialog(model, n.content, onPick = { tag -> editor.addTag(n, tag); tagging = null; scope.launch { editor.save() } }, onClose = { tagging = null })
    }
    val shot = cameraFile
    if (builtInCamera && shot != null) {
        CameraScreen(shot) { taken ->
            builtInCamera = false
            if (taken) insertPicture(FileProvider.getUriForFile(context, "${context.packageName}.files", shot)) else shot.delete()
        }
    }
}

/** A block being dragged: where the finger is (in the list) and how far sideways it went. */
private data class Drag(
    val node: Node, val depth: Int, val grabY: Float, val fingerY: Float, val dx: Float, val fingerX: Float,
    /** A new row from the ⋯ menu (not a block of the page), and where the finger was when it started. */
    val isNew: Boolean = false, val startX: Float? = null,
)

/** Stands in for the new row while it is dragged in: it is not on the page, so nothing is skipped or lifted. */
private val NEW_ROW = Node("new-row", "")

/**
 * The page's floating ⋯: a column of actions above it. *New row* adds one at
 * the end when tapped; dragged, it follows the finger and lands where the
 * drop line shows ([onNewRowDrag]: 0 start, 1 move, 2 drop, 3 cancel; places
 * in the page box's coordinates via [toBox]).
 */
@Composable
private fun PageMenuFab(
    open: Boolean, onToggle: () -> Unit, dragging: Boolean, locked: Boolean?,
    onNewRowTap: () -> Unit, onNewRowDrag: (Int, Offset) -> Unit,
    toBox: (androidx.compose.ui.layout.LayoutCoordinates, Offset) -> Offset,
    onCamera: () -> Unit, onGallery: () -> Unit, onLock: () -> Unit, onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.graphicsLayer { alpha = if (dragging) 0.3f else 1f }) {
                var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
                MenuAction("New row (tap, or drag to a place)", Icons.Filled.Add, "menu-new-row", Modifier.onGloballyPositioned { coords = it }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var started = false
                        try {
                            while (true) {
                                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                val c = coords ?: break
                                if (!ch.pressed) { if (started) onNewRowDrag(2, toBox(c, ch.position)) else onNewRowTap(); started = false; break }
                                if (!started && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) { started = true; onNewRowDrag(0, toBox(c, ch.position)) }
                                if (started) onNewRowDrag(1, toBox(c, ch.position))
                            }
                        } finally { if (started) onNewRowDrag(3, Offset.Zero) }
                    }
                })
                MenuAction("Take a photo", Icons.Filled.PhotoCamera, "menu-camera", Modifier.clickable(onClick = onCamera))
                MenuAction("Choose a picture", Icons.Filled.PhotoLibrary, "menu-gallery", Modifier.clickable(onClick = onGallery))
                if (locked == true) MenuAction("Unlock encrypted blocks", Icons.Filled.Lock, "menu-unlock", Modifier.clickable(onClick = onUnlock), tint = Color(0xFFFFA000))
                if (locked == false) MenuAction("Lock encrypted blocks", Icons.Filled.LockOpen, "menu-lock", Modifier.clickable(onClick = onLock), tint = Color(0xFF43A047))
            }
        }
        androidx.compose.material3.FloatingActionButton(onClick = onToggle, modifier = Modifier.testTag("page-menu")) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.MoreHoriz, if (open) "Close" else "Page actions")
        }
    }
}

/** One action in the ⋯ column: its label in a small card, and its round icon (the whole row is the target). */
@Composable
private fun MenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, target: Modifier, tint: Color = Color.Unspecified) {
    Row(target.testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = MaterialTheme.shapes.small, tonalElevation = 3.dp, shadowElevation = 2.dp) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 3.dp, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSecondaryContainer else tint) }
        }
    }
}

/** Where it would land: after [prev] (none: first on the page), at [depth]; [lineY] in the list. */
private data class Drop(val prev: OutlineRow?, val depth: Int, val lineY: Float)

/** The thin strip above a block (a tap there, found by the list, adds a row); a faint + line while pressed. */
@Composable
private fun GapAbove(depth: Int, pressed: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(8.dp).drawBehind {
            if (!pressed) return@drawBehind
            val x = (12 + depth * 20).dp.toPx()
            val y = size.height / 2
            drawLine(color.copy(alpha = 0.6f), Offset(x + 16.dp.toPx(), y), Offset(size.width - 12.dp.toPx(), y), 2.dp.toPx())
            drawLine(color, Offset(x + 2.dp.toPx(), y), Offset(x + 12.dp.toPx(), y), 2.dp.toPx())
            drawLine(color, Offset(x + 7.dp.toPx(), y - 5.dp.toPx()), Offset(x + 7.dp.toPx(), y + 5.dp.toPx()), 2.dp.toPx())
        },
    )
}

@Composable
private fun Bullet(n: Node, editor: PageEditor, number: Int?, onToggle: () -> Unit) {
    val hasKids = n.children.isNotEmpty() || editor.isLocked(n)
    if (number != null) {
        // A numbered list item (logseq.order-list-type:: number), as in the web app.
        Text("$number.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).clickable(enabled = hasKids, onClick = onToggle))
        return
    }
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
    onStatus: () -> Unit, onLineBreak: () -> Unit, onStrike: () -> Unit, onRule: () -> Unit, onDelete: () -> Unit, onDone: () -> Unit,
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
            IconButton(onClick = onStrike, modifier = Modifier.testTag("edit-strike")) { Icon(Icons.Filled.FormatStrikethrough, "Strikethrough") }
            IconButton(onClick = onRule, modifier = Modifier.testTag("edit-rule")) { Icon(Icons.Filled.HorizontalRule, "Horizontal line") }
            picture()
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete block") }
          }
            IconButton(onClick = onDone, modifier = Modifier.testTag("done")) { Icon(Icons.Filled.Check, "Done") }
        }
    }
}

@Composable
private fun ConflictBar(c: Conflict, editor: PageEditor) {
    var comparing by remember(c) { mutableStateOf(false) }
    if (comparing) ConflictDiffDialog(editor) { comparing = false }
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
            if (c is Conflict.Changed) androidx.compose.material3.TextButton(onClick = { comparing = true }, modifier = Modifier.testTag("see-diff")) {
                Text("See the differences", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/**
 * The two versions side by side as one list: lines only on the other device
 * in red with −, lines only on this phone in green with +, the rest dimmed.
 * Long unchanged stretches fold to their edges.
 */
@Composable
private fun ConflictDiffDialog(editor: PageEditor, onClose: () -> Unit) {
    val lines = remember(editor.conflict, editor.revision) { editor.conflictDiff() } ?: run { onClose(); return }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val red = if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    val green = if (dark) Color(0xFF81C784) else Color(0xFF1B5E20)
    // Drag the title bar to move the card around the screen (the list below still scrolls).
    var offset by remember { mutableStateOf(Offset.Zero) }
    // A full-screen window with the card inside, so the card can go anywhere on the screen.
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onClose).padding(16.dp), contentAlignment = Alignment.Center) {
    androidx.compose.material3.Card(
        Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.x.toInt(), offset.y.toInt()) }
            .fillMaxWidth().heightIn(max = 520.dp).clickable(interactionSource = null, indication = null) {},
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(8.dp),
    ) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) { detectDragGestures { ch, by -> ch.consume(); offset += by } }.testTag("diff-handle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Differences", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                Icon(Icons.Filled.DragIndicator, "Drag to move", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onClose, modifier = Modifier.testTag("diff-close")) { Icon(Icons.Filled.Close, "Close") }
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text("− only on the other device   + only on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (lines.none { it.kind != app.blockseq.android.editor.DiffLine.Kind.Same }) Text("The text is the same; only hidden details (such as properties or folding) differ.")
                androidx.compose.foundation.lazy.LazyColumn(Modifier.testTag("diff-list")) {
                    val folded = foldSame(lines)
                    items(folded.size) { i ->
                        val l = folded[i]
                        val (mark, color, bg) = when (l?.kind) {
                            null -> Triple(" ", MaterialTheme.colorScheme.onSurfaceVariant, Color.Transparent)
                            app.blockseq.android.editor.DiffLine.Kind.Theirs -> Triple("−", red, red.copy(alpha = 0.12f))
                            app.blockseq.android.editor.DiffLine.Kind.Mine -> Triple("+", green, green.copy(alpha = 0.12f))
                            else -> Triple(" ", MaterialTheme.colorScheme.onSurfaceVariant, Color.Transparent)
                        }
                        // The words that changed within the row get a stronger mark.
                        val shown = if (l == null) androidx.compose.ui.text.AnnotatedString("  ⋯") else androidx.compose.ui.text.buildAnnotatedString {
                            append("$mark ")
                            val start = length
                            append(l.text)
                            l.changed.forEach { r -> addStyle(androidx.compose.ui.text.SpanStyle(background = color.copy(alpha = 0.35f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), start + r.first, start + r.last + 1) }
                        }
                        Text(
                            shown,
                            color = color,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onClose) { Text("Close") }
                androidx.compose.material3.TextButton(onClick = { onClose(); editor.useTheirs() }) { Text("Use theirs") }
                androidx.compose.material3.TextButton(onClick = { onClose(); editor.keepMine() }) { Text("Keep mine") }
            }
    }
    }
    }
    }
}

/** Unchanged runs longer than 5 lines keep 2 at each end; null marks the fold. */
private fun foldSame(lines: List<app.blockseq.android.editor.DiffLine>): List<app.blockseq.android.editor.DiffLine?> {
    val out = mutableListOf<app.blockseq.android.editor.DiffLine?>()
    var i = 0
    while (i < lines.size) {
        if (lines[i].kind != app.blockseq.android.editor.DiffLine.Kind.Same) { out += lines[i]; i++; continue }
        var j = i
        while (j < lines.size && lines[j].kind == app.blockseq.android.editor.DiffLine.Kind.Same) j++
        val run = lines.subList(i, j)
        if (run.size > 5) { out += run.take(2); out += null; out += run.takeLast(2) } else out += run
        i = j
    }
    return out
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


/** "Oct 29th, 2026": the server's journal page names. */
internal fun isJournalName(name: String) = Regex("""^[A-Z][a-z]{2} \d{1,2}(st|nd|rd|th), \d{4}$""").matches(name)

/** The number of a numbered list item: its place among the numbered siblings right before it. */
internal fun numberOf(n: Node): Int? {
    if (n.props[app.blockseq.android.editor.NUMBERED] != "number") return null
    val siblings = n.parent?.children ?: return 1
    var count = 1
    var i = siblings.indexOf(n) - 1
    while (i >= 0 && siblings[i].props[app.blockseq.android.editor.NUMBERED] == "number") { count++; i-- }
    return count
}

/**
 * Lines joining the bullets, as in the web app: from a block's dot down
 * through its children, ending at the dot of the last one.
 */
private fun Modifier.treeLines(depth: Int, nextDepth: Int, passing: BooleanArray, color: androidx.compose.ui.graphics.Color) = drawBehind {
    val stroke = 1.dp.toPx()
    fun x(level: Int) = (12 + level * 20 + 8).dp.toPx()
    val dotY = 25.dp.toPx() // gap strip 8 + bullet padding 9 + half its 16
    for (level in 0 until depth - 1) {
        // A later sibling of this row's branch still comes: the line runs through.
        if (passing[level]) drawLine(color, Offset(x(level), 0f), Offset(x(level), size.height), stroke)
    }
    if (depth > 0) {
        // The parent's line comes down and bends into this row's dot, with a rounded corner.
        val px = x(depth - 1)
        val r = 8.dp.toPx()
        val end = x(depth) - 6.dp.toPx()
        if (passing[depth - 1]) drawLine(color, Offset(px, 0f), Offset(px, size.height), stroke)
        val elbow = androidx.compose.ui.graphics.Path().apply {
            moveTo(px, 0f)
            lineTo(px, dotY - r)
            quadraticTo(px, dotY, px + r, dotY)
            lineTo(end, dotY)
        }
        drawPath(elbow, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    }
    // Open children below: a line starts at this dot.
    if (nextDepth > depth) drawLine(color, androidx.compose.ui.geometry.Offset(x(depth), dotY), androidx.compose.ui.geometry.Offset(x(depth), size.height), stroke)
}

/** For each row, which ancestor lines carry on below it (a later row one level deeper comes before the branch ends). */
internal fun treeLevels(depths: List<Int>): List<BooleanArray> {
    val max = (depths.maxOrNull() ?: 0) + 1
    val flag = BooleanArray(max)
    val out = MutableList(depths.size) { BooleanArray(0) }
    for (i in depths.indices.reversed()) {
        val d = depths[i]
        out[i] = BooleanArray(max) { it < d && flag[it] }
        for (l in d until max) flag[l] = false
        if (d >= 1) flag[d - 1] = true
    }
    return out
}
