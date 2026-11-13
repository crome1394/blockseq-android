package app.blockseq.android.render

import android.util.LruCache
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
) {
    val st = statuses.of(content)
    var body = if (st != null) statuses.strip(content) else content
    if (st != null) body = TASK_TAG.replaceFirst(body, "")
    val colors = inlineColors()
    val done = st?.done == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val list = parts(body)
        list.forEachIndexed { index, part ->
            when (part) {
                is Part.Text -> Row {
                    if (index == 0 && st != null) StatusBadge(st, onStatusTap)
                    val style = when (part.heading) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        0 -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        inlineMarkdown(part.text, colors, onTap),
                        style = style.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = if (part.quote) Modifier.border(width = 0.dp, color = Color.Transparent).padding(start = 8.dp) else Modifier,
                    )
                }
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
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
        )
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
