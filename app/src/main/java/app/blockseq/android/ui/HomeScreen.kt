package app.blockseq.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.ui.Alignment
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

/** Starred pages: bright yellow, in light and dark. */
private val STAR_YELLOW = androidx.compose.ui.graphics.Color(0xFFFFD600)

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Journals("Journals", Icons.Filled.CalendarToday),
    Pages("Pages", Icons.AutoMirrored.Filled.List),
    Search("Search", Icons.Filled.Search),
    Tasks("Tasks", Icons.Filled.CheckBox),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: AppModel, onOpen: (String) -> Unit, onSettings: () -> Unit, onAssets: () -> Unit = {}, onQuickNote: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Journals) }
    // Bumped by live updates from other devices, so lists reload.
    var changes by remember { mutableIntStateOf(0) }
    val api = model.api ?: return
    // Live updates; reconnects after a dropped connection. The left pane shows the state.
    LaunchedEffect(api) {
        while (true) {
            api.events().catch { model.connected = false }.collect { ev ->
                val type = ev["type"]?.toString()?.trim('"')
                if (type == "_open") { model.connected = true; model.markSynced(); return@collect }
                model.markSynced()
                if (ev["client"]?.toString()?.trim('"') != api.clientId) changes++
            }
            model.connected = false
            delay(5000)
        }
    }
    var unlocking by remember { mutableStateOf(false) }
    if (unlocking) UnlockDialog(model) { unlocking = false }
    var creating by remember { mutableStateOf(false) }
    var pagesMenu by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { pagesMenu = false }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var tasking by remember { mutableStateOf(false) }
    if (tasking) NewTaskSheet(model) { added -> tasking = false; if (added) changes++ }
    if (noting) QuickNoteSheet(model, onOpenDay = { noting = false; onQuickNote(journalName(LocalDate.now())) }, onDone = { added -> noting = false; if (added) changes++ })
    if (creating) NewPageDialog(model, onDone = { name -> creating = false; name?.let(onOpen) })
    // Swipe sideways between the tabs; the tab bar and the swipe stay in step.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab.ordinal) { Tab.entries.size }
    LaunchedEffect(tab) { if (pager.currentPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal) }
    LaunchedEffect(pager.settledPage) { tab = Tab.entries[pager.settledPage] }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            LeftPane(model, tab, onTab = { tab = it; scope.launch { drawer.close() } }, onOpen = { scope.launch { drawer.close() }; onOpen(it) }, onSettings = { scope.launch { drawer.close() }; onSettings() }, onAssets = { scope.launch { drawer.close() }; onAssets() }, open = drawer.isOpen)
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (model.store.workbook == "main") "Blockseq" else model.store.workbook) },
                navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.testTag("menu")) { Icon(Icons.Filled.Menu, "Menu") } },
                actions = {
                    if (model.setup != null) LockButton(model.key != null, onLock = model::lock, onUnlock = { unlocking = true })
                },
            )
        },
        floatingActionButton = {
            when (tab) {
                // Pages: a new page or tag. Journals: a quick note on today's journal.
                Tab.Pages -> if (!filtering) ActionsFab(
                    pagesMenu, Icons.Filled.MoreVert, "Page actions", "fab-pages", { pagesMenu = !pagesMenu },
                    listOf(
                        FabItem("Filter", Icons.Filled.FilterList, "fab-filter") { pagesMenu = false; filtering = true },
                        FabItem("New page or tag", Icons.Filled.Add, "fab-new") { pagesMenu = false; creating = true },
                    ),
                )
                Tab.Tasks -> FloatingActionButton(onClick = { tasking = true }, modifier = Modifier.testTag("fab-task")) { Icon(Icons.Filled.Add, "New task") }
                // Journals: the pencil opens the Quick note card.
                Tab.Journals -> FloatingActionButton(onClick = { noting = true }, modifier = Modifier.testTag("fab-note")) { Icon(Icons.Filled.Edit, "Quick note") }
                else -> {}
            }
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
        // On the first tab, a swipe right (past the pager's edge) opens the left pane.
        // Watched without consuming, so the pager and lists work as before.
        val openAt = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
        val swipeToPane = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pager.currentPage != 0) return@awaitEachGesture
                while (true) {
                    val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val d = ch.position - down.position
                    if (kotlin.math.abs(d.y) > kotlin.math.abs(d.x) && kotlin.math.abs(d.y) > viewConfiguration.touchSlop) break
                    if (d.x > openAt && d.x > 2 * kotlin.math.abs(d.y)) { scope.launch { drawer.open() }; break }
                }
            }
        }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().then(swipeToPane), key = { Tab.entries[it].name }) { page ->
          Column(Modifier.fillMaxSize()) {
            when (Tab.entries[page]) {
                Tab.Journals -> JournalsTab(model, key, onOpen)
                Tab.Pages -> PagesTab(model, key, onOpen, filtering) { filtering = false }
                Tab.Search -> SearchTab(model, onOpen)
                Tab.Tasks -> TasksTab(model, key, onOpen)
            }
          }
        }
    }
    }
}

/** The left pane: sections, starred pages, workbooks, settings. */
@Composable
private fun LeftPane(model: AppModel, current: Tab, onTab: (Tab) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit, onAssets: () -> Unit, open: Boolean) {
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var workbooks by remember { mutableStateOf<List<app.blockseq.android.data.Workbook>>(emptyList()) }
    var days by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(open, model.generation) {
        if (!open) return@LaunchedEffect
        val api = model.api ?: return@LaunchedEffect
        runCatching { api.pages() }.onSuccess { list -> favorites = list.filter { it.favorite }.map { it.name } }.onFailure(model::handle)
        runCatching { api.workbooks() }.onSuccess { workbooks = it }
        runCatching { api.sidebar() }.onSuccess { days = it.journalDays.toSet() }
    }
    ModalDrawerSheet(Modifier.testTag("left-pane")) {
        Text("Blockseq", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
        ConnectionStatus(model)
        // The sections, favorites and workbooks share the height above Settings: each row
        // grows to fill it. With too many to fit at a comfortable size, the list scrolls.
        val rows = Tab.entries.size + 1 + maxOf(favorites.size, 1) + (if (workbooks.size > 1) workbooks.size else 0)
        val headings = if (workbooks.size > 1) 2 else 1
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fits = maxHeight >= 48.dp * rows + 40.dp * headings
            Column(if (fits) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                @Composable
                fun item(content: @Composable (Modifier) -> Unit) {
                    content(if (fits) Modifier.weight(1f).fillMaxHeight() else Modifier.height(48.dp))
                }
                Tab.entries.forEach { t ->
                    item { m ->
                        NavigationDrawerItem(
                            label = { Text(t.label) }, icon = { Icon(t.icon, null) }, selected = t == current, onClick = { onTab(t) },
                            modifier = m.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().testTag("pane-${t.name.lowercase()}"),
                        )
                    }
                }
                // The workbook's uploaded files (the web app's Assets page).
                item { m ->
                    NavigationDrawerItem(
                        label = { Text("Assets") }, icon = { Icon(Icons.Filled.Folder, null) }, selected = false, onClick = onAssets,
                        modifier = m.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().testTag("pane-assets"),
                    )
                }
                PaneHeading("Favorites")
                if (favorites.isEmpty()) item { m ->
                    Text("Star a page in the web app to see it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = m.padding(horizontal = 28.dp, vertical = 4.dp))
                }
                favorites.forEach { name ->
                    item { m ->
                        NavigationDrawerItem(label = { Text(name) }, icon = { Icon(Icons.Filled.Star, null, tint = STAR_YELLOW) }, selected = false, onClick = { onOpen(name) }, modifier = m.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth())
                    }
                }
                if (workbooks.size > 1) {
                    PaneHeading("Workbooks")
                    workbooks.forEach { w ->
                        item { m ->
                            NavigationDrawerItem(
                                label = { Text(w.name) }, icon = { Icon(Icons.Filled.Book, null) },
                                selected = model.store.workbook == w.id, onClick = { model.switchWorkbook(w.id) },
                                modifier = m.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        // Settings sits just above the calendar, at the bottom of the pane.
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        NavigationDrawerItem(label = { Text("Settings") }, icon = { Icon(Icons.Filled.Settings, null) }, selected = false, onClick = onSettings, modifier = Modifier.padding(horizontal = 12.dp).height(44.dp).testTag("pane-settings"))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        MonthCalendar(days, java.time.DayOfWeek.of(model.store.weekStart)) { onOpen(journalName(it)) }
    }
}

@Composable
private fun PaneHeading(text: String) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, bottom = 4.dp))
}

@Composable
private fun linkHandler(model: AppModel, onOpen: (String) -> Unit): (Target) -> Unit {
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    return { t ->
        when (t) {
            is Target.Page -> onOpen(t.name)
            is Target.Url -> runCatching { uri.openUri(t.url) }
            is Target.BlockRef -> scope.launch { runCatching { model.api?.blockPage(t.id) }.getOrNull()?.let(onOpen) }
            is Target.Copy -> copyText(context, t.text)
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
        runCatching { load() }.onSuccess { data = it; error = null; model.markSynced() }.onFailure { error = it.message ?: "Could not load"; model.handle(it) }
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

/** The band for striped journal rows, from the theme so it suits dark and light; null: off. */
@Composable
internal fun stripeColor(style: String): androidx.compose.ui.graphics.Color? = when (style) {
    "subtle" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    "stronger" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    "accent" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else -> null
}

@Composable
private fun JournalCard(name: String, blocks: List<app.blockseq.android.outline.Block>, model: AppModel, onOpen: (String) -> Unit, onTap: (Target) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // The calendar and the date open the day (as on linked references); links in the text open other pages.
        Row(Modifier.fillMaxWidth().clickable { onOpen(name) }.testTag("journal-open"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, "Open $name", tint = app.blockseq.android.render.inlineColors().link, modifier = Modifier.padding(end = 10.dp).size(26.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.padding(top = 6.dp))
        if (blocks.isEmpty()) {
            Text("Tap the date to write", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onOpen(name) })
        } else {
            // Top two levels as a preview; open the journal for everything. Properties
            // (row colour, heading, alignment) come along, and encrypted children
            // show once unlocked.
            val root = buildTree(blocks)
            val key = model.key
            val preview = mutableListOf<Triple<Int, app.blockseq.android.outline.Node, Boolean>>()
            fun openKids(c: app.blockseq.android.outline.Node): List<app.blockseq.android.outline.Node> {
                val blob = c.encryptedBlob ?: return c.children
                if (key == null) return emptyList()
                return runCatching { app.blockseq.android.outline.parseOutline(app.blockseq.android.crypto.BlockCrypto.decrypt(key, blob)) }.getOrNull().orEmpty()
            }
            root.children.forEach { c ->
                preview += Triple(0, c, c.encryptedBlob != null && key == null)
                if (!c.collapsed) openKids(c).forEach { preview += Triple(1, it, false) }
            }
            // Every other row gets a soft band (Settings → Display → Journal rows).
            val band = stripeColor(model.store.journalStripes)
            preview.take(12).forEachIndexed { i, (depth, node, locked) ->
                Row(
                    Modifier.padding(top = 2.dp).fillMaxWidth()
                        .then(if (band != null && i % 2 == 1) Modifier.background(band, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) else Modifier)
                        .padding(start = (depth * 18 + 6).dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        BlockContent(node.content, model.statuses, model.api, onTap, null, props = node.props, depth = depth)
                        if (locked) Text("🔒 Encrypted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (preview.size > 12) Text("… more", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onOpen(name) })
        }
    }
    HorizontalDivider()
}

class FabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tag: String, val onClick: () -> Unit)

/** A floating button that opens a short column of labelled actions above it (top item first). */
@Composable
private fun ActionsFab(open: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onToggle: () -> Unit, items: List<FabItem>) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { FabAction(it.label, it.icon, it.tag, it.onClick) }
            }
        }
        FloatingActionButton(onClick = onToggle, modifier = Modifier.testTag(tag)) {
            Icon(if (open) Icons.Filled.Close else icon, if (open) "Close" else label)
        }
    }
}

@Composable
private fun FabAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.small, tonalElevation = 3.dp, shadowElevation = 2.dp) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        androidx.compose.material3.SmallFloatingActionButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Icon(icon, label) }
    }
}

private enum class PageKind(val label: String) { Pages("Pages"), Journals("Journals"), Starred("Starred"), All("All") }

@Composable
private fun PagesTab(model: AppModel, key: String, onOpen: (String) -> Unit, filtering: Boolean, onCloseFilter: () -> Unit) {
    val api = model.api ?: return
    var filter by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PageKind.Pages) }
    // Closing the filter shows every page again, so the list is never filtered out of sight.
    LaunchedEffect(filtering) { if (!filtering) { filter = ""; kind = PageKind.Pages } }
    // Settings → Search and filter fields: the card sits at the top or bottom, like the Search field.
    val atBottom = model.store.searchAtBottom
    val card = @Composable {
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        androidx.compose.material3.ElevatedCard(Modifier.fillMaxWidth().padding(10.dp).testTag("filter-card")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                OutlinedTextField(filter, { filter = it }, placeholder = { Text("Filter") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.FilterList, null) }, modifier = Modifier.weight(1f).focusRequester(focus).testTag("filter"))
                IconButton(onClick = onCloseFilter, modifier = Modifier.testTag("filter-close")) { Icon(Icons.Filled.Close, "Close filter") }
            }
            Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PageKind.entries.forEach { k ->
                    androidx.compose.material3.FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }, modifier = Modifier.testTag("kind-${k.name.lowercase()}"))
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().then(if (atBottom && filtering) Modifier.imePadding() else Modifier)) {
        if (!atBottom && filtering) card()
        Box(Modifier.weight(1f)) {
        Loader(model, key, { api.pages() }) { pages: List<PageSummary> ->
            val shown = pages
                .filter {
                    when (kind) {
                        PageKind.Pages -> it.journalDay == null
                        PageKind.Journals -> it.journalDay != null
                        PageKind.Starred -> it.favorite
                        PageKind.All -> true
                    }
                }
                .filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<PageSummary> { it.favorite }.thenByDescending { it.journalDay ?: 0L }.thenByDescending { it.updatedAt })
            LazyColumn(Modifier.fillMaxSize().testTag("pages")) {
                if (shown.isEmpty()) item { Text("No pages match.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                items(shown, key = { it.name }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(p.name) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (p.favorite) Icon(Icons.Filled.Star, "Starred", tint = STAR_YELLOW, modifier = Modifier.padding(end = 8.dp))
                        else if (p.journalDay != null) Icon(Icons.Filled.CalendarMonth, "Journal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                        else Icon(Icons.AutoMirrored.Filled.Article, "Page", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
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
        if (atBottom && filtering) card()
    }
}

@Composable
private fun SearchTab(model: AppModel, onOpen: (String) -> Unit) {
    val api = model.api ?: return
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    // Tag names (lower case), so tag pages in the results get a tag symbol.
    var tagNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(api) { runCatching { api.pages() }.onSuccess { ps -> tagNames = ps.flatMap { it.tags }.map { it.lowercase() }.toSet() } }
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
                Row(Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageIcon(name, Modifier.padding(end = 10.dp).size(22.dp), tag = name.lowercase() in tagNames)
                    Text(name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(results.blocks, key = { "b:" + it.id }) { b: BlockHit ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(b.pageName) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PageIcon(b.pageName, Modifier.padding(end = 6.dp).size(18.dp))
                        Text(b.pageName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                        Row(Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PageIcon(page, Modifier.padding(end = 8.dp).size(20.dp))
                            Text(page, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
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

/** The Pages tab's + button: a new page, or a tag (a tag is a page others link to with #name). */
@Composable
private fun NewPageDialog(model: AppModel, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun create(asTag: Boolean) {
        val n = name.trim().removePrefix("#").removePrefix("[[").removeSuffix("]]").trim()
        if (n.isEmpty()) return
        scope.launch {
            runCatching { model.api?.ensurePage(n) }
                .onSuccess { onDone(n) }
                .onFailure { error = it.message; model.handle(it) }
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { TitleWithClose("New page or tag", { onDone(null) }, "new-page-close") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("new-name"))
                Text(
                    "A tag is a page too: #name in any block links to it. An empty page goes away again if nothing is written on it or links to it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = { create(false) }) { Text("Create page") } },
        dismissButton = { androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = { create(true) }) { Text("Create tag") } },
    )
}

/** Connected (live updates open) or not, and when the app last synced with the server. */
@Composable
private fun ConnectionStatus(model: AppModel) {
    // Redraw "x min ago" every half minute.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    tick
    val color = if (model.connected) androidx.compose.ui.graphics.Color(0xFF43A047) else androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    Row(Modifier.padding(start = 24.dp, top = 6.dp, bottom = 12.dp).testTag("connection"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(8.dp))
        val synced = model.lastSync?.let { "synced ${ago(it)}" } ?: "not synced yet"
        Text((if (model.connected) "Connected" else "Offline") + " · " + synced, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> formatTime(ms)
    }
}

/**
 * The journal's pencil: a card that slides up with a text box. What is typed
 * goes on the last row of today's journal (a new row unless the last is
 * empty), saved with the same version check as any edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteSheet(model: AppModel, onOpenDay: () -> Unit, onDone: (added: Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val today = journalName(LocalDate.now())
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { if (!busy) onDone(false) }, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick note · $today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!busy) onDone(false) }, modifier = Modifier.testTag("quick-note-close")) { Icon(Icons.Filled.Close, "Close") }
            }
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("Write a note…") }, minLines = 3, maxLines = 8,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).focusRequester(focus).testTag("quick-note"),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.material3.TextButton(onClick = onOpenDay) { Text("Open the day") }
                androidx.compose.material3.Button(
                    enabled = text.isNotBlank() && !busy,
                    modifier = Modifier.testTag("quick-note-add"),
                    onClick = {
                        val api = model.api ?: return@Button
                        busy = true
                        scope.launch {
                            val editor = app.blockseq.android.editor.PageEditor(api, today, model.statuses, model.key, scope, model::handle, model::markSynced)
                            editor.load()
                            val row = editor.noteRow()
                            editor.setText(row, text.trim())
                            editor.save()
                            busy = false
                            if (editor.conflict == null && editor.error == null && !editor.dirty) onDone(true)
                            else error = editor.error ?: "Could not add it (the day changed elsewhere); try again."
                        }
                    },
                ) { Text(if (busy) "Adding…" else "Add") }
            }
        }
    }
}

/** A calendar for a journal day, a page symbol for any other page (as in linked references). */
@Composable
fun PageIcon(name: String, modifier: Modifier = Modifier, tag: Boolean = false) {
    Icon(
        when { isJournalName(name) -> Icons.Filled.CalendarMonth; tag -> Icons.Filled.Tag; else -> Icons.AutoMirrored.Filled.Article },
        when { isJournalName(name) -> "Journal"; tag -> "Tag"; else -> "Page" },
        tint = app.blockseq.android.render.inlineColors().link, modifier = modifier,
    )
}
