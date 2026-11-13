package app.blockseq.android.scratch

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.blockseq.android.MainActivity
import app.blockseq.android.R
import app.blockseq.android.render.Part
import app.blockseq.android.render.parts
import app.blockseq.android.render.shownText
import java.io.File

/** Each widget's state: only a revision; the look is shared ([WidgetPrefs]). */
object WidgetLook {
    /** Bumped after every change to the phone's Scratchpad or the look, so the widget reads them again. */
    val REVISION = androidx.datastore.preferences.core.longPreferencesKey("revision")
}

/** One line of the widget: a day heading, or a row. */
private sealed interface Line {
    data class Day(val day: String) : Line
    data class Note(val content: String, val depth: Int, val waiting: Boolean) : Line
}

/**
 * The Scratchpad on the home screen: the page's rows (newest first unless the
 * widget is set otherwise), pictures as thumbnails, notes still waiting to be
 * sent marked ⏳, a sync button and a + to add a note. Read only; tap a row to
 * open the page in the app. Works offline from the phone's copy.
 */
class ScratchpadWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val images = ScratchpadStore.imageDir(context)
        provideContent {
            val prefs = currentState<Preferences>()
            // Read again whenever the revision changes (a sync or a new note), not only at start.
            val rev = prefs[WidgetLook.REVISION]
            val state = androidx.compose.runtime.remember(rev) { ScratchpadStore.load(context) }
            val look = androidx.compose.runtime.remember(rev) { WidgetPrefs.load(context) }
            Content(context, state, images, look)
        }
    }

    @Composable
    private fun Content(context: Context, state: ScratchState, images: File, look: WidgetLookPrefs) {
        val text = Color(look.text)
        val fg = ColorProvider(text)
        val dim = ColorProvider(text.copy(alpha = 0.65f))
        val open = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_PAGE, SCRATCHPAD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        // Rows as synced plus notes not sent yet; each top-level row keeps its children with it.
        val groups = mutableListOf<MutableList<Line.Note>>()
        state.rows.forEach { r -> if (r.depth == 0 || groups.isEmpty()) groups += mutableListOf(Line.Note(r.content, r.depth, false)) else groups.last() += Line.Note(r.content, r.depth, false) }
        state.pending.forEach { groups += mutableListOf(Line.Note(it.content, 0, true)) }
        // Days in page order: a date row alone ([[Nov 13th, 2026]]) starts a day and
        // becomes its heading; older rows carry the date at their start.
        val days = mutableListOf<Pair<String?, MutableList<List<Line.Note>>>>()
        for (g in groups) {
            val s = splitStamp(shownText(g.first().content))
            val dateRow = s.day != null && s.body.isBlank() && g.size == 1
            if (s.day != null && s.day != days.lastOrNull()?.first) days += s.day to mutableListOf()
            if (days.isEmpty()) days += null to mutableListOf()
            if (!dateRow) days.last().second += g
        }
        val ordered = buildList {
            for ((day, gs) in if (look.newestFirst) days.reversed() else days) {
                if (gs.isEmpty()) continue
                if (day != null) add(Line.Day(day))
                (if (look.newestFirst) gs.reversed() else gs).forEach { addAll(it) }
            }
        }
        val hasEncrypted = state.rows.any { it.content.contains("encrypted:: ") }
        @Composable
        fun Buttons() {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(SCRATCHPAD, style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight().clickable(open))
                // Encrypted rows can only be read in the app (the key is never kept on the phone): the padlock opens it there.
                val open_ = WidgetLockState.unlocked
                if (hasEncrypted) Image(
                    ImageProvider(if (open_) R.drawable.ic_widget_lock_open else R.drawable.ic_widget_lock),
                    if (open_) "Unlocked in the app" else "Open in the app to unlock",
                    colorFilter = ColorFilter.tint(ColorProvider(if (open_) Color(0xFF43A047) else Color(0xFFFFA000))),
                    modifier = GlanceModifier.size(28.dp).padding(3.dp).clickable(open))
                Spacer(GlanceModifier.width(10.dp))
                Image(ImageProvider(R.drawable.ic_widget_sync), "Sync", colorFilter = ColorFilter.tint(fg), modifier = GlanceModifier.size(28.dp).padding(2.dp).clickable(actionRunCallback<SyncNow>()))
                Spacer(GlanceModifier.width(10.dp))
                Image(ImageProvider(R.drawable.ic_widget_add), "Add a note", colorFilter = ColorFilter.tint(fg), modifier = GlanceModifier.size(28.dp).padding(2.dp).clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))))
            }
        }
        Column(GlanceModifier.fillMaxSize().background(Color(look.background)).cornerRadius(16.dp).padding(12.dp)) {
            if (!look.buttonsBottom) Buttons()
            val status = buildList {
                state.pending.count { splitStamp(it.content).body.isNotBlank() }.takeIf { it > 0 }?.let { add("⏳ $it waiting") }
                state.error?.let { add(it) } ?: state.lastSync?.let { add("synced ${android.text.format.DateUtils.getRelativeTimeSpanString(it)}") }
            }.joinToString(" · ")
            if (status.isNotEmpty()) Text(status, style = TextStyle(color = dim, fontSize = 11.sp), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            val body = GlanceModifier.fillMaxWidth().defaultWeight()
            when {
                ordered.isEmpty() -> Column(body) { Text("Nothing yet. Tap + to add a note.", style = TextStyle(color = dim, fontSize = 13.sp)) }
                else -> LazyColumn(body) {
                    items(ordered) { line ->
                        when (line) {
                            is Line.Day -> Text(line.day, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp))
                            is Line.Note -> Row(GlanceModifier.fillMaxWidth().padding(start = (line.depth * 14).dp, top = 3.dp, bottom = 3.dp).clickable(open)) {
                                RowContent(line.content, line.waiting, images, fg, dim)
                            }
                        }
                    }
                }
            }
            if (look.buttonsBottom) { Spacer(GlanceModifier.height(6.dp)); Buttons() }
        }
    }

    @Composable
    private fun RowContent(content: String, waiting: Boolean, images: File, fg: ColorProvider, dim: ColorProvider) {
        val (_, time, body) = splitStamp(shownText(content))
        Column(GlanceModifier.fillMaxWidth()) {
            if (time != null || waiting) Text(listOfNotNull(time, if (waiting) "⏳ not sent yet" else null).joinToString(" · "), style = TextStyle(color = dim, fontSize = 11.sp))
            parts(body).forEach { part ->
                when (part) {
                    is Part.Image -> {
                        val f = File(images, ScratchpadSync.thumbName(part.src))
                        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
                        if (bmp != null) Image(ImageProvider(bmp), part.alt, modifier = GlanceModifier.fillMaxWidth().cornerRadius(8.dp).padding(vertical = 2.dp))
                        else Text("🖼 ${part.alt.ifEmpty { "picture" }}", style = TextStyle(color = dim, fontSize = 13.sp))
                    }
                    is Part.Rule -> androidx.glance.layout.Box(GlanceModifier.fillMaxWidth().height(1.dp).background(dim)) {}
                    is Part.Code -> Text(part.code, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 8)
                    is Part.Text -> if (part.text.isNotBlank()) Text(plain(part.text), style = TextStyle(color = fg, fontSize = if (part.heading in 1..3) 16.sp else 14.sp))
                    is Part.Table -> Text(part.rows.joinToString("\n") { it.joinToString("  ·  ") }, style = TextStyle(color = fg, fontSize = 12.sp))
                    is Part.Admonition -> Text(plain(part.text), style = TextStyle(color = fg, fontSize = 13.sp))
                    is Part.Maths -> Text(part.tex, style = TextStyle(color = dim, fontSize = 12.sp))
                }
            }
            if (content.contains("encrypted:: ")) Text(if (WidgetLockState.unlocked) "🔓 Unlocked in the app. Open it to read." else "🔒 Encrypted. Open in the app to unlock.", style = TextStyle(color = dim, fontSize = 12.sp))
        }
    }

    companion object {
        /** Redraws every Scratchpad widget with the phone's current copy. */
        suspend fun refresh(context: Context) {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            for (id in manager.getGlanceIds(ScratchpadWidget::class.java)) {
                androidx.glance.appwidget.state.updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply { this[WidgetLook.REVISION] = System.nanoTime() }
                }
                ScratchpadWidget().update(context, id)
            }
        }

        // A day, then a time if the note has one (older notes); seconds are dropped.
        private val STAMP = Regex("""^\[\[([A-Z][a-z]{2} \d{1,2}(?:st|nd|rd|th), \d{4})\]\]\s*(?:(\d{1,2}:\d{2})(?::\d{2})?\s*)?""")

        data class Stamp(val day: String?, val time: String?, val body: String)

        /** "[[Nov 12th, 2026]] 21:48:05 Buy stakes" → ("Nov 12th, 2026", "21:48", "Buy stakes"); the time is optional. */
        fun splitStamp(text: String): Stamp {
            val m = STAMP.find(text) ?: return Stamp(null, null, text)
            return Stamp(m.groupValues[1], m.groupValues[2].ifEmpty { null }, text.substring(m.range.last + 1))
        }

        /** Markdown made readable as plain widget text. */
        fun plain(text: String): String = text
            .replace(Regex("""\[\[([^\]]+)\]\]"""), "$1")
            .replace(Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)"""), "$1")
            .replace(Regex("""\*\*(.+?)\*\*|__(.+?)__"""), "$1$2")
            .replace(Regex("""``(.+?)``|`([^`]+)`"""), "$1$2")
            .replace(Regex("""~~(.+?)~~|==(.+?)=="""), "$1$2")
    }
}

/** The widget's sync button. */
class SyncNow : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ScratchpadStore.update(context) { it.copy(error = "Syncing…") }
        ScratchpadWidget.refresh(context)
        ScratchpadSync.sync(context)
    }
}

class ScratchpadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScratchpadWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScratchpadSync.schedule(context)
        ScratchpadSync.request(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ScratchpadSync.unschedule(context)
    }
}

