package app.blockseq.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.BuildConfig
import app.blockseq.android.data.Workbook
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.blockseq.android.scratch.ScratchpadSync
import app.blockseq.android.scratch.ScratchpadWidget
import app.blockseq.android.scratch.ScratchpadWidgetReceiver
import app.blockseq.android.scratch.WidgetLookEditor
import app.blockseq.android.scratch.WidgetPrefs
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) { General("General"), Display("Display"), Code("Code"), Widget("Widget"), Security("Security") }

/**
 * Settings as a card over the current screen, in tabs like the web app's
 * categories; swipe sideways (or tap a tab) to move between them.
 */
@Composable
fun SettingsScreen(model: AppModel, onBack: () -> Unit) {
    var workbooks by remember { mutableStateOf<List<Workbook>>(emptyList()) }
    var unlocking by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    LaunchedEffect(model.api) { runCatching { model.api?.workbooks() }.getOrNull()?.let { workbooks = it } }
    val tabs = SettingsTab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // A dialog is its own window: test tags must be turned into view ids here too.
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.clickable(interactionSource = null, indication = null, onClick = onBack).safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(0.92f).clickable(interactionSource = null, indication = null) {}.testTag("settings-card"), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings-close")) { Icon(Icons.Filled.Close, "Close") }
            }
            ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 12.dp, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t.label) }, modifier = Modifier.testTag("settings-tab-${t.name.lowercase()}"))
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tabs[page]) {
                        SettingsTab.General -> {
                            Section("Server")
                            Text(model.store.server, style = MaterialTheme.typography.bodyLarge)
                            Text("Paired as “${model.store.deviceName}”. Revoke this phone any time in the web app: Settings → Security.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Section("Workbook")
                            workbooks.forEach { w ->
                                Row(Modifier.fillMaxWidth().clickable { model.switchWorkbook(w.id) }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = model.store.workbook == w.id, onClick = { model.switchWorkbook(w.id) })
                                    Text(w.name)
                                }
                            }
                            HorizontalDivider()
                            Text("Blockseq for Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsTab.Display -> {
                            Section("Controls")
                            Row(Modifier.fillMaxWidth().clickable { model.changeControlsAtBottom(!model.controlsAtBottom) }.testTag("controls-bottom"), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Controls at the bottom")
                                    Text("The picture viewer's buttons (share, save, remove) sit along the bottom, within reach of your thumb.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = model.controlsAtBottom, onCheckedChange = { model.changeControlsAtBottom(it) })
                            }
                            HorizontalDivider()
                            Section("Search and filter fields")
                            var bottom by remember { mutableStateOf(model.store.searchAtBottom) }
                            listOf(false to "Top", true to "Bottom (easier to reach with one hand)").forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clickable { bottom = value; model.store.searchAtBottom = value }.testTag("search-${if (value) "bottom" else "top"}"), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = bottom == value, onClick = { bottom = value; model.store.searchAtBottom = value })
                                    Text(label)
                                }
                            }
                            HorizontalDivider()
                            Section("First day of the week")
                            var weekStart by remember { mutableStateOf(model.store.weekStart) }
                            listOf(7 to "Sunday", 1 to "Monday", 6 to "Saturday").forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clickable { weekStart = value; model.store.weekStart = value }.testTag("week-$value"), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = weekStart == value, onClick = { weekStart = value; model.store.weekStart = value })
                                    Text(label)
                                }
                            }
                        }
                        SettingsTab.Code -> {
                            Section("Code blocks")
                            val fold = model.codeFold
                            Row(Modifier.fillMaxWidth().clickable { model.changeCodeFold(fold.copy(on = !fold.on)) }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Fold long code blocks")
                                    Text("Long code blocks start folded, showing their first lines and a Show all bar (as in the web app).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = fold.on, onCheckedChange = { model.changeCodeFold(fold.copy(on = it)) })
                            }
                            if (fold.on) {
                                NumberSetting("Fold code blocks longer than (lines)", fold.lines, 2..1000) { model.changeCodeFold(fold.copy(lines = it)) }
                                NumberSetting("Lines to show when folded", fold.show, 1..200) { model.changeCodeFold(fold.copy(show = it)) }
                            }
                        }
                        SettingsTab.Widget -> {
                            Section("Scratchpad widget")
                            Text("The Scratchpad page in Main on your home screen: date-stamped notes, readable and addable offline. These settings apply to every Scratchpad widget at once.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                                    val provider = android.content.ComponentName(context, ScratchpadWidgetReceiver::class.java)
                                    if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(provider, null, null)
                                    else android.widget.Toast.makeText(context, "Add it from the home screen's widget list", android.widget.Toast.LENGTH_LONG).show()
                                }, modifier = Modifier.testTag("add-widget")) { Text("Add to home screen") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val ok = ScratchpadSync.sync(context)
                                        android.widget.Toast.makeText(context, if (ok) "Scratchpad synced" else "Could not sync (offline?)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Sync now") }
                            }
                            HorizontalDivider()
                            var look by remember { mutableStateOf(WidgetPrefs.load(context)) }
                            WidgetLookEditor(look) {
                                look = it
                                WidgetPrefs.save(context, it)
                                scope.launch { ScratchpadWidget.refresh(context) }
                            }
                        }
                        SettingsTab.Security -> {
                            Section("Encrypted blocks")
                            when {
                                model.setup == null -> Text("No encryption password is set on the server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                model.key == null -> OutlinedButton(onClick = { unlocking = true }, modifier = Modifier.testTag("settings-unlock")) { Text("Unlock…") }
                                else -> OutlinedButton(onClick = model::lock) { Text("Lock now") }
                            }
                            HorizontalDivider()
                            Section("This phone")
                            OutlinedButton(onClick = { confirmUnpair = true }) { Text("Unpair this phone") }
                        }
                    }
                }
            }
        }
    }
    if (unlocking) UnlockDialog(model) { unlocking = false }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this phone?") },
            text = { Text("The token is removed from this phone. Pair again with the password to use it. (To stop the old token on the server too, revoke it in the web app.)") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; model.unpair() }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NumberSetting(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    androidx.compose.material3.OutlinedTextField(
        text,
        { v ->
            text = v.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in range }?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
