package app.blockseq.android

import app.blockseq.android.editor.Row
import app.blockseq.android.editor.depthRange
import app.blockseq.android.editor.placeNode
import app.blockseq.android.editor.subtreeIds
import app.blockseq.android.outline.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReorderTest {
    private fun node(id: String, vararg kids: Node) = Node(id, id).also { n -> kids.forEach { it.parent = n; n.children += it } }
    private fun outline(root: Node): String = buildString {
        fun go(n: Node, d: Int) { n.children.forEach { append("  ".repeat(d) + it.id + "\n"); go(it, d + 1) } }
        go(root, 0)
    }
    private fun rows(root: Node): List<Row> = buildList {
        fun go(n: Node, d: Int) { n.children.forEach { add(Row(it, d)); go(it, d + 1) } }
        go(root, 0)
    }

    @Test fun movesDownWithChildren() {
        val a = node("a", node("a1")); val b = node("b"); val root = node("", a, b)
        placeNode(root, a, b, 0, 0)
        assertEquals("b\na\n  a1\n", outline(root))
    }

    @Test fun becomesChildOfRowAbove() {
        val a = node("a"); val b = node("b", node("b1")); val root = node("", a, b)
        placeNode(root, a, b, 0, 1)
        assertEquals("b\n  a\n  b1\n", outline(root))
    }

    @Test fun outdentsPastAncestors() {
        val c = node("c"); val root = node("", node("a", node("a1", node("a2"))), c)
        val a2 = root.children[0].children[0].children[0]
        placeNode(root, c, a2, 2, 0)
        assertEquals("a\n  a1\n    a2\nc\n", outline(root))
    }

    @Test fun toTheTop() {
        val a = node("a"); val b = node("b"); val root = node("", a, b)
        placeNode(root, b, null, -1, 0)
        assertEquals("b\na\n", outline(root))
    }

    @Test fun notIntoItself() {
        val a = node("a", node("a1")); val root = node("", a)
        assertFalse(placeNode(root, a, a.children[0], 1, 2))
        assertEquals("a\n  a1\n", outline(root))
    }

    @Test fun depthLimits() {
        val root = node("", node("a", node("a1")), node("b"), node("c"))
        val r = rows(root)
        val rest = r.filter { it.node.id !in subtreeIds(r, root.children[1]) } // b dragged: a, a1, c
        assertEquals(0..2, depthRange(rest, 2) { false })      // after a1, before c
        assertEquals(1..1, depthRange(rest, 1) { false })      // after a, before a1
        assertEquals(0..0, depthRange(rest, 0) { false })      // top of the page
        assertEquals(0..1, depthRange(rest, 2) { it.id == "a1" }) // a1 folded
    }
}
