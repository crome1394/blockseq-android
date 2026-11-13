package app.blockseq.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.blockseq.android.AppModel
import app.blockseq.android.data.BlockHit
import app.blockseq.android.data.JournalPage
import app.blockseq.android.data.PageSummary
import app.blockseq.android.data.SearchResults
import app.blockseq.android.data.TaskHit
import app.blockseq.android.outline.buildTree
import app.blockseq.android.outline.flatten
import app.blockseq.android.render.BlockContent
import app.blockseq.android.render.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Tab(val label: String) { Journals("Journals"), Pages("Pages"), Search("Search"), Tasks("Tasks") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: AppModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Journals) }
    // Bumped by live updates from other devices, so lists reload.
    var changes by remember { mutableIntStateOf(0) }
    val api = model.api ?: return
    LaunchedEffect(api) {
        api.events().catch { }.collect { ev ->
            if (ev["client"]?.toString()?.trim('"') != api.clientId) changes++
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (model.store.workbook == "main") "Blockseq" else model.store.workbook) },
                actions = { IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                        modifier = Modifier.testTag("tab-${t.name.lowercase()}"),
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Journals -> Icons.Filled.CalendarToday
                                    Tab.Pages -> Icons.AutoMirrored.Filled.List
                                    Tab.Search -> Icons.Filled.Search
                                    Tab.Tasks -> Icons.Filled.CheckBox
                                },
                                t.label,
                            )
                        },
                    )
                }
            }
        },
    ) { pad ->
        val key = "${model.generation}:$changes"
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Journals -> JournalsTab(model, key, onOpen)
                Tab.Pages -> PagesTab(model, key, onOpen)
                Tab.Search -> SearchTab(model, onOpen)
                Tab.Tasks -> TasksTab(model, key, onOpen)
            }
        }
    }
}

@Composable
private fun linkHandler(model: AppModel, onOpen: (String) -> Unit): (Target) -> Unit {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    return { t ->
        when (t) {
            is Target.Page -> onOpen(t.name)
            is Target.Url -> runCatching { uri.openUri(t.url) }
            is Target.BlockRef -> scope.launch { runCatching { model.api?.blockPage(t.id) }.getOrNull()?.let(onOpen) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Loader(model: AppModel, key: String, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var data by remember { mutableStateOf<T?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun fetch() {
        runCatching { load() }.onSuccess { data = it; error = null }.onFailure { error = it.message ?: "Could not load"; model.handle(it) }
    }
    LaunchedEffect(key) { fetch() }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; fetch(); refreshing = false } }) {
        val d = data
        when {
            d != null -> content(d)
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            else -> Text("Loading…", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun JournalsTab(model: AppModel, key: String, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    val onTap = linkHandler(model, onOpen)
    Loader(model, key, { api.journals(limit = 14) }) { list: List<JournalPage> ->
        val today = LocalDate.now()
        val todayName = journalName(today)
        val hasToday = list.any { it.page.journalDay == journalDay(today) }
        LazyColumn(Modifier.fillMaxSize().testTag("journals"), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (!hasToday) item { JournalCard(todayName, emptyList(), model, onOpen, onTap) }
            items(list, key = { it.page.id }) { j -> JournalCard(j.page.name, j.blocks, model, onOpen, onTap) }
        }
    }
}

@Composable
private fun JournalCard(name: String, blocks: List<app.blockseq.android.outline.Block>, model: AppModel, onOpen: (String) -> Unit, onTap: (Target) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(top = 6.dp))
        if (blocks.isEmpty()) {
            Text("Tap to write", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Top two levels as a preview; open the journal for everything.
            val root = buildTree(blocks)
            val preview = mutableListOf<Pair<Int, String>>()
            root.children.forEach { c ->
                preview += 0 to c.content
                if (!c.collapsed && c.encryptedBlob == null) c.children.forEach { preview += 1 to it.content }
            }
            preview.take(12).forEach { (depth, text) ->
                Row(Modifier.padding(start = (depth * 18).dp, top = 3.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    BlockContent(text, model.statuses, model.api, onTap, null)
                }
            }
            if (preview.size > 12) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun PagesTab(model: AppModel, key: String, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    var filter by rememberSaveable { mutableStateOf("") }
    Column {
        OutlinedTextField(filter, { filter = it }, label = { Text("Filter pages") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
        Loader(model, key, { api.pages().filter { it.journalDay == null } }) { pages: List<PageSummary> ->
            val shown = pages.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<PageSummary> { it.favorite }.thenByDescending { it.updatedAt })
            LazyColumn(Modifier.fillMaxSize().testTag("pages")) {
                items(shown, key = { it.name }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(p.name) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (p.favorite) Icon(Icons.Filled.Star, "Starred", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            val tags = p.tags.joinToString(" ") { "#$it" }
                            Text("${p.words} words${if (tags.isNotEmpty()) " · $tags" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(model: AppModel, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val onTap = linkHandler(model, onOpen)
    LaunchedEffect(q) {
        if (q.isBlank()) { results = SearchResults(); return@LaunchedEffect }
        delay(250)
        runCatching { api.search(q.trim()) }.onSuccess { results = it }
    }
    Column {
        OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("search"))
        LazyColumn(Modifier.fillMaxSize()) {
            items(results.pages, key = { "p:$it" }) { name ->
                Text(name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            items(results.blocks, key = { "b:" + it.id }) { b: BlockHit ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(b.pageName) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(b.pageName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BlockContent(b.content, model.statuses, api, onTap, null)
                }
            }
        }
    }
}

@Composable
private fun TasksTab(model: AppModel, key: String, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    var showDone by rememberSaveable { mutableStateOf(false) }
    var bump by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val onTap = linkHandler(model, onOpen)
    Column {
        Row(Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show completed", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Switch(checked = showDone, onCheckedChange = { showDone = it })
        }
        Loader(model, "$key:$bump", { api.tasks() }) { tasks: List<TaskHit> ->
            val shown = tasks.filter { showDone || model.statuses.of(it.content)?.done != true }
            val groups = shown.groupBy { it.pageName }
            LazyColumn(Modifier.fillMaxSize().testTag("tasks")) {
                groups.forEach { (page, list) ->
                    item(key = "g:$page") {
                        Text(page, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(list, key = { "t:" + it.id }) { t ->
                        BlockContent(
                            t.content, model.statuses, api, onTap,
                            onStatusTap = {
                                // Only the status changes; refused if the task changed meanwhile.
                                val next = model.statuses.with(t.content, model.statuses.next(model.statuses.of(t.content)?.word))
                                scope.launch { runCatching { api.putBlock(t.id, next, t.content) }; bump++ }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                if (shown.isEmpty()) item { Text("No open tasks.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
