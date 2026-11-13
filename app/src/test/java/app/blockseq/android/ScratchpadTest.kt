package app.blockseq.android

import app.blockseq.android.outline.Block
import app.blockseq.android.scratch.PendingNote
import app.blockseq.android.scratch.withPending
import org.junit.Assert.assertEquals
import org.junit.Test

class ScratchpadTest {
    private val d12 = "[[Nov 12th, 2026]]"
    private val d13 = "[[Nov 13th, 2026]]"
    private fun note(id: String, text: String, day: String) = PendingNote(id, text, 0, day)

    @Test fun notesGoUnderTheirDay() {
        val page = listOf(Block("a", null, d12), Block("b", "a", "Hello2day"), Block("c", "b", "deeper"), Block("x", null, "Loose row"))
        val out = withPending(page, listOf(note("n1", "Late note", d12), note("n2", "Hi There", d13), note("n3", "Final Note", d13)))
        // Nov 12th's note after its last descendant; Nov 13th made once at the end, both notes under it.
        assertEquals(listOf("a", "b", "c", "n1", "x"), out.take(5).map { it.id })
        assertEquals("a", out[3].parentId)
        val head = out[5]
        assertEquals(d13 to null, head.content to head.parentId)
        assertEquals(listOf("n2" to head.id, "n3" to head.id), out.drop(6).map { it.id to it.parentId })
    }

    @Test fun sameDayRowOnRetry() {
        val a = withPending(emptyList(), listOf(note("n1", "x", d13)))
        val b = withPending(emptyList(), listOf(note("n1", "x", d13)))
        assertEquals(a, b)
    }
}
