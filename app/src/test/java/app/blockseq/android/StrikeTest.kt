package app.blockseq.android

import app.blockseq.android.editor.toggleLine
import app.blockseq.android.editor.toggleSelection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same results as the web editor's toggleLine (tests/e2e/strike.ts in the server repo). */
class StrikeTest {
    @Test fun keepsStatusAndTagsOutside() {
        assertEquals("TODO ~~Order seeds~~ #garden #task", toggleLine("TODO Order seeds #garden #task", "~~", "TODO "))
        assertEquals("TODO Order seeds #garden #task", toggleLine("TODO ~~Order seeds~~ #garden #task", "~~", "TODO "))
    }
    @Test fun headingsAndOtherMarks() {
        assertEquals("## ~~Title~~", toggleLine("## Title", "~~"))
        assertEquals("**~~bold~~**", toggleLine("**bold**", "~~"))
        assertEquals("```kotlin", toggleLine("```kotlin", "~~"))
    }
    @Test fun selection() {
        assertEquals(Triple("a ~~bc~~ d", 4, 6), toggleSelection("a bc d", 2, 4, "~~"))
        assertEquals(Triple("a bc d", 2, 4), toggleSelection("a ~~bc~~ d", 4, 6, "~~"))
        assertEquals(Triple("a bc d", 2, 4), toggleSelection("a ~~bc~~ d", 2, 8, "~~"))
    }
}
