package app.blockseq.android.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.blockseq.android.editor.ALIGN
import app.blockseq.android.editor.COLOR
import app.blockseq.android.editor.HEADING
import app.blockseq.android.editor.NUMBERED
import app.blockseq.android.editor.PageEditor
import app.blockseq.android.outline.Node
import app.blockseq.android.render.SWATCHES
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Press and hold a block: the web app's block menu, laid out for a phone as
 * a bottom sheet. Colours and headings apply at once; the sheet stays open
 * for those so a few can be tried. Actions close it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockMenu(editor: PageEditor, n: Node, server: String, onTag: () -> Unit, onAddRow: (Node) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    val clipboard = LocalClipboardManager.current
    // Opens half way; dragging anywhere on it (not just the handle) pulls it up or down.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    fun copy(text: String, what: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "$what copied", Toast.LENGTH_SHORT).show()
        onClose()
    }
    // Read through the editor's revision so the sheet shows the current state.
    editor.revision
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, modifier = Modifier.testTag("block-menu")) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            // Colour: the row highlight.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SWATCHES.forEach { (name, rgb) ->
                    val selected = n.props[COLOR] == name
                    Box(
                        Modifier.size(34.dp).background(Color(rgb), CircleShape)
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { editor.setProp(n, COLOR, if (selected) null else name) }
                            .testTag("color-$name"),
                    )
                }
                Box(Modifier.size(34.dp).clickable { editor.setProp(n, COLOR, null) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Block, "No colour", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Heading: H1–H6 (Markdown), Auto (by depth), none.
            val current = Regex("^(?:\\S+ )?(#{1,6}) ").find(n.content)?.groupValues?.get(1)?.length ?: 0
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..6).forEach { level ->
                    FilterChip(selected = current == level, onClick = { editor.setHeading(n, if (current == level) 0 else level) }, label = { Text("H$level") })
                }
                FilterChip(selected = n.props[HEADING] == "true", onClick = { editor.setHeading(n, 0, auto = n.props[HEADING] != "true") }, label = { Text("Auto") })
                FilterChip(selected = current == 0 && n.props[HEADING] == null, onClick = { editor.setHeading(n, 0) }, label = { Text("None") })
            }
            // Alignment.
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                val align = n.props[ALIGN] ?: "left"
                listOf<Triple<String, ImageVector, String>>(
                    Triple("left", Icons.AutoMirrored.Filled.FormatAlignLeft, "Align left"),
                    Triple("center", Icons.Filled.FormatAlignCenter, "Align centre"),
                    Triple("right", Icons.AutoMirrored.Filled.FormatAlignRight, "Align right"),
                ).forEach { (value, icon, label) ->
                    IconToggleButton(checked = align == value, onCheckedChange = { editor.setProp(n, ALIGN, if (value == "left") null else value) }) {
                        Icon(icon, label, tint = if (align == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            // Moving: the sheet stays open so a block can be stepped a few places.
            val parent = n.parent
            val i = parent?.children?.indexOf(n) ?: -1
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MoveButton(Icons.Filled.ArrowUpward, "Move up", "menu-up", i > 0) { editor.move(n, -1); tick() }
                MoveButton(Icons.Filled.ArrowDownward, "Move down", "menu-down", parent != null && i < parent.children.size - 1) { editor.move(n, 1); tick() }
                MoveButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent", "menu-outdent", parent?.parent != null) { editor.outdent(n); tick() }
                MoveButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent", "menu-indent", i > 0 && !editor.isLocked(parent!!.children[i - 1])) { editor.indent(n); tick() }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Item(Icons.Filled.VerticalAlignTop, "Add row above", "menu-add-above") { onAddRow(editor.insertBeside(n, below = false)) }
            Item(Icons.Filled.VerticalAlignBottom, "Add row below", "menu-add-below") { onAddRow(editor.insertBeside(n, below = true)) }
            Item(Icons.AutoMirrored.Filled.Label, "Add tag…", "menu-tag") { onTag() }
            Item(Icons.Filled.FormatStrikethrough, "Strikethrough", "menu-strike") { editor.toggleStrike(n); onClose() }
            Item(Icons.Filled.Schedule, "Add timestamp") {
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                editor.addTimestamp(n, "[[${journalName(LocalDate.now())}]] $time")
                onClose()
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Item(Icons.Filled.ContentCopy, "Copy block ref") { copy("((${n.id}))", "Block ref") }
            Item(Icons.Filled.Link, "Copy block URL") { copy("${server.trimEnd('/')}/#/page/${Uri.encode(editor.name)}?b=${n.id}", "Block URL") }
            Item(Icons.Filled.Notes, "Copy as Markdown") { copy(editor.export(n, plain = false), "Markdown") }
            Item(Icons.Filled.Notes, "Copy as plain text") { copy(editor.export(n, plain = true), "Text") }
            Item(Icons.Filled.Share, "Share…") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, editor.export(n, plain = true))
                context.startActivity(Intent.createChooser(send, "Share block"))
                onClose()
            }
            Item(Icons.Filled.ContentCut, "Cut") {
                clipboard.setText(AnnotatedString(editor.export(n, plain = false)))
                editor.delete(n)
                Toast.makeText(context, "Block cut", Toast.LENGTH_SHORT).show()
                onClose()
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Item(Icons.Filled.FormatListNumbered, if (n.props[NUMBERED] == "number") "Turn off number list" else "Number list") { editor.toggleNumbered(n); onClose() }
            Item(Icons.Filled.Delete, "Delete block", "menu-delete", danger = true) { editor.delete(n); onClose() }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, tag: String = "", danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, null, tint = color) },
        modifier = Modifier.clickable(onClick = onClick).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
