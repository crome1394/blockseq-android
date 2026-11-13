package app.blockseq.android.outline

import java.util.UUID

/**
 * A page's blocks as a tree, and the text formats the server and the web
 * app use for them. Must stay compatible with web/js/render.js (splitProps,
 * joinProps, parseOutline) and editor.js (subtreeMarkdown) in the server
 * repo: encrypted children travel in exactly that outline format.
 */

/** A block as the API sends and receives it (flat, with a parent id). */
@kotlinx.serialization.Serializable
data class Block(
    val id: String,
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
    val content: String,
    val collapsed: Boolean = false,
)

/** A block in the editor's tree. [content] has the managed properties split out into [props]. */
class Node(
    var id: String,
    var content: String,
    var props: MutableMap<String, String> = linkedMapOf(),
    var collapsed: Boolean = false,
    val children: MutableList<Node> = mutableListOf(),
) {
    var parent: Node? = null

    /** An #Encrypted block whose children were decrypted on this phone. */
    var unsealed: Boolean = false

    /** The plaintext the children had when opened, to skip re-encrypting unchanged ones. */
    var sealedText: String? = null

    /** The encrypted children could not be decrypted (wrong key). */
    var decryptError: Boolean = false

    val encryptedBlob: String? get() = props[ENCRYPTED]
}

const val ENCRYPTED = "encrypted"

/** Properties the server's clients manage and hide while viewing and editing. */
val HIDDEN_PROPS = listOf("logseq.order-list-type", "background-color", "heading", ENCRYPTED, "text-align")

private val PROP_LINE = Regex("""^([\w.-]+):: *(.*)$""")
val ENCRYPTED_TAG = Regex("""(^|\s)#(?:encrypted|\[\[encrypted\]\])(?=\s|$)""", RegexOption.IGNORE_CASE)

fun newId(): String = UUID.randomUUID().toString()

/** Splits managed properties out of block text (never from the first line). */
fun splitProps(text: String): Pair<String, MutableMap<String, String>> {
    val props = linkedMapOf<String, String>()
    val keep = mutableListOf<String>()
    text.split('\n').forEachIndexed { i, line ->
        val m = PROP_LINE.matchEntire(line)
        if (i > 0 && m != null && m.groupValues[1] in HIDDEN_PROPS) props[m.groupValues[1]] = m.groupValues[2].trim()
        else keep += line
    }
    return keep.joinToString("\n") to props
}

/** Puts managed properties back after the first line (last, after code or a table). */
fun joinProps(content: String, props: Map<String, String>): String {
    val entries = props.filter { it.value.isNotEmpty() }
    if (entries.isEmpty()) return content
    val lines = entries.map { "${it.key}:: ${it.value}" }
    val parts = content.split('\n')
    val first = parts.first()
    if (first.startsWith("```") || Regex("""^\s*\|""").containsMatchIn(first)) return (listOf(content) + lines).joinToString("\n")
    return (listOf(first) + lines + parts.drop(1)).joinToString("\n")
}

/** Builds the tree from the API's flat list. Parents always come before children. */
fun buildTree(blocks: List<Block>): Node {
    val root = Node("", "")
    val byId = hashMapOf<String, Node>()
    for (b in blocks) {
        val (content, props) = splitProps(b.content)
        val node = Node(b.id, content, props, b.collapsed)
        val parent = b.parentId?.let { byId[it] } ?: root
        node.parent = parent
        parent.children += node
        byId[b.id] = node
    }
    return root
}

/**
 * The flat list the server stores. Children of an #Encrypted block are left
 * out once they are sealed: they travel inside its `encrypted::` property.
 */
fun flatten(root: Node): List<Block> {
    val out = mutableListOf<Block>()
    // Same rules as the web editor's flatten({ forSave: true }).
    fun walk(n: Node) {
        for (c in n.children) {
            val sealed = c.encryptedBlob != null && c.unsealed
            out += Block(c.id, if (n === root) null else n.id, joinProps(c.content, c.props), !sealed && c.collapsed && c.children.isNotEmpty())
            if (!sealed) walk(c)
        }
    }
    walk(root)
    return out
}

/** One block and its subtree as Logseq-style Markdown, with `id::` lines (editor.js subtreeMarkdown, ids on). */
fun subtreeMarkdown(node: Node): String {
    val lines = mutableListOf<String>()
    fun walk(n: Node, depth: Int) {
        val ind = "\t".repeat(depth)
        val parts = joinProps(n.content, n.props).split('\n')
        lines += "$ind- ${parts.first()}"
        lines += "$ind  id:: ${n.id}"
        parts.drop(1).forEach { lines += "$ind  $it" }
        // An open #Encrypted block keeps its children inside its property.
        if (n.encryptedBlob != null && n.unsealed) return
        n.children.forEach { walk(it, depth + 1) }
    }
    walk(node, 0)
    return lines.joinToString("\n")
}

/** Parses "- item" outline text (render.js parseOutline). Null when it is not an outline. */
fun parseOutline(text: String): List<Node>? {
    val lines = text.replace(Regex("\r\n?"), "\n").split('\n')
    val first = lines.firstOrNull { it.isNotBlank() } ?: return null
    if (!Regex("""^\s*-( |$)""").containsMatchIn(first)) return null
    fun width(ws: String) = ws.sumOf { if (it == '\t') 4 else 1 }
    data class Entry(val indent: Int, val raw: Int, val node: Node)
    val roots = mutableListOf<Node>()
    val stack = ArrayDeque<Entry>()
    var current: Entry? = null
    val item = Regex("""^(\s*)-(?: (.*)|$)""")
    val idProp = Regex("""^\s*(id|collapsed):: *(.*)$""")
    for (line in lines) {
        val m = item.matchEntire(line)
        if (m != null) {
            val indent = width(m.groupValues[1])
            while (stack.isNotEmpty() && stack.last().indent >= indent) stack.removeLast()
            val node = Node(newId(), m.groups[2]?.value ?: "")
            val parent = stack.lastOrNull()?.node
            if (parent != null) { node.parent = parent; parent.children += node } else roots += node
            val e = Entry(indent, m.groupValues[1].length, node)
            stack.addLast(e)
            current = e
            continue
        }
        val cur = current ?: continue
        val strip = cur.raw + 2
        val lead = line.length - line.trimStart().length
        val body = line.substring(minOf(lead, strip))
        val p = idProp.matchEntire(body)
        if (p != null) {
            val v = p.groupValues[2].trim()
            if (p.groupValues[1] == "id" && Regex("^[0-9a-fA-F-]{36}$").matches(v)) cur.node.id = v.lowercase()
            if (p.groupValues[1] == "collapsed") cur.node.collapsed = v == "true"
            continue
        }
        cur.node.content += "\n" + body
    }
    fun trim(nodes: List<Node>) { nodes.forEach { it.content = it.content.trimEnd('\n'); trim(it.children) } }
    trim(roots)
    // Managed properties (an encrypted block nested inside) are split out like the editor does.
    fun props(nodes: List<Node>) {
        nodes.forEach { n -> val (c, p) = splitProps(n.content); n.content = c; n.props = p; props(n.children) }
    }
    props(roots)
    return roots
}

/** Every block in the tree, depth first (not the root). */
fun allNodes(root: Node): List<Node> {
    val out = mutableListOf<Node>()
    fun walk(n: Node) { n.children.forEach { out += it; walk(it) } }
    walk(root)
    return out
}
