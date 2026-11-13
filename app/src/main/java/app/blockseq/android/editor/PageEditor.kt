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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** Inside an #Encrypted block (the children it protects). */
    fun insideEncrypted(n: Node): Boolean {
        var p = n.parent
        while (p != null) {
            if (isEncryptedRoot(p)) return true
            p = p.parent
        }
        return false
    }

    suspend fun load() {
        try {
            val data = api.page(name)
            apply(data.blocks, data.page?.version ?: 0)
            backlinks = data
            error = null
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

    /** Before saving: encrypt the children of every #Encrypted block, innermost first. */
    private fun sealSecrets() {
        val roots = allNodes(root).filter { isEncryptedRoot(it) }.sortedByDescending { depthOf(it) }
        for (r in roots) {
            val tagged = ENCRYPTED_TAG.containsMatchIn(r.content)
            if (!tagged && r.encryptedBlob != null && r.unsealed) {
                // Tag removed while unlocked: the children become normal blocks again.
                r.props.remove(ENCRYPTED)
                r.unsealed = false
                continue
            }
            if (!tagged || key == null) continue
            if (r.encryptedBlob != null && !r.unsealed) continue
            val text = r.children.joinToString("\n") { subtreeMarkdown(it) }
            if (r.encryptedBlob == null || text != r.sealedText) {
                r.props[ENCRYPTED] = BlockCrypto.encrypt(key, text)
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

    /** A new empty block at the end of the page (saved once it has text). */
    fun addAtEnd(): Node = appendBlock()

    fun addChild(parent: Node): Node {
        val n = Node(newId(), "")
        n.parent = parent
        parent.children.add(0, n)
        parent.collapsed = false
        changed()
        return n
    }

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

    fun move(n: Node, dir: Int) {
        val parent = n.parent ?: return
        val i = parent.children.indexOf(n)
        val j = i + dir
        if (j !in parent.children.indices) return
        parent.children.removeAt(i)
        parent.children.add(j, n)
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
            save()
        }
    }

    /** Saves now if anything changed. Safe to call any time (e.g. when leaving the page). */
    suspend fun save() {
        if (!dirty || conflict != null) return
        if (saving) { saveAgain = true; return }
        saving = true
        try {
            val blocks = withContext(Dispatchers.Default) {
                sealSecrets()
                flatten(root)
            }
            val overwrite = overwriteNext
            when (val r = api.savePage(name, version, blocks, overwrite)) {
                is SaveResult.Saved -> {
                    version = r.page.version
                    if (overwrite) overwriteNext = false
                    lastSaved = System.currentTimeMillis()
                    error = null
                    if (!saveAgain) dirty = false
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
        } catch (e: Unpaired) {
            onUnpaired(e)
        } catch (e: Exception) {
            error = e.message ?: "Could not save."
            scheduleSave(4000)
        } finally {
            saving = false
        }
        if (saveAgain) {
            saveAgain = false
            dirty = true
            scheduleSave(0)
        }
    }

    /** Their version wins: this phone's unsaved change is dropped. */
    fun useTheirs() {
        val c = conflict as? Conflict.Changed ?: return
        conflict = null
        apply(c.blocks, c.version)
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
