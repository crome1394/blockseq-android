package app.blockseq.android.editor

import app.blockseq.android.outline.Node

/** One line of a comparison: the same on both, only theirs, or only mine. */
data class DiffLine(val kind: Kind, val text: String) {
    enum class Kind { Same, Theirs, Mine }
}

/**
 * A page as lines for comparing: one per block line, indented two spaces a
 * level, properties left out. Children inside an encrypted block are shown as
 * one 🔒 line on both sides (the other device's are still sealed).
 */
internal fun outlineLines(root: Node): List<String> = buildList {
    fun walk(n: Node, depth: Int) {
        val ind = "  ".repeat(depth)
        n.content.split('\n').forEachIndexed { i, l -> add(ind + (if (i == 0) "• " else "  ") + l) }
        if (n.encryptedBlob != null) { add("$ind  🔒 encrypted children"); return }
        n.children.forEach { walk(it, depth + 1) }
    }
    root.children.forEach { walk(it, 0) }
}

/** A line diff (longest common subsequence), in reading order. */
internal fun diffLines(theirs: List<String>, mine: List<String>): List<DiffLine> {
    val a = theirs; val b = mine
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed())
        lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    val out = mutableListOf<DiffLine>()
    var i = 0; var j = 0
    while (i < a.size || j < b.size) when {
        i < a.size && j < b.size && a[i] == b[j] -> { out += DiffLine(DiffLine.Kind.Same, a[i]); i++; j++ }
        i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> { out += DiffLine(DiffLine.Kind.Theirs, a[i]); i++ }
        else -> { out += DiffLine(DiffLine.Kind.Mine, b[j]); j++ }
    }
    return out
}
