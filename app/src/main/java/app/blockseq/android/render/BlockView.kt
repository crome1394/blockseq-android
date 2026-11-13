package app.blockseq.android.render

import android.util.LruCache
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blockseq.android.data.Api
import app.blockseq.android.outline.Status
import app.blockseq.android.outline.Statuses

/** The web app's status colours (hue); gray has none. */
private val HUES = mapOf("red" to 4f, "orange" to 28f, "amber" to 40f, "green" to 140f, "teal" to 175f, "blue" to 214f, "purple" to 270f, "pink" to 330f)

@Composable
fun statusColor(st: Status): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val hue = HUES[st.color]
    val sat = if (hue == null) 0f else 0.55f
    val h = hue ?: 0f
    return if (dark) Color.hsl(h, sat, 0.25f) to Color.hsl(h, sat, 0.82f) else Color.hsl(h, sat, 0.9f) to Color.hsl(h, sat, 0.3f)
}

private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
fun StatusBadge(st: Status, onTap: (() -> Unit)?) {
    val (bg, fg) = statusColor(st)
    Text(
        st.label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(end = 6.dp, top = 2.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Links and tags use fixed colours: the wallpaper-based theme can pick an
 * accent close to the text colour (a black-and-white wallpaper gives grey).
 */
@Composable
fun inlineColors(): InlineColors {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    return inlineColorsFor(dark)
}

@Composable
private fun inlineColorsFor(dark: Boolean) = InlineColors(
    link = if (dark) Color(0xFF6CC9C9) else Color(0xFF1B6B6B),
    tag = if (dark) Color(0xFFD1A2EA) else Color(0xFF7A3C9E),
    code = MaterialTheme.colorScheme.onSurfaceVariant,
    codeBackground = MaterialTheme.colorScheme.surfaceVariant,
    highlight = Color(0x66FFD54F),
    dark = dark,
)

private val TASK_TAG = Regex("""(^|\s+)#task(?=\s|$)""", RegexOption.IGNORE_CASE)

/**
 * One block's text, rendered. [onStatusTap] steps the task status (null:
 * the badge is not tappable).
 */
@Composable
fun BlockContent(
    content: String,
    statuses: Statuses,
    api: Api?,
    onTap: (Target) -> Unit,
    onStatusTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Takes a picture out of this block (the picture viewer offers it); null: not offered. */
    onRemoveImage: ((src: String) -> Unit)? = null,
    /** Managed properties already split out of [content] (the editor's blocks). */
    props: Map<String, String> = emptyMap(),
    /** How deep the block sits (for "auto" headings). */
    depth: Int = 0,
) {
    // Colour, alignment and heading props, from the editor or from the text itself.
    val all = app.blockseq.android.outline.splitProps(content).second + props
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val bg = all["background-color"]?.let { rowColor(it, dark) }
    val align = when (all["text-align"]) { "center" -> TextAlign.Center; "right" -> TextAlign.End; else -> TextAlign.Start }
    val autoHeading = if (all["heading"] == "true") (depth + 2).coerceAtMost(4) else 0
    Box(if (bg != null) modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp) else modifier) {
        BlockBody(content, statuses, api, onTap, onStatusTap, onRemoveImage, align, autoHeading)
    }
}

/** The web app's row highlight colours (default theme), light and dark. */
fun rowColor(name: String, dark: Boolean): Color? {
    val light = mapOf("yellow" to 0xFFFBF1C7, "red" to 0xFFFBDCDC, "pink" to 0xFFFADCEC, "green" to 0xFFDCF2DF, "blue" to 0xFFDBE9FB, "purple" to 0xFFEBE1FA, "gray" to 0xFFEBEBE8)
    val darkMap = mapOf("yellow" to 0xFF4A3F14, "red" to 0xFF4D2323, "pink" to 0xFF4A2338, "green" to 0xFF1F3D27, "blue" to 0xFF1E3350, "purple" to 0xFF352650, "gray" to 0xFF33373A)
    return (if (dark) darkMap else light)[name]?.let { Color(it) }
}

/** The colour swatches the block menu offers (the web app's toolbar colours). */
val SWATCHES = listOf("yellow" to 0xFFE6B400, "red" to 0xFFE5484D, "pink" to 0xFFE93D82, "green" to 0xFF30A46C, "blue" to 0xFF3B82F6, "purple" to 0xFF8E4EC6, "gray" to 0xFF8B8D98)

@Composable
private fun BlockBody(
    content: String,
    statuses: Statuses,
    api: Api?,
    onTap: (Target) -> Unit,
    onStatusTap: (() -> Unit)?,
    onRemoveImage: ((src: String) -> Unit)?,
    align: TextAlign,
    autoHeading: Int,
) {
    val modifier = Modifier
    // Managed property lines (background-color::, id::, …) are never shown, as in the web app.
    val visible = shownText(content)
    val st = statuses.of(visible)
    var body = if (st != null) statuses.strip(visible) else visible
    if (st != null) body = TASK_TAG.replaceFirst(body, "")
    val colors = inlineColors()
    val done = st?.done == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val list = parts(body)
        list.forEachIndexed { index, part ->
            when (part) {
                is Part.Text -> Row {
                    if (index == 0 && st != null) StatusBadge(st, onStatusTap)
                    val level = if (part.heading == 0 && index == 0) autoHeading else part.heading
                    val style = when (level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        0 -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        inlineMarkdown(part.text, colors, onTap),
                        style = style.copy(textDecoration = if (done) TextDecoration.LineThrough else null, textAlign = align),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = (if (part.quote) Modifier.padding(start = 8.dp) else Modifier).let { if (align != TextAlign.Start) it.fillMaxWidth() else it },
                    )
                }
                is Part.Rule -> androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                is Part.Code -> CodeBlock(part.lang, part.code)
                is Part.Maths -> Text(
                    part.tex,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                is Part.Table -> TableView(part.rows, colors, onTap)
                is Part.Admonition -> Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                ) {
                    Text(inlineMarkdown(part.text, colors, onTap), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                is Part.Image -> AssetImage(part.src, part.alt, api, onRemoveImage?.let { remove -> { remove(part.src) } })
            }
        }
        if (list.isEmpty() && st != null) Row { StatusBadge(st, onStatusTap) }
    }
}

@Composable
private fun TableView(rows: List<List<String>>, colors: InlineColors, onTap: (Target) -> Unit) {
    if (rows.isEmpty()) return
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
    ) {
        rows.forEachIndexed { r, cells ->
            Row(if (r == 0) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier) {
                cells.forEach { cell ->
                    Text(
                        inlineMarkdown(cell, colors, onTap),
                        fontWeight = if (r == 0) FontWeight.SemiBold else null,
                        modifier = Modifier.width(120.dp).padding(8.dp),
                    )
                }
            }
        }
    }
}

private val imageCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

fun imageCacheRemove(src: String) { imageCache.remove(src) }

/**
 * An uploaded picture (../assets/…) as a thumbnail: at most 220 dp tall,
 * whatever size the block asks for. Tap to open it full screen.
 */
@Composable
private fun AssetImage(src: String, alt: String, api: Api?, onRemove: (() -> Unit)?) {
    if (!src.contains("assets/") || api == null) {
        Text("🖼 $alt", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var image by remember(src) { mutableStateOf(imageCache.get(src)) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(src) {
        if (image != null) return@LaunchedEffect
        val bytes = runCatching { api.asset(src) }.getOrNull() ?: return@LaunchedEffect
        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decodeScaled(bytes, 800) } ?: return@LaunchedEffect
        image = bmp.also { imageCache.put(src, it) }
    }
    val img = image
    if (img == null) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { open = true },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { Text("🖼 ${alt.ifEmpty { "picture" }}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        androidx.compose.foundation.Image(
            img,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            alignment = androidx.compose.ui.Alignment.CenterStart,
            modifier = Modifier
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .testTag("picture"),
        )
    }
    if (open) ImageViewer(src, alt, api, ImageActions(removeFromBlock = onRemove)) { open = false }
}

/**
 * A code block with syntax colours. A long one starts folded (Settings →
 * Code blocks, the web app's defaults): its first lines and a bar to show all.
 */
@Composable
private fun CodeBlock(lang: String, code: String) {
    val fold = app.blockseq.android.ui.LocalCodeFold.current
    val lines = code.split('\n')
    val long = fold.on && lines.size > fold.lines
    var open by remember(code) { mutableStateOf(false) }
    val shown = if (long && !open) lines.take(fold.show.coerceIn(1, lines.size - 1)).joinToString("\n") else code
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val colors = remember(dark) { codeColors(dark) }
    val text = remember(shown, lang, dark) { highlight(shown, lang, colors) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxWidth()) {
            // Long lines wrap: no sideways scrolling on a phone.
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                softWrap = true,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 40.dp),
            )
            androidx.compose.material3.IconButton(
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                    android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).testTag("code-copy"),
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ContentCopy, "Copy code", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (long) {
            Text(
                if (open) "Fold" else "Show all ${lines.size} lines",
                color = inlineColors().link,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp).testTag("code-fold"),
            )
        }
    }
}

private val ALWAYS_HIDDEN = Regex("""^(id|collapsed|logseq\.order-list-type|background-color|heading|encrypted|text-align):: .*$""")

/** Block text as shown: without the property lines the web app hides (never the first line). */
fun shownText(content: String): String {
    val lines = content.split('\n')
    return (listOf(lines.first()) + lines.drop(1).filterNot { ALWAYS_HIDDEN.matches(it) }).joinToString("\n")
}
