package app.blockseq.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.render.inlineColors

/**
 * Press and hold a block: pick one of the workbook's tags, or create a new
 * one from what you type. [onPick] gets the tag name without the #.
 */
@Composable
fun TagDialog(model: AppModel, blockText: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        val api = model.api ?: return@LaunchedEffect
        // The tags used anywhere in the workbook, most used first.
        runCatching { api.pages() }.onSuccess { pages ->
            // #task is not offered: a task status adds and removes it by itself.
            tags = pages.flatMap { it.tags }.filterNot { it.equals("task", true) }.groupingBy { it.lowercase() }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { e -> pages.flatMap { it.tags }.first { it.lowercase() == e.key } }
        }.onFailure(model::handle)
    }
    val q = query.trim().removePrefix("#")
    val shown = tags.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }.take(50)
    val exists = tags.any { it.equals(q, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a tag") },
        text = {
            Column {
                Text(blockText.lineSequence().first().take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                OutlinedTextField(
                    query, { query = it }, singleLine = true, label = { Text("Find or create a tag") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 6.dp)) {
                    if (q.isNotEmpty() && !exists) item {
                        Text(
                            "New tag #$q", color = inlineColors().link,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = 12.dp),
                        )
                    }
                    items(shown, key = { it }) { tag ->
                        Text(
                            "#$tag", color = inlineColors().tag,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(tag) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
