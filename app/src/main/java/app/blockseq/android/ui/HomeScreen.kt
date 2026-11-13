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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Book
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
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Journals("Journals", Icons.Filled.CalendarToday),
    Pages("Pages", Icons.AutoMirrored.Filled.List),
    Search("Search", Icons.Filled.Search),
    Tasks("Tasks", Icons.Filled.CheckBox),
}

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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            LeftPane(model, tab, onTab = { tab = it; scope.launch { drawer.close() } }, onOpen = { scope.launch { drawer.close() }; onOpen(it) }, onSettings = { scope.launch { drawer.close() }; onSettings() }, open = drawer.isOpen)
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (model.store.workbook == "main") "Blockseq" else model.store.workbook) },
                navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.testTag("menu")) { Icon(Icons.Filled.Menu, "Menu") } },
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
                        icon = { Icon(t.icon, t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val key = "${model.generation}:$changes"
        // The tab bar's space is already taken off: the keyboard padding below must not count it again.
        Column(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            when (tab) {
                Tab.Journals -> JournalsTab(model, key, onOpen)
                Tab.Pages -> PagesTab(model, key, onOpen)
                Tab.Search -> SearchTab(model, onOpen)
                Tab.Tasks -> TasksTab(model, key, onOpen)
            }
        }
    }
    }
}

/** The left pane: sections, starred pages, workbooks, settings. */
@Composable
private fun LeftPane(model: AppModel, current: Tab, onTab: (Tab) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit, open: Boolean) {
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var workbooks by remember { mutableStateOf<List<app.blockseq.android.data.Workbook>>(emptyList()) }
    LaunchedEffect(open, model.generation) {
        if (!open) return@LaunchedEffect
        val api = model.api ?: return@LaunchedEffect
        runCatching { api.pages() }.onSuccess { list -> favorites = list.filter { it.favorite }.map { it.name } }.onFailure(model::handle)
        runCatching { api.workbooks() }.onSuccess { workbooks = it }
    }
    ModalDrawerSheet(Modifier.testTag("left-pane")) {
        LazyColumn {
            item {
                Text("Blockseq", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp))
            }
            items(Tab.entries.toList(), key = { "tab:" + it.name }) { t ->
                NavigationDrawerItem(
                    label = { Text(t.label) }, icon = { Icon(t.icon, null) }, selected = t == current, onClick = { onTab(t) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("pane-${t.name.lowercase()}"),
                )
            }
            item { PaneHeading("Favorites") }
            if (favorites.isEmpty()) item {
                Text("Star a page in the web app to see it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
            }
            items(favorites, key = { "fav:$it" }) { name ->
                NavigationDrawerItem(label = { Text(name) }, icon = { Icon(Icons.Filled.Star, null) }, selected = false, onClick = { onOpen(name) }, modifier = Modifier.padding(horizontal = 12.dp))
            }
            if (workbooks.size > 1) {
                item { PaneHeading("Workbooks") }
                items(workbooks, key = { "wb:" + it.id }) { w ->
                    NavigationDrawerItem(
                        label = { Text(w.name) }, icon = { Icon(Icons.Filled.Book, null) },
                        selected = model.store.workbook == w.id, onClick = { model.switchWorkbook(w.id) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(label = { Text("Settings") }, icon = { Icon(Icons.Filled.Settings, null) }, selected = false, onClick = onSettings, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun PaneHeading(text: String) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, bottom = 4.dp))
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
        runCatching { api.search(q.trim()) }.onSuccess { results = it }.onFailure(model::handle)
    }
    // Settings → Search field chooses top or bottom (bottom is easier to reach one-handed).
    val atBottom = model.store.searchAtBottom
    val field = @Composable {
        OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("search"))
    }
    Column(Modifier.fillMaxSize().then(if (atBottom) Modifier.imePadding() else Modifier)) {
        if (!atBottom) field()
        LazyColumn(Modifier.weight(1f), reverseLayout = false) {
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
        if (atBottom) field()
    }
}

/** Status changes go to the server one after another (see step). */
private val statusLock = kotlinx.coroutines.sync.Mutex()

/** How long a task whose status was just changed stays in the list (as in the web app). */
private const val LINGER_MS = 6000L

@Composable
private fun TasksTab(model: AppModel, key: String, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    var showDone by rememberSaveable { mutableStateOf(false) }
    var bump by remember { mutableIntStateOf(0) }
    // Tasks changed here a moment ago: shown with their new text until the
    // time is up, even when they are now done or have no status, so a few
    // taps can step through the statuses without the row vanishing.
    val lingering = remember { mutableStateMapOf<String, Pair<TaskHit, Long>>() }
    var lastOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lingering.size) {
        while (lingering.isNotEmpty()) {
            delay(500)
            now = System.currentTimeMillis()
            lingering.entries.removeAll { it.value.second <= now }
        }
    }
    val scope = rememberCoroutineScope()
    val onTap = linkHandler(model, onOpen)
    Column {
        Row(Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show completed", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Switch(checked = showDone, onCheckedChange = { showDone = it })
        }
        Loader(model, "$key:$bump", { api.tasks() }) { tasks: List<TaskHit> ->
            val active = lingering.filterValues { it.second > now }
            // Server order, with lingering tasks the server no longer lists kept where they were.
            val merged = tasks.map { t -> active[t.id]?.first ?: t }.toMutableList()
            for ((id, entry) in active) {
                if (merged.none { it.id == id }) {
                    val at = lastOrder.indexOf(id).takeIf { it >= 0 }?.coerceAtMost(merged.size) ?: merged.size
                    merged.add(at, entry.first)
                }
            }
            val shown = merged.filter { it.id in active || showDone || model.statuses.of(it.content)?.done != true }
            lastOrder = merged.map { it.id }
            val groups = shown.groupBy { it.pageName }
            LazyColumn(Modifier.fillMaxSize().testTag("tasks")) {
                groups.forEach { (page, list) ->
                    item(key = "g:$page") {
                        Text(page, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(list, key = { "t:" + it.id }) { t ->
                        val st = model.statuses.of(t.content)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            if (st == null) {
                                // No status any more: a placeholder to tap back into the cycle.
                                Text(
                                    "No status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 6.dp, top = 4.dp).clickable { step(model, api, t, lingering, scope) { bump++ } },
                                )
                            }
                            BlockContent(
                                t.content, model.statuses, api, onTap,
                                onStatusTap = { step(model, api, t, lingering, scope) { bump++ } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (shown.isEmpty()) item { Text("No open tasks.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/**
 * Steps a task to the next status (none → first → … → last → none). Only the
 * status changes, and the server refuses it if the task changed meanwhile.
 */
private fun step(
    model: AppModel,
    api: app.blockseq.android.data.Api,
    t: TaskHit,
    lingering: MutableMap<String, Pair<TaskHit, Long>>,
    scope: kotlinx.coroutines.CoroutineScope,
    refresh: () -> Unit,
) {
    val next = model.statuses.with(t.content, model.statuses.next(model.statuses.of(t.content)?.word))
    lingering[t.id] = t.copy(content = next) to System.currentTimeMillis() + LINGER_MS
    scope.launch {
        // One at a time: a quick second tap expects the text the first one wrote.
        runCatching { statusLock.withLock { api.putBlock(t.id, next, t.content) } }.onFailure {
            lingering.remove(t.id)
            model.handle(it)
        }
        refresh()
    }
}
