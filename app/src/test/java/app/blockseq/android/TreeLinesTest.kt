package app.blockseq.android

import app.blockseq.android.ui.treeLevels
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeLinesTest {
    @Test fun linesEndAtTheLastChild() {
        // A, B (child), C (grandchild), D (child), E (top level)
        val t = treeLevels(listOf(0, 1, 2, 1, 0)).map { a -> a.take(2).map { if (it) 1 else 0 } }
        assertEquals(listOf(0, 0), t[0])
        assertEquals(listOf(1, 0), t[1]) // A's line runs on past B to D
        assertEquals(listOf(1, 0), t[2]) // and past C; B's line ends at C (last child)
        assertEquals(listOf(0, 0), t[3]) // D is A's last child
        assertEquals(listOf(0, 0), t[4])
    }
}
