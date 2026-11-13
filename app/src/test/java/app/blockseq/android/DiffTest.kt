package app.blockseq.android

import app.blockseq.android.editor.DiffLine.Kind.Mine
import app.blockseq.android.editor.DiffLine.Kind.Same
import app.blockseq.android.editor.DiffLine.Kind.Theirs
import app.blockseq.android.editor.diffLines
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffTest {
    @Test fun changedLine() {
        val d = diffLines(listOf("a", "b", "c"), listOf("a", "B", "c", "d"))
        assertEquals(listOf(Same, Theirs, Mine, Same, Mine), d.map { it.kind })
        assertEquals("B", d.first { it.kind == Mine }.text)
    }
    @Test fun same() = assertEquals(listOf(Same, Same), diffLines(listOf("x", "y"), listOf("x", "y")).map { it.kind })
}
