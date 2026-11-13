package app.blockseq.android.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blockseq.android.crypto.BlockCrypto
import app.blockseq.android.data.Api
import app.blockseq.android.data.PageData
import app.blockseq.android.data.SaveResult
import app.blockseq.android.data.Unpaired
import app.blockseq.android.outline.Block
import app.blockseq.android.outline.ENCRYPTED
import app.blockseq.android.outline.ENCRYPTED_TAG
import app.blockseq.android.outline.Node
import app.blockseq.android.outline.Statuses
import app.blockseq.android.outline.allNodes
import app.blockseq.android.outline.buildTree
import app.blockseq.android.outline.flatten
import app.blockseq.android.outline.newId
import app.blockseq.android.outline.parseOutline
import app.blockseq.android.outline.subtreeMarkdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A visible block and how deep it sits. */
data class Row(val node: Node, val depth: Int)

/** Why the page needs a choice before it saves again. */
sealed interface Conflict {
    /** Another device saved this page: their version. */
    data class Changed(val version: Long, val blocks: List<Block>) : Conflict
    /** Another device deleted or renamed this page. */
    data object Deleted : Conflict
}

/**
 * One page being viewed and edited: the block tree, autosave with the
 * server's version check, and #Encrypted blocks (opened with the key after
 * load, sealed again before every save). The same rules as the web editor
 * (editor.js, sync.js, secrets.js in the server repo).
 */
class PageEditor(
    private val api: Api,
    val name: String,
    private val statuses: Statuses,
    private val key: ByteArray?,
    private val scope: CoroutineScope,
    private val onUnpaired: (Throwable) -> Unit,
    private val onSynced: () -> Unit = {},
) {
    var root: Node = Node("", "")
        private set
    var version = 0L
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var conflict by mutableStateOf<Conflict?>(null)
        private set
    var lastSaved by mutableStateOf<Long?>(null)
        private set
    var backlinks by mutableStateOf<PageData?>(null)
        private set

    /** Bumped on every change to the tree, so the list redraws. */
    var revision by mutableIntStateOf(0)
        private set

    private var saveJob: Job? = null
    private var saveAgain = false
    private var overwriteNext = false
    /** Counts edits, so a save only marks the page clean when nothing changed while it was on its way. */
    private var edits = 0

    val rows: List<Row>
        get() {
            val out = mutableListOf<Row>()
            fun walk(n: Node, depth: Int) {
                for (c in n.children) {
                    out += Row(c, depth)
                    if (!c.collapsed && !isLocked(c)) walk(c, depth + 1)
                }
            }
            walk(root, 0)
            return out
        }

    /** An #Encrypted block whose children are still sealed (no key, or not decryptable). */
    fun isLocked(n: Node) = n.encryptedBlob != null && !n.unsealed

    fun isEncryptedRoot(n: Node) = ENCRYPTED_TAG.containsMatchIn(n.content) || n.encryptedBlob != null

    suspend fun load() {
        try {
            val data = api.page(name)
            apply(data.blocks, data.page?.version ?: 0)
            backlinks = data
            error = null
            // Tagged #Encrypted while locked (here or in the web app): encrypt those children now.
            if (key != null && needsSealing()) { dirty = true; edits++; scheduleSave(0) }
            onSynced()
        } catch (e: Unpaired) {
            onUnpaired(e)
        } catch (e: Exception) {
            error = e.message ?: "Could not load the page."
        }
        loaded = true
    }

    private fun apply(blocks: List<Block>, v: Long) {
        root = buildTree(blocks)
        version = v
        if (root.children.isEmpty()) appendBlock()
        openSecrets()
        changed(save = false)
        dirty = false
    }

    /** Decrypts every sealed block (and blocks nested inside those) when unlocked. */
    private fun openSecrets() {
        val k = key ?: return
        var again = true
        while (again) {
            again = false
            for (r in allNodes(root)) {
                val blob = r.encryptedBlob ?: continue
                if (r.unsealed || r.decryptError) continue
                try {
                    val text = BlockCrypto.decrypt(k, blob)
                    val kids = if (text.isBlank()) emptyList() else parseOutline(text) ?: emptyList()
                    val existing = allNodes(root).map { it.id }.toHashSet()
                    fun adopt(n: Node, parent: Node) {
                        if (!existing.add(n.id)) n.id = newId()
                        n.parent = parent
                        n.children.forEach { adopt(it, n) }
                    }
                    kids.forEach { adopt(it, r) }
                    r.children.addAll(0, kids)
                    r.unsealed = true
                    r.sealedText = text
                    again = true
                } catch (e: Exception) {
                    r.decryptError = true
                }
            }
        }
    }

    /**
     * #Encrypted blocks whose children are still plain text: tagged while
     * locked, as the web app allows (it warns and encrypts them on unlock).
     */
    fun needsSealing(): Boolean = allNodes(root).any(::notSealedYet)

    /** This block is tagged #Encrypted but its children are not encrypted yet. */
    fun notSealedYet(n: Node): Boolean = ENCRYPTED_TAG.containsMatchIn(n.content) && n.encryptedBlob == null && n.children.isNotEmpty()

    /** Before saving: encrypt the children of every #Encrypted block, innermost first. */
    private fun sealSecrets() {
        // Lock zeroes the key in place, and this editor may still hold it (a save queued
        // or retrying): never encrypt with an all-zero key.
        val k = key?.takeIf { kk -> kk.any { it != 0.toByte() } }
        val roots = allNodes(root).filter { isEncryptedRoot(it) }.sortedByDescending { depthOf(it) }
        for (r in roots) {
            val tagged = ENCRYPTED_TAG.containsMatchIn(r.content)
            if (!tagged && r.encryptedBlob != null && r.unsealed) {
                // Tag removed while unlocked: the children become normal blocks again.
                r.props.remove(ENCRYPTED)
                r.unsealed = false
                continue
            }
            if (!tagged) continue
            if (r.encryptedBlob != null && !r.unsealed) continue
            if (k == null) {
                // Unsealed but the key is gone: a change made before Lock cannot be sealed.
                if (r.unsealed && r.children.joinToString("\n") { subtreeMarkdown(it) } != r.sealedText) throw LockedOut()
                continue
            }
            val text = r.children.joinToString("\n") { subtreeMarkdown(it) }
            if (r.encryptedBlob == null || text != r.sealedText) {
                r.props[ENCRYPTED] = BlockCrypto.encrypt(k, text)
                r.sealedText = text
            }
            r.unsealed = true
        }
    }

    private fun depthOf(n: Node): Int {
        var d = 0
        var p = n.parent
        while (p != null) { d++; p = p.parent }
        return d
    }

    // ---- edits ---------------------------------------------------------------

    private fun changed(save: Boolean = true) {
        revision++
        if (save) {
            dirty = true
            edits++
            scheduleSave()
        }
    }

    fun setText(n: Node, text: String) {
        if (n.content == text) return
        n.content = text
        changed()
    }

    /** Enter in the middle of a block: the text after the cursor becomes a new block below. */
    fun split(n: Node, before: String, after: String): Node {
        n.content = before
        val fresh = Node(newId(), after)
        val parent = n.parent ?: root
        // A block with open children gets the new block as its first child, like the web editor.
        if (n.children.isNotEmpty() && !n.collapsed && !isLocked(n)) {
            fresh.parent = n
            n.children.add(0, fresh)
        } else {
            fresh.parent = parent
            parent.children.add(parent.children.indexOf(n) + 1, fresh)
        }
        changed()
        return fresh
    }

    fun appendBlock(): Node {
        val n = Node(newId(), "")
        n.parent = root
        root.children += n
        changed(save = false)
        return n
    }

    /** The row a quick note goes on: the last top-level block if it is empty, else a new one at the end. */
    fun noteRow(): Node {
        val last = root.children.lastOrNull()
        return if (last != null && last.content.isBlank() && last.children.isEmpty()) last else appendBlock()
    }

    /** A new empty block at the end of the page (saved once it has text). */
    fun addAtEnd(): Node = appendBlock()

    fun indent(n: Node) {
        val parent = n.parent ?: return
        val i = parent.children.indexOf(n)
        if (i <= 0) return
        val prev = parent.children[i - 1]
        if (isLocked(prev)) return
        parent.children.removeAt(i)
        n.parent = prev
        prev.children += n
        prev.collapsed = false
        changed()
    }

    fun outdent(n: Node) {
        val parent = n.parent ?: return
        val grand = parent.parent ?: return
        val i = parent.children.indexOf(n)
        // The siblings below come along as children, as in the web editor.
        val below = parent.children.subList(i + 1, parent.children.size).toList()
        parent.children.removeAll(below)
        parent.children.remove(n)
        below.forEach { it.parent = n }
        n.children.addAll(below)
        n.parent = grand
        grand.children.add(grand.children.indexOf(parent) + 1, n)
        changed()
    }

    /** Dropped after a drag: see [placeNode]. Blocks inside a locked encrypted block stay put. */
    fun moveAfter(n: Node, prev: Node?, prevDepth: Int, depth: Int): Boolean {
        if (prev != null && depth == prevDepth + 1 && isLocked(prev)) return false
        if (!placeNode(root, n, prev, prevDepth, depth)) return false
        changed()
        return true
    }

    /** A new empty block dropped after [prev] at [depth] (see [placeNode]); not saved until it has text. */
    fun newRowAfter(prev: Node?, prevDepth: Int, depth: Int): Node? {
        if (prev != null && depth == prevDepth + 1 && isLocked(prev)) return null
        val n = Node(newId(), "")
        n.parent = root
        root.children += n
        if (!placeNode(root, n, prev, prevDepth, depth)) { root.children.remove(n); return null }
        changed(save = false)
        return n
    }

    /** A new empty block beside [n]: above it or below it (below goes after its children). */
    fun insertBeside(n: Node, below: Boolean): Node {
        val parent = n.parent ?: root
        val fresh = Node(newId(), "")
        fresh.parent = parent
        parent.children.add(parent.children.indexOf(n) + if (below) 1 else 0, fresh)
        changed(save = false)
        return fresh
    }

    fun move(n: Node, dir: Int) {
        val parent = n.parent ?: return
        val i = parent.children.indexOf(n)
        val j = i + dir
        if (j !in parent.children.indices) return
        parent.children.removeAt(i)
        parent.children.add(j, n)
        changed()
    }

    /** Where a block sits, so a delete can be undone: its parent and place among the siblings. */
    fun placeOf(n: Node): Pair<Node, Int> { val p = n.parent ?: root; return p to p.children.indexOf(n) }

    /** Puts a deleted block back where [placeOf] said it was (the empty row a delete may add is dropped). */
    fun restore(n: Node, at: Pair<Node, Int>) {
        val (parent, i) = at
        root.children.singleOrNull()?.takeIf { it !== n && it.content.isBlank() && it.children.isEmpty() && parent === root }?.let { root.children.clear() }
        n.parent = parent
        parent.children.add(i.coerceIn(0, parent.children.size), n)
        changed()
    }

    /** Removes a block and everything under it. Returns the block above, to edit next. */
    fun delete(n: Node): Node? {
        val list = rows.map { it.node }
        val above = list.getOrNull(list.indexOf(n) - 1)
        (n.parent ?: root).children.remove(n)
        if (root.children.isEmpty()) appendBlock()
        changed()
        return above
    }

    fun toggleCollapse(n: Node) {
        if (n.children.isEmpty()) return
        n.collapsed = !n.collapsed
        changed()
    }

    /** Takes a picture's Markdown out of a block (the file stays on the server). */
    fun removeImage(n: Node, src: String) {
        val re = Regex("""!\[[^\]\n]*\]\(""" + Regex.escape(src) + """\)(?:\{[^}\n]*\})?""")
        val next = re.replace(n.content, "").replace(Regex("[ \t]+\n"), "\n").trim()
        setText(n, next)
    }

    /** A new block at the end of the page holding [text] (a picture just added). */
    fun appendWith(text: String): Node {
        val last = root.children.lastOrNull()
        val n = if (last != null && last.content.isBlank() && last.children.isEmpty()) last else appendBlock()
        n.content = text
        changed()
        return n
    }

    /**
     * Adds a #tag at the end of a block's first line (as the web app shows
     * tags), unless the block already has it. A block that starts with a
     * code fence gets the tag on a line after its text.
     */
    fun addTag(n: Node, tag: String) {
        val name = tag.trim().removePrefix("#").removePrefix("[[").removeSuffix("]]").trim()
        if (name.isEmpty()) return
        val text = if (Regex("""[\s#,.!?;:()\[\]"'`]""").containsMatchIn(name)) "#[[$name]]" else "#$name"
        val has = Regex("""(^|\s)#(\[\[)?""" + Regex.escape(name) + """(\]\])?(?=\s|$)""", RegexOption.IGNORE_CASE)
        if (has.containsMatchIn(n.content)) return
        val lines = n.content.split('\n').toMutableList()
        if (lines.first().trimStart().startsWith("```")) lines += text
        else lines[0] = lines[0].trimEnd().let { if (it.isEmpty()) text else "$it $text" }
        setText(n, lines.joinToString("\n"))
    }

    /** Sets or clears a managed property (colour, alignment, list type, auto heading). */
    fun setProp(n: Node, key: String, value: String?) {
        if (value == null) n.props.remove(key) else n.props[key] = value
        changed()
    }

    /** A Markdown heading (1–6), 0 for none; "auto" sizes it by depth (heading:: true), as the web app does. */
    fun setHeading(n: Node, level: Int, auto: Boolean = false) {
        val marker = statuses.prefix(n.content)
        val rest = n.content.substring(marker.length).replace(Regex("^#{1,6} "), "")
        n.content = marker + (if (level in 1..6) "#".repeat(level) + " " else "") + rest
        if (auto) n.props[HEADING] = "true" else n.props.remove(HEADING)
        changed()
    }

    /** Strikes through the first line (or clears it); status, heading and trailing tags stay outside. */
    fun toggleStrike(n: Node) {
        val nl = n.content.indexOf('\n')
        val first = if (nl == -1) n.content else n.content.substring(0, nl)
        setText(n, toggleLine(first, "~~", statuses.prefix(first)) + (if (nl == -1) "" else n.content.substring(nl)))
    }

    fun toggleNumbered(n: Node) {
        if (n.props[NUMBERED] == "number") n.props.remove(NUMBERED) else n.props[NUMBERED] = "number"
        changed()
    }

    /** Adds "[[Nov 12th, 2026]] 14:05" before the trailing tags of the first line (web editor addTimestamp). */
    fun addTimestamp(n: Node, stamp: String) {
        val text = n.content
        val nl = text.indexOf('\n')
        val first = if (nl == -1) text else text.substring(0, nl)
        val rest = text.substring(first.length)
        n.content = when {
            first.isBlank() -> stamp + rest
            first.startsWith("```") || Regex("""^\s*\|""").containsMatchIn(first) -> "$stamp\n$text"
            else -> {
                val tags = Regex("""((?:\s+(?:#\[\[[^\]\n]+\]\]|#[^\s#,.!?;:()\[\]"'`]+))*)\s*$""").find(first)!!
                val cut = tags.range.first
                "${first.substring(0, cut)} $stamp${first.substring(cut)}$rest"
            }
        }
        changed()
    }

    /** One block and its children as Logseq Markdown (no ids), or as indented plain text. */
    fun export(n: Node, plain: Boolean): String {
        val lines = mutableListOf<String>()
        fun walk(x: Node, depth: Int) {
            val ind = "\t".repeat(depth)
            if (plain) x.content.split('\n').forEach { lines += ind + it }
            else {
                val parts = app.blockseq.android.outline.joinProps(x.content, x.props.filterKeys { it != ENCRYPTED }).split('\n')
                lines += "$ind- ${parts.first()}"
                parts.drop(1).forEach { lines += "$ind  $it" }
            }
            if (!isLocked(x)) x.children.forEach { walk(it, depth + 1) }
        }
        walk(n, 0)
        return lines.joinToString("\n")
    }

    fun cycleStatus(n: Node) {
        n.content = statuses.with(n.content, statuses.next(statuses.of(n.content)?.word))
        changed()
    }

    // ---- saving --------------------------------------------------------------

    private fun scheduleSave(delayMs: Long = 800) {
        if (conflict != null) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(delayMs)
            // Only the wait is cancelled by the next edit: a save already sent must finish,
            // or the server's new version is dropped and the next save conflicts with itself.
            withContext(NonCancellable) { save() }
        }
    }

    /** Saves now if anything changed. Safe to call any time (e.g. when leaving the page). */
    suspend fun save() {
        if (!dirty || conflict != null) return
        if (saving) { saveAgain = true; return }
        saving = true
        try {
            val sent = edits
            // On the caller's (main) thread: the tree is edited there, and sealing writes to it.
            // AES-GCM on a page's text is quick; only unlocking (PBKDF2) is slow.
            sealSecrets()
            val blocks = flatten(root)
            val overwrite = overwriteNext
            when (val r = api.savePage(name, version, blocks, overwrite)) {
                is SaveResult.Saved -> {
                    version = r.page.version
                    if (overwrite) overwriteNext = false
                    lastSaved = System.currentTimeMillis()
                    onSynced()
                    error = null
                    if (!saveAgain && edits == sent) dirty = false
                }
                is SaveResult.Conflict -> conflict = Conflict.Changed(r.page.version, r.blocks)
                SaveResult.Deleted -> conflict = Conflict.Deleted
                is SaveResult.ForeignIds -> {
                    // Pasted copies of blocks from another page get new ids.
                    val bad = r.ids.toHashSet()
                    allNodes(root).filter { it.id in bad }.forEach { it.id = newId() }
                    saveAgain = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Unpaired) {
            onUnpaired(e)
        } catch (e: LockedOut) {
            // Retrying cannot help: the key is gone.
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: "Could not save."
            scheduleSave(4000)
        } finally {
            saving = false
        }
        if (saveAgain) {
            saveAgain = false
            dirty = true
            // Directly, not via [scope]: it may be gone (the last save as the page closes).
            save()
        }
    }

    /** Their version wins: this phone's unsaved change is dropped. */
    fun useTheirs() {
        val c = conflict as? Conflict.Changed ?: return
        conflict = null
        apply(c.blocks, c.version)
    }

    /** What differs between the other device's version and this phone's, line by line. */
    fun conflictDiff(): List<DiffLine>? {
        val c = conflict as? Conflict.Changed ?: return null
        return diffLines(outlineLines(app.blockseq.android.outline.buildTree(c.blocks)), outlineLines(root))
    }

    /** This phone's version wins; the server keeps theirs in the page history. */
    fun keepMine() {
        val c = conflict ?: return
        version = if (c is Conflict.Changed) c.version else 0
        overwriteNext = true
        conflict = null
        dirty = true
        scheduleSave(0)
    }

    /** The page was deleted elsewhere: drop this phone's change. */
    fun leaveDeleted() {
        conflict = null
        dirty = false
    }

    /** Another device saved: reload, unless this phone has unsaved changes (the next save reports the conflict). */
    suspend fun remoteChanged() {
        if (dirty || saving) return
        load()
    }
}

/** A change inside an #Encrypted block was still unsaved when the key was dropped. */
class LockedOut : IllegalStateException("Locked before a change to an encrypted block was saved. Unlock and make the change again.")

/** Managed properties (stored the way Logseq writes them). */
const val COLOR = "background-color"
const val ALIGN = "text-align"
const val HEADING = "heading"
const val NUMBERED = "logseq.order-list-type"

private val TRAILING_TAG_RUN = Regex("""(?:\s+(?:#\[\[[^\]\n]+\]\]|#[^\s#,.!?;:()\[\]"'`+][^\s#,.!?;:()\[\]"'`]*))+\s*$""")
private val MARKS = listOf("**", "_", "~~")

private fun toggleMark(core: String, mark: String): String {
    val m = mark.length
    if (core.length >= 2 * m && core.startsWith(mark) && core.endsWith(mark)) return core.substring(m, core.length - m)
    for (other in MARKS) {
        val o = other.length
        if (other != mark && core.length >= 2 * o && core.startsWith(other) && core.endsWith(other)) {
            return other + toggleMark(core.substring(o, core.length - o), mark) + other
        }
    }
    return if (core.isEmpty()) core else mark + core + mark
}

/**
 * Toggles a mark (`~~`, `**`) around one line's text, as the web editor's
 * `toggleLine`: a status word ([statusPrefix]), heading marker and trailing
 * #tags stay outside. Code fences, tables and property lines are left alone.
 */
fun toggleLine(line: String, mark: String, statusPrefix: String = ""): String {
    if (Regex("""^\s*(```|#\+|\|)""").containsMatchIn(line) || Regex("""^[\w.-]+:: """).containsMatchIn(line)) return line
    var pre = statusPrefix
    var rest = line.substring(pre.length)
    val h = Regex("""^(\s*(?:#{1,6} |> )?)""").find(rest)!!.value
    pre += h
    rest = rest.substring(h.length)
    var tail = ""
    TRAILING_TAG_RUN.find(rest)?.takeIf { it.range.first > 0 }?.let { tail = rest.substring(it.range.first); rest = rest.substring(0, it.range.first) }
    val ws = rest.length - rest.trimEnd().length
    if (ws > 0) { tail = rest.substring(rest.length - ws) + tail; rest = rest.substring(0, rest.length - ws) }
    return pre + toggleMark(rest, mark) + tail
}

/** Toggles a mark around the selection [start, end) of [text]; returns the text and the new selection. */
fun toggleSelection(text: String, start: Int, end: Int, mark: String): Triple<String, Int, Int> {
    val m = mark.length
    val sel = text.substring(start, end)
    return when {
        sel.length >= 2 * m && sel.startsWith(mark) && sel.endsWith(mark) ->
            Triple(text.substring(0, start) + sel.substring(m, sel.length - m) + text.substring(end), start, end - 2 * m)
        start >= m && text.startsWith(mark, start - m) && text.startsWith(mark, end) ->
            Triple(text.substring(0, start - m) + sel + text.substring(end + m), start - m, end - m)
        else -> Triple(text.substring(0, start) + mark + sel + mark + text.substring(end), start + m, end + m)
    }
}
