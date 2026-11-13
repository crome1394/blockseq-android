package app.blockseq.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = when {
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
                actions = {
                    if (model.setup != null) {
                        if (model.key == null) IconButton(onClick = { unlocking = true }) { Icon(Icons.Filled.Lock, "Unlock encrypted blocks") }
                        else IconButton(onClick = { stopEdit(); model.lock() }) { Icon(Icons.Filled.LockOpen, "Lock encrypted blocks") }
                    }
                },
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
                    onDelete = {
                        val above = editor.delete(node)
                        if (above != null) startEdit(above) else stopEdit()
                    },
                    onDone = { stopEdit() },
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
                                onValueChange = { v ->
                                    val old = field.text
                                    // A typed newline splits the block, like Enter on a computer.
                                    if (v.text.length == old.length + 1 && v.selection.collapsed && v.selection.start > 0 && v.text[v.selection.start - 1] == '\n') {
                                        val at = v.selection.start - 1
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
                                    BlockContent(n.content, model.statuses, api, onTap, { editor.cycleStatus(n) })
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
                            Text(g.pageName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.clickable { onOpen(g.pageName) })
                            g.blocks.forEach { b -> BlockContent(b.content, model.statuses, api, onTap, null, Modifier.padding(start = 8.dp, top = 4.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (unlocking) UnlockDialog(model) { unlocking = false }
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
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onOutdent) { Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent") }
            IconButton(onClick = onIndent) { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent") }
            IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
            IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
            IconButton(onClick = onStatus) { Icon(Icons.Filled.CheckBox, "Next task status") }
            IconButton(onClick = onLineBreak) { Icon(Icons.Filled.KeyboardReturn, "New line in this block") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete block") }
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
