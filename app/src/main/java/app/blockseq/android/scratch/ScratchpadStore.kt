package app.blockseq.android.scratch

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** The page the widget shows and adds to, always in the Main workbook. */
const val SCRATCHPAD = "Scratchpad"

/** One row of the Scratchpad as last synced. */
@Serializable
data class ScratchRow(val id: String, val content: String, val depth: Int = 0, val parent: String? = null)

/** A note typed on this phone that the server does not have yet. */
@Serializable
data class PendingNote(
    val id: String,
    val content: String,
    val createdAt: Long,
    /** The day's date row (`[[Nov 13th, 2026]]`) the note goes under; null for a top-level row. */
    val day: String? = null,
)

@Serializable
data class ScratchState(
    val rows: List<ScratchRow> = emptyList(),
    val version: Long = 0,
    val lastSync: Long? = null,
    val pending: List<PendingNote> = emptyList(),
    /** Why the last sync failed (shown on the widget), or null. */
    val error: String? = null,
)

/**
 * The Scratchpad kept on the phone, so the widget works offline: the rows as
 * last synced, and notes waiting to be sent. A small JSON file in the app's
 * private storage (not backed up, like the rest of the app's data).
 */
object ScratchpadStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, "scratchpad.json")

    fun load(context: Context): ScratchState = synchronized(lock) {
        runCatching { json.decodeFromString<ScratchState>(file(context).readText()) }.getOrDefault(ScratchState())
    }

    fun update(context: Context, change: (ScratchState) -> ScratchState): ScratchState = synchronized(lock) {
        val next = change(load(context))
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(next))
        tmp.renameTo(f)
        next
    }

    /**
     * Queues a note to go under today's date row (`[[Nov 13th, 2026]]`, a link
     * to the day's journal), which is made when the day's first note is sent.
     */
    fun add(context: Context, text: String): PendingNote {
        val note = PendingNote(java.util.UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis(), day = "[[${journalName(LocalDate.now())}]]")
        update(context) { s -> s.copy(pending = s.pending + note) }
        return note
    }

    /** Where picture thumbnails for the widget are kept. */
    fun imageDir(context: Context) = File(context.filesDir, "scratch-img").apply { mkdirs() }

    private fun journalName(date: LocalDate): String = app.blockseq.android.ui.journalName(date)
}

/**
 * The page with the queued notes put in, in outline order: each note becomes
 * the last child of its day's top-level date row, which is added at the end
 * when the page does not have it yet. Used to send the notes and to draw them
 * on the widget before they are sent.
 */
fun withPending(blocks: List<app.blockseq.android.outline.Block>, pending: List<PendingNote>): List<app.blockseq.android.outline.Block> {
    val out = blocks.toMutableList()
    for (p in pending) {
        if (out.any { it.id == p.id }) continue
        val day = p.day
        if (day == null) { out += app.blockseq.android.outline.Block(p.id, null, p.content); continue }
        val head = out.firstOrNull { it.parentId == null && it.content.trim() == day }
            // Same id on every try for the same note, so a retry does not add a second date row.
            ?: app.blockseq.android.outline.Block(java.util.UUID.nameUUIDFromBytes("day:${p.id}".toByteArray()).toString(), null, day).also { out += it }
        val tree = hashSetOf(head.id)
        var at = out.indexOf(head)
        for (i in at + 1 until out.size) {
            if (out[i].parentId in tree) { tree += out[i].id; at = i } else break
        }
        out.add(at + 1, app.blockseq.android.outline.Block(p.id, head.id, p.content))
    }
    return out
}
