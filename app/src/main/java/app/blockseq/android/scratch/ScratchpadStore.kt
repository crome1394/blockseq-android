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
data class ScratchRow(val id: String, val content: String, val depth: Int = 0)

/** A note typed on this phone that the server does not have yet. */
@Serializable
data class PendingNote(val id: String, val content: String, val createdAt: Long)

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
     * Queues a note. The day's first note gets a row of its own above it with
     * the date (`[[Nov 13th, 2026]]`, a link to the day's journal); later notes
     * that day go in plain.
     */
    fun add(context: Context, text: String): PendingNote {
        val stamp = "[[${journalName(LocalDate.now())}]]"
        val note = PendingNote(java.util.UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis())
        update(context) { s ->
            val dated = (s.rows.filter { it.depth == 0 }.map { it.content } + s.pending.map { it.content }).any { it.startsWith(stamp) }
            val head = if (dated) emptyList() else listOf(PendingNote(java.util.UUID.randomUUID().toString(), stamp, note.createdAt))
            s.copy(pending = s.pending + head + note)
        }
        return note
    }

    /** Where picture thumbnails for the widget are kept. */
    fun imageDir(context: Context) = File(context.filesDir, "scratch-img").apply { mkdirs() }

    private fun journalName(date: LocalDate): String = app.blockseq.android.ui.journalName(date)
}
