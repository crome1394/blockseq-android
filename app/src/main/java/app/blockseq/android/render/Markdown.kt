package app.blockseq.android.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** Where a tapped link goes. */
sealed interface Target {
    data class Page(val name: String) : Target
    data class Url(val url: String) : Target
    data class BlockRef(val id: String) : Target
    /** The copy button after ``code``. */
    data class Copy(val text: String) : Target
}

/** Colours the inline renderer uses (taken from the Material theme). */
data class InlineColors(val link: Color, val tag: Color, val code: Color, val codeBackground: Color, val highlight: Color, val dark: Boolean = true)

private val TAG_HUES = intArrayOf(355, 22, 42, 95, 145, 172, 195, 215, 240, 265, 290, 322)

/** The web app's tagHue (render.js): a stable hue per tag name, so a tag has one colour everywhere. */
fun tagHue(name: String): Int {
    var h = 2166136261L.toInt()
    name.lowercase().codePoints().forEach { cp -> h = (h xor cp) * 16777619 }
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    return TAG_HUES[(h.toUInt() % TAG_HUES.size.toUInt()).toInt()]
}

/** A tag's colour: text, and its light background, like the web app's coloured tags. */
fun tagColors(name: String, dark: Boolean): Pair<Color, Color> {
    val hue = tagHue(name).toFloat()
    val text = if (dark) Color.hsl(hue, 0.62f, 0.72f) else Color.hsl(hue, 0.58f, 0.32f)
    return text to text.copy(alpha = 0.14f)
}

private fun AnnotatedString.Builder.tag(name: String, c: InlineColors, onTap: (Target) -> Unit) {
    val (fg, bg) = tagColors(name, c.dark)
    link("#$name", Target.Page(name), SpanStyle(color = fg, background = bg), onTap)
}

private data class Rule(val regex: Regex, val apply: AnnotatedString.Builder.(MatchResult, InlineColors, (Target) -> Unit) -> Unit)

private val TAG_CHARS = """[^\s#,.!?;:()\[\]"'`]+"""

/**
 * Inline Markdown as the web app writes it: `code`, [[page]], #tag,
 * #[[tag]], [text](url), ((block ref)), **bold**, *italic*, ~~strike~~,
 * ==highlight==, $maths$ (shown as source) and bare links.
 */
fun inlineMarkdown(text: String, colors: InlineColors, onTap: (Target) -> Unit): AnnotatedString =
    buildAnnotatedString { appendInline(text, colors, onTap) }

private fun AnnotatedString.Builder.link(label: String, target: Target, style: SpanStyle, onTap: (Target) -> Unit) {
    withLink(LinkAnnotation.Clickable(target.toString(), TextLinkStyles(style)) { onTap(target) }) { append(label) }
}

private val RULES: List<Rule> = listOf(
    // ``text`` is code with a copy button (as in the web app); `text` is plain inline code.
    Rule(Regex("``(.+?)``|`([^`]+)`")) { m, c, tap ->
        val code = m.groupValues[1].ifEmpty { m.groupValues[2] }.let { if (m.groupValues[1].isNotEmpty()) it.removeSurrounding(" ") else it.trim() }
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.code, background = c.codeBackground)) { append(code) }
        if (m.groupValues[1].isNotEmpty()) {
            append("\u2009")
            link("⧉", Target.Copy(code), SpanStyle(color = c.link, background = c.codeBackground, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified), tap)
        }
    },
    Rule(Regex("""\[\[([^\]]+)\]\]""")) { m, c, tap ->
        link(m.groupValues[1], Target.Page(m.groupValues[1]), SpanStyle(color = c.link), tap)
    },
    Rule(Regex("""(?<=^|\s)#\[\[([^\]]+)\]\]""")) { m, c, tap ->
        tag(m.groupValues[1], c, tap)
    },
    Rule(Regex("""(?<=^|\s)#($TAG_CHARS)""")) { m, c, tap ->
        tag(m.groupValues[1], c, tap)
    },
    Rule(Regex("""(?<!!)\[([^\]]+)\]\((https?://[^)\s]+)\)""")) { m, c, tap ->
        link(m.groupValues[1], Target.Url(m.groupValues[2]), SpanStyle(color = c.link, textDecoration = TextDecoration.Underline), tap)
    },
    Rule(Regex("""\(\(([0-9a-fA-F-]{36})\)\)""")) { m, c, tap ->
        link("↪ block", Target.BlockRef(m.groupValues[1]), SpanStyle(color = c.link), tap)
    },
    Rule(Regex("""\*\*(.+?)\*\*|__(.+?)__""")) { m, c, tap ->
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(m.groupValues[1].ifEmpty { m.groupValues[2] }, c, tap) }
    },
    Rule(Regex("""(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")) { m, c, tap ->
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(m.groupValues[1].ifEmpty { m.groupValues[2] }, c, tap) }
    },
    Rule(Regex("~~(.+?)~~")) { m, c, tap ->
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(m.groupValues[1], c, tap) }
    },
    Rule(Regex("==(.+?)==")) { m, c, tap ->
        withStyle(SpanStyle(background = c.highlight)) { appendInline(m.groupValues[1], c, tap) }
    },
    Rule(Regex("""(?<![$\\])\$(?!\s)([^$\n]+?)(?<!\s)\$(?!\$)""")) { m, c, _ ->
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.code)) { append(m.groupValues[1]) }
    },
    Rule(Regex("""https?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"]""")) { m, c, tap ->
        link(m.value, Target.Url(m.value), SpanStyle(color = c.link, textDecoration = TextDecoration.Underline), tap)
    },
)

private fun AnnotatedString.Builder.appendInline(text: String, colors: InlineColors, onTap: (Target) -> Unit) {
    var pos = 0
    while (pos < text.length) {
        // The earliest match wins; on a tie, the rule listed first.
        var best: Pair<Rule, MatchResult>? = null
        for (rule in RULES) {
            val m = rule.regex.find(text, pos) ?: continue
            if (best == null || m.range.first < best.second.range.first) best = rule to m
        }
        if (best == null) {
            append(text.substring(pos))
            return
        }
        val (rule, m) = best
        append(text.substring(pos, m.range.first))
        rule.apply(this, m, colors, onTap)
        pos = m.range.last + 1
    }
}

/** Parts of a block, in order, for the block renderer. */
sealed interface Part {
    data class Text(val text: String, val heading: Int = 0, val quote: Boolean = false) : Part
    data class Code(val lang: String, val code: String) : Part
    data class Maths(val tex: String) : Part
    data class Table(val rows: List<List<String>>) : Part
    data class Admonition(val kind: String, val text: String) : Part
    data class Image(val alt: String, val src: String) : Part
}

private val IMAGE = Regex("""^!\[([^\]\n]*)\]\(([^)\s]+)\)(?:\{[^}\n]*\})?\s*$""")
private val INLINE_IMAGE = Regex("""!\[([^\]\n]*)\]\(([^)\s]+)\)(?:\{[^}\n]*\})?""")
private val HEADING = Regex("^(#{1,6}) (.*)$")
private val ADMONITION = Regex("""^#\+BEGIN_(\w+)\s*$""", RegexOption.IGNORE_CASE)

/** Splits block text into paragraphs, code, maths, tables, admonitions and images. */
fun parts(content: String): List<Part> {
    val lines = content.split('\n')
    val out = mutableListOf<Part>()
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) out += Part.Text(para.joinToString("\n"))
        para.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                out += Part.Code(lang, code.joinToString("\n"))
            }
            t.startsWith("$$") || t.startsWith("\\[") -> {
                flush()
                val close = if (t.startsWith("$$")) "$$" else "\\]"
                val open = if (t.startsWith("$$")) "$$" else "\\["
                val first = t.removePrefix(open)
                if (first.endsWith(close) && first.length >= close.length) {
                    out += Part.Maths(first.removeSuffix(close).trim())
                } else {
                    val tex = mutableListOf(first)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith(close)) tex += lines[i++]
                    if (i < lines.size) tex += lines[i].trim().removeSuffix(close)
                    out += Part.Maths(tex.joinToString("\n").trim())
                }
            }
            ADMONITION.matches(t) -> {
                flush()
                val kind = ADMONITION.find(t)!!.groupValues[1].lowercase()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().equals("#+END_$kind", ignoreCase = true)) body += lines[i++]
                out += Part.Admonition(kind, body.joinToString("\n"))
            }
            t.startsWith("|") -> {
                flush()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                    if (!cells.all { Regex("^:?-{2,}:?$").matches(it) }) rows += cells
                    i++
                }
                i--
                out += Part.Table(rows)
            }
            IMAGE.matches(t) -> {
                flush()
                val m = IMAGE.find(t)!!
                out += Part.Image(m.groupValues[1], m.groupValues[2])
            }
            HEADING.matches(line) -> {
                flush()
                val m = HEADING.find(line)!!
                out += Part.Text(m.groupValues[2], heading = m.groupValues[1].length)
            }
            line.startsWith("> ") -> {
                flush()
                out += Part.Text(line.removePrefix("> "), quote = true)
            }
            INLINE_IMAGE.containsMatchIn(line) -> {
                // A picture in the middle of a line: the text around it stays.
                var rest = line
                while (true) {
                    val m = INLINE_IMAGE.find(rest) ?: break
                    val before = rest.substring(0, m.range.first)
                    if (before.isNotBlank()) para += before
                    flush()
                    out += Part.Image(m.groupValues[1], m.groupValues[2])
                    rest = rest.substring(m.range.last + 1)
                }
                if (rest.isNotBlank()) para += rest
            }
            else -> para += line
        }
        i++
    }
    flush()
    return out
}
