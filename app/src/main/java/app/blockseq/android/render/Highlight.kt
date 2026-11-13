package app.blockseq.android.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/** Colours for code (from the theme: dark or light). */
data class CodeColors(val keyword: Color, val string: Color, val comment: Color, val number: Color, val type: Color)

fun codeColors(dark: Boolean) = if (dark) {
    CodeColors(keyword = Color(0xFFC792EA), string = Color(0xFFA5D6A7), comment = Color(0xFFE6C84F), number = Color(0xFFF78C6C), type = Color(0xFF82AAFF))
} else {
    CodeColors(keyword = Color(0xFF7B1FA2), string = Color(0xFF2E7D32), comment = Color(0xFF8D6E00), number = Color(0xFFC2410C), type = Color(0xFF1E5BB8))
}

private val COMMON_KEYWORDS = setOf(
    "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default", "try", "catch",
    "finally", "throw", "new", "class", "interface", "enum", "struct", "import", "export", "from", "as", "in", "is",
    "true", "false", "null", "nil", "none", "None", "True", "False", "this", "self", "super", "static", "public",
    "private", "protected", "const", "let", "var", "val", "fun", "fn", "func", "function", "def", "lambda", "async",
    "await", "yield", "with", "pub", "mod", "use", "impl", "trait", "match", "where", "mut", "ref", "type", "void",
    "int", "long", "float", "double", "bool", "boolean", "char", "string", "package", "extends", "implements", "and",
    "or", "not", "elif", "pass", "raise", "except", "global", "go", "defer", "chan", "select", "loop", "unsafe",
    "then", "fi", "esac", "done", "echo", "local", "export", "sudo", "when", "object", "override", "open", "data",
    "select", "insert", "update", "delete", "where", "join", "into", "values", "create", "table", "drop", "alter",
    "SELECT", "INSERT", "UPDATE", "DELETE", "WHERE", "FROM", "JOIN", "INTO", "VALUES", "CREATE", "TABLE", "AND", "OR",
)

/** How a language writes comments. */
private fun lineComment(lang: String): List<String> = when (lang.lowercase()) {
    "python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "toml", "perl", "r", "dockerfile", "make", "makefile", "conf", "ini", "nix", "elixir", "powershell", "ps1" -> listOf("#")
    "sql", "lua", "haskell", "hs" -> listOf("--")
    "lisp", "clojure", "scheme", "elisp" -> listOf(";")
    "html", "xml", "markdown", "md", "text", "txt", "plain", "" -> emptyList()
    else -> listOf("//")
}

private fun blockComments(lang: String): Boolean = when (lang.lowercase()) {
    "python", "py", "bash", "sh", "shell", "zsh", "yaml", "yml", "toml", "ruby", "rb", "sql", "lua", "text", "txt", "" -> lang.lowercase() == "sql"
    else -> true
}

/**
 * A small, forgiving highlighter: comments, strings, numbers, keywords and
 * capitalised type names. Good enough to read code on a phone; it never
 * changes the text.
 */
fun highlight(code: String, lang: String, c: CodeColors): AnnotatedString = buildAnnotatedString {
    val comments = lineComment(lang)
    val blocks = blockComments(lang)
    var i = 0
    val n = code.length
    fun startsWith(s: String) = code.startsWith(s, i)
    while (i < n) {
        val ch = code[i]
        val line = comments.firstOrNull { startsWith(it) && (it != "#" || i == 0 || code[i - 1].isWhitespace()) }
        when {
            line != null -> {
                val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) { append(code, i, end) }
                i = end
            }
            blocks && startsWith("/*") -> {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) { append(code, i, end) }
                i = end
            }
            ch == '"' || ch == '\'' || ch == '`' -> {
                var j = i + 1
                while (j < n && code[j] != ch && !(code[j] == '\n' && ch != '`')) j += if (code[j] == '\\') 2 else 1
                val end = minOf(n, j + 1)
                withStyle(SpanStyle(color = c.string)) { append(code, i, end) }
                i = end
            }
            ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_') -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                withStyle(SpanStyle(color = c.number)) { append(code, i, j) }
                i = j
            }
            ch.isLetter() || ch == '_' -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                when {
                    word in COMMON_KEYWORDS -> withStyle(SpanStyle(color = c.keyword)) { append(word) }
                    word.first().isUpperCase() && word.length > 1 && word.any { it.isLowerCase() } -> withStyle(SpanStyle(color = c.type)) { append(word) }
                    else -> append(word)
                }
                i = j
            }
            else -> { append(ch); i++ }
        }
    }
}
