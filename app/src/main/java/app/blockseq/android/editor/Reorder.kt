package app.blockseq.android.editor

import app.blockseq.android.outline.Node

/**
 * Dragging a block: where it may land. [rest] is the page's visible rows
 * without the dragged block and its children; the block goes before
 * `rest[k]` (or at the end when k == rest.size).
 *
 * The depth it may take runs from the next row's depth (so that row is not
 * pulled under it) to one more than the row above (a child of it), or the
 * same as the row above when that row's children are hidden.
 */
internal fun depthRange(rest: List<Row>, k: Int, hidesChildren: (Node) -> Boolean): IntRange {
    val prev = rest.getOrNull(k - 1) ?: return 0..0
    val max = if (hidesChildren(prev.node)) prev.depth else prev.depth + 1
    val min = (rest.getOrNull(k)?.depth ?: 0).coerceAtMost(max)
    return min..max
}

/** The rows from [rows] that are [n] and everything under it. */
internal fun subtreeIds(rows: List<Row>, n: Node): Set<String> {
    val i = rows.indexOfFirst { it.node === n }
    if (i < 0) return emptySet()
    val d = rows[i].depth
    val out = mutableSetOf(n.id)
    for (j in i + 1 until rows.size) {
        if (rows[j].depth <= d) break
        out += rows[j].node.id
    }
    return out
}

/**
 * Moves [n] (with its children) to just after [prev] in reading order, at
 * [depth]: a first child of [prev] when depth is one more than [prevDepth],
 * else a sibling after [prev]'s ancestor at that depth. No [prev]: the first
 * block of the page. Returns false (and changes nothing) when it cannot.
 */
internal fun placeNode(root: Node, n: Node, prev: Node?, prevDepth: Int, depth: Int): Boolean {
    if (prev === n) return false
    var p: Node? = prev
    while (p != null) { if (p === n) return false; p = p.parent }
    val oldParent = n.parent ?: return false
    val parent: Node
    val anchor: Node?
    if (prev == null) {
        parent = root; anchor = null
    } else if (depth == prevDepth + 1) {
        parent = prev; anchor = null
    } else {
        var a: Node = prev
        repeat(prevDepth - depth) { a = a.parent ?: return false }
        parent = a.parent ?: return false
        anchor = a
    }
    oldParent.children.remove(n)
    val at = if (anchor == null) 0 else parent.children.indexOf(anchor) + 1
    parent.children.add(at, n)
    n.parent = parent
    if (parent !== root) parent.collapsed = false
    return true
}
