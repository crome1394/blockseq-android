package app.blockseq.android.editor

import app.blockseq.android.outline.Node

/** One line of a comparison: the same on both, only theirs, or only mine. */
data class DiffLine(val kind: Kind, val text: String, val changed: List<IntRange> = emptyList()) {
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
    return markWords(out)
}

/**
 * In each changed stretch (their lines, then mine), pairs the lines in order
 * and marks the words that differ, so a row edited on both sides shows just
 * its changed words. Lines too unlike each other are left unmarked.
 */
private fun markWords(lines: List<DiffLine>): List<DiffLine> {
    val out = lines.toMutableList()
    var i = 0
    while (i < out.size) {
        if (out[i].kind != DiffLine.Kind.Theirs) { i++; continue }
        var t = i
        while (t < out.size && out[t].kind == DiffLine.Kind.Theirs) t++
        var m = t
        while (m < out.size && out[m].kind == DiffLine.Kind.Mine) m++
        for (k in 0 until minOf(t - i, m - t)) {
            val (a, b) = wordDiff(out[i + k].text, out[t + k].text) ?: continue
            out[i + k] = out[i + k].copy(changed = a)
            out[t + k] = out[t + k].copy(changed = b)
        }
        i = m
    }
    return out
}

private val TOKEN = Regex("""\s+|[\p{L}\p{N}_]+|.""")

/** The character ranges that differ in [a] and in [b], word by word; null when they share under a third. */
internal fun wordDiff(a: String, b: String): Pair<List<IntRange>, List<IntRange>>? {
    val x = TOKEN.findAll(a).toList(); val y = TOKEN.findAll(b).toList()
    val lcs = Array(x.size + 1) { IntArray(y.size + 1) }
    for (i in x.indices.reversed()) for (j in y.indices.reversed())
        lcs[i][j] = if (x[i].value == y[j].value) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    val keptA = BooleanArray(x.size); val keptB = BooleanArray(y.size)
    var i = 0; var j = 0
    while (i < x.size && j < y.size) when {
        x[i].value == y[j].value -> { keptA[i] = true; keptB[j] = true; i++; j++ }
        lcs[i + 1][j] >= lcs[i][j + 1] -> i++
        else -> j++
    }
    val shared = keptA.indices.filter { keptA[it] && x[it].value.isNotBlank() }.sumOf { x[it].value.length }
    if (shared * 3 < maxOf(a.trim().length, b.trim().length)) return null
    // Changed words next to each other (only spaces between) make one mark.
    fun ranges(src: String, t: List<MatchResult>, kept: BooleanArray): List<IntRange> {
        val r = mutableListOf<IntRange>()
        t.forEachIndexed { k, m ->
            if (kept[k] || m.value.isBlank()) return@forEachIndexed
            val last = r.lastOrNull()
            if (last != null && src.substring(last.last + 1, m.range.first).isBlank()) r[r.size - 1] = last.first..m.range.last
            else r += m.range
        }
        return r
    }
    return ranges(a, x, keptA) to ranges(b, y, keptB)
}

