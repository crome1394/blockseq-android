package app.blockseq.android

import app.blockseq.android.outline.Block
import app.blockseq.android.outline.Statuses
import app.blockseq.android.outline.buildTree
import app.blockseq.android.outline.flatten
import app.blockseq.android.outline.joinProps
import app.blockseq.android.outline.parseOutline
import app.blockseq.android.outline.splitProps
import app.blockseq.android.outline.subtreeMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutlineTest {
    @Test
    fun propsSplitAndJoinLikeTheWebApp() {
        val text = "Secrets #Encrypted\nencrypted:: v1:aaa:bbb\nmore text"
        val (content, props) = splitProps(text)
        assertEquals("Secrets #Encrypted\nmore text", content)
        assertEquals("v1:aaa:bbb", props["encrypted"])
        assertEquals(text, joinProps(content, props))
        // The first line is never a property, and code keeps props last.
        assertEquals("heading:: true", splitProps("heading:: true").first)
        assertEquals("```js\nx\n```\nheading:: 2", joinProps("```js\nx\n```", mapOf("heading" to "2")))
    }

    @Test
    fun treeRoundTripsThroughTheApiShape() {
        val blocks = listOf(
            Block("a", null, "parent"),
            Block("b", "a", "child\ncollapsed:: x", collapsed = true),
            Block("c", "b", "grandchild"),
        )
        val root = buildTree(blocks)
        assertEquals(blocks, flatten(root))
        // collapsed is only kept for a block with children.
        assertEquals(false, flatten(buildTree(listOf(Block("z", null, "leaf", collapsed = true)))).first().collapsed)
    }

    @Test
    fun outlineTextRoundTrips() {
        val root = buildTree(listOf(Block("11111111-1111-1111-1111-111111111111", null, "one\nsecond line"), Block("22222222-2222-2222-2222-222222222222", "11111111-1111-1111-1111-111111111111", "two")))
        val md = subtreeMarkdown(root.children[0])
        assertEquals("- one\n  id:: 11111111-1111-1111-1111-111111111111\n  second line\n\t- two\n\t  id:: 22222222-2222-2222-2222-222222222222", md)
        val back = parseOutline(md)!!
        assertEquals("11111111-1111-1111-1111-111111111111", back[0].id)
        assertEquals("one\nsecond line", back[0].content)
        assertEquals("two", back[0].children[0].content)
        assertNull(parseOutline("not an outline"))
    }

    @Test
    fun parsesTheWebAppsEncryptedChildren() {
        val plain = "- Wifi password: hunter2 ✓\n  id:: 6f1c2a9e-3b7d-4c55-9a10-2f4e8d7b1c03\n\t- nested child\n\t  id:: 0a9b8c7d-6e5f-4a3b-8c2d-1e0f9a8b7c6d"
        val kids = parseOutline(plain)!!
        assertEquals("Wifi password: hunter2 ✓", kids[0].content)
        assertEquals("nested child", kids[0].children[0].content)
        assertEquals(plain, subtreeMarkdown(kids[0]))
    }

    @Test
    fun statusesMatchTheWebApp() {
        val s = Statuses.DEFAULT
        assertEquals("TODO", s.of("TODO buy milk #task")?.word)
        assertNull(s.of("TODOS are fun"))
        assertEquals("DONE buy milk #task", s.with("TODO buy milk #task", "DONE"))
        assertEquals("BACKLOG write #task", s.with("write", "BACKLOG"))
        assertEquals("write", s.with("TODO write #task", null))
        assertEquals("BACKLOG", s.next(null))
        assertEquals(null, s.next("CANCELED"))
    }
}

class EnterTest {
    @org.junit.Test
    fun findsTheTypedNewline() {
        assertEquals(5, app.blockseq.android.ui.newlineTyped("hello world", "hello\n world"))
        // Autocorrect committed in the same edit.
        assertEquals(10, app.blockseq.android.ui.newlineTyped("I recieve it", "I receive \nit"))
        assertEquals(null, app.blockseq.android.ui.newlineTyped("abc", "abcd"))
        assertEquals(null, app.blockseq.android.ui.newlineTyped("abcd", "abc"))
    }

    @org.junit.Test
    fun codeFencesKeepTheirNewlines() {
        val t = "Script\n```py\nx = 1\n```"
        assertEquals(true, app.blockseq.android.ui.insideFence(t, t.indexOf("x = 1") + 5))
        assertEquals(false, app.blockseq.android.ui.insideFence(t, 6))
    }
}

class PartsTest {
    @org.junit.Test
    fun picturesAreFoundAloneAndInsideText() {
        val alone = app.blockseq.android.render.parts("![cat](../assets/cat.jpg){:width 600}")
        assertEquals(listOf(app.blockseq.android.render.Part.Image("cat", "../assets/cat.jpg")), alone)
        val mixed = app.blockseq.android.render.parts("Look ![dog](../assets/dog.png) here")
        assertEquals(3, mixed.size)
        assertEquals(app.blockseq.android.render.Part.Image("dog", "../assets/dog.png"), mixed[1])
    }
}

class HighlightTest {
    @org.junit.Test
    fun highlightingKeepsTheTextAndMarksTheParts() {
        val code = "def f(x): # half\n    return \"x\" * 2"
        val c = app.blockseq.android.render.codeColors(true)
        val a = app.blockseq.android.render.highlight(code, "python", c)
        assertEquals(code, a.text)
        val colorAt = { i: Int -> a.spanStyles.firstOrNull { i >= it.start && i < it.end }?.item?.color }
        assertEquals(c.keyword, colorAt(0))
        assertEquals(c.comment, colorAt(code.indexOf("# half")))
        assertEquals(c.string, colorAt(code.indexOf("\"x\"")))
        assertEquals(c.number, colorAt(code.lastIndexOf('2')))
    }
}
