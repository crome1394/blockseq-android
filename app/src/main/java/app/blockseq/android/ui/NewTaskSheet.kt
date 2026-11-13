package app.blockseq.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.editor.PageEditor
import app.blockseq.android.render.statusColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Tasks tab's +: a card to write a task, pick its status and, if wanted,
 * a due date. It goes on the last row of today's journal (as the quick note
 * does). A due date is a link to that day (`📅 [[Nov 20th, 2026]]`), so the
 * task also shows in that day's linked references, in the web app too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(model: AppModel, onDone: (added: Boolean) -> Unit) {
    val statuses = model.statuses.list.filterNot { it.done }
    var text by remember { mutableStateOf("") }
    var word by remember { mutableStateOf((statuses.firstOrNull { it.word == "TODO" } ?: statuses.firstOrNull())?.word) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val today = journalName(LocalDate.now())
    ModalBottomSheet(onDismissRequest = { if (!busy) onDone(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("New task · $today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!busy) onDone(false) }) { Icon(Icons.Filled.Close, "Close") }
            }
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("What needs doing?") }, minLines = 2, maxLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("task-text"),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                statuses.forEach { st ->
                    val (fg, bg) = statusColor(st)
                    FilterChip(
                        selected = word == st.word, onClick = { word = st.word }, label = { Text(st.label) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = fg, selectedContainerColor = bg, selectedLabelColor = fg),
                        modifier = Modifier.testTag("task-status-${st.word.lowercase()}"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picking = true }, modifier = Modifier.testTag("task-due")) {
                    Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.padding(end = 6.dp))
                    Text(due?.let { "Due ${journalName(it)}" } ?: "Add a due date")
                }
                if (due != null) IconButton(onClick = { due = null }) { Icon(Icons.Filled.Close, "No due date") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(enabled = text.isNotBlank() && !busy, modifier = Modifier.testTag("task-add"), onClick = {
                    val api = model.api ?: return@Button
                    busy = true
                    val body = text.trim().replace('\n', ' ') + (due?.let { " 📅 [[${journalName(it)}]]" } ?: "")
                    scope.launch {
                        val editor = PageEditor(api, today, model.statuses, model.key, scope, model::handle, model::markSynced)
                        editor.load()
                        editor.setText(editor.noteRow(), model.statuses.with(body, word))
                        editor.save()
                        busy = false
                        // Saved once is enough: a slow save leaves the editor dirty with a follow-up
                        // save queued, which must not read as a failure (a retry would add it twice).
                        if (editor.conflict == null && editor.lastSaved != null) onDone(true)
                        else error = editor.error ?: "Could not add it (the day changed elsewhere); try again."
                    }
                }) { Text(if (busy) "Adding…" else "Add task") }
            }
        }
    }
    if (picking) {
        // The picker works in UTC midnights.
        val state = rememberDatePickerState(initialSelectedDateMillis = (due ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
