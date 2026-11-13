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

class WordDiffTest {
    @Test fun marksChangedWords() {
        val (a, b) = app.blockseq.android.editor.wordDiff("Pole against the south fence", "Pole against the west fence soon")!!
        assertEquals(listOf("south"), a.map { "Pole against the south fence".substring(it) })
        assertEquals(listOf("west", "soon"), b.map { "Pole against the west fence soon".substring(it) })
    }
    @Test fun joinsNeighbours() {
        val (_, b) = app.blockseq.android.editor.wordDiff("buy milk today", "buy oat milk drink today")!!
        assertEquals(listOf("oat"), b.take(1).map { "buy oat milk drink today".substring(it) })
    }
    @Test fun unlikeLinesUnmarked() = org.junit.Assert.assertNull(app.blockseq.android.editor.wordDiff("alpha beta gamma", "completely different words here"))
}
