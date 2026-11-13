package app.blockseq.android.outline

/**
 * Task statuses, matching web/js/tasks.js: a block whose first word is a
 * status word is a task, and carries a #task tag at the end of its first line.
 */
@kotlinx.serialization.Serializable
data class Status(val word: String, val label: String, val color: String = "gray", val done: Boolean = false)

private val TASK_TAG = Regex("""(^|\s)#task(?=\s|$)""", RegexOption.IGNORE_CASE)
private val TASK_TAG_STRIP = Regex("""(^|\s+)#task(?=\s|$)""", RegexOption.IGNORE_CASE)

class Statuses(val list: List<Status>) {
    fun of(content: String): Status? {
        val word = Regex("""^(\S+)(?:\s|$)""").find(content)?.groupValues?.get(1) ?: return null
        return list.firstOrNull { it.word == word }
    }

    /** "TODO " (with its space) at the start of the text, or "". */
    fun prefix(content: String): String {
        val st = of(content) ?: return ""
        val n = st.word.length
        return content.substring(0, n) + if (content.getOrNull(n) == ' ') " " else ""
    }

    fun strip(content: String): String = content.substring(prefix(content).length)

    fun with(content: String, word: String?): String {
        val body = strip(content)
        val nl = body.indexOf('\n')
        var first = if (nl == -1) body else body.substring(0, nl)
        val rest = if (nl == -1) "" else body.substring(nl)
        if (word != null) {
            if (!TASK_TAG.containsMatchIn(body) && !first.startsWith("```")) {
                first = first.trimEnd() + (if (first.isNotBlank()) " " else "") + "#task"
            }
            return "$word $first$rest"
        }
        first = TASK_TAG_STRIP.replaceFirst(first, "").trimEnd()
        return first + rest
    }

    /** Tap order: none → first → … → last → none. */
    fun next(word: String?): String? {
        val i = list.indexOfFirst { it.word == word }
        return if (i == -1) list.firstOrNull()?.word else list.getOrNull(i + 1)?.word
    }

    companion object {
        val DEFAULT = Statuses(
            listOf(
                Status("BACKLOG", "Backlog", "gray"), Status("TODO", "Todo", "blue"), Status("DOING", "Doing", "amber"),
                Status("IN-REVIEW", "In Review", "purple"), Status("DONE", "Done", "green", true), Status("CANCELED", "Cancelled", "red", true),
            ),
        )
    }
}
