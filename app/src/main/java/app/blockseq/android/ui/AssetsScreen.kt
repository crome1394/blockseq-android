package app.blockseq.android.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.blockseq.android.AppModel
import app.blockseq.android.data.Api
import app.blockseq.android.data.AssetInfo
import app.blockseq.android.render.ImageActions
import app.blockseq.android.render.LocalImageViewer
import app.blockseq.android.render.ViewerRequest
import app.blockseq.android.render.decodeScaled
import app.blockseq.android.render.imageCacheRemove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The workbook's uploaded files, as on the web app's Assets page: filter,
 * only unused, the total, and per file a thumbnail, its path (copy), the
 * pages that use it, size, date, and Open / Download / Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(model: AppModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val api = model.api ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewer = LocalImageViewer.current
    var list by remember { mutableStateOf<List<AssetInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var unusedOnly by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AssetInfo?>(null) }
    LaunchedEffect(api) {
        runCatching { api.assets() }.onSuccess { list = it }.onFailure { error = it.message; model.handle(it) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Assets") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val all = list
            if (all == null) {
                Text(error ?: "Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            OutlinedTextField(
                filter, { filter = it }, placeholder = { Text("Filter files") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("assets-filter"),
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = unusedOnly, onClick = { unusedOnly = !unusedOnly }, label = { Text("Only unused") }, modifier = Modifier.testTag("assets-unused"))
                Spacer(Modifier.width(12.dp))
                Text("${all.size} file${if (all.size == 1) "" else "s"} · ${formatSize(all.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val q = filter.trim().lowercase()
            val rows = all.filter { it.name.lowercase().contains(q) && (!unusedOnly || it.pages.isEmpty()) }
            if (rows.isEmpty()) Text(
                if (all.isEmpty()) "No files yet. Add pictures to a block (the page's ⋯ menu)." else "No matching files.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.fillMaxSize().testTag("assets-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows, key = { it.name }) { a ->
                    AssetRow(
                        a, api, onPage = onOpen,
                        onOpenFile = {
                            if (a.kind == "image" && viewer != null) viewer.current = ViewerRequest("../${a.path}", a.name, api, ImageActions())
                            else scope.launch { openWithApp(context, api, a) }
                        },
                        onDownload = { scope.launch { download(context, api, a) } },
                        onDelete = { deleting = a },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${a.name}?") },
            text = {
                Text("It is removed from the server. This cannot be undone." + if (a.pages.isNotEmpty()) "\n\nIt is used on: ${a.pages.joinToString(", ")}. Those blocks will show a broken link." else "")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { api.deleteAsset(a.name) }
                            .onSuccess {
                                imageCacheRemove("../${a.path}")
                                list = list?.minus(a)
                                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AssetRow(a: AssetInfo, api: Api, onPage: (String) -> Unit, onOpenFile: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("asset-row")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpenFile), contentAlignment = Alignment.Center) {
                if (a.kind == "image") Thumb(a, api)
                else Text(a.name.substringAfterLast('.', "file").uppercase().take(4), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatSize(a.size)} · ${formatAdded(a.modified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The path, as the web shows it (the full server path when the server exposes it).
                val shown = a.absolute ?: a.path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shown, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = { copyText(context, shown) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, "Copy the path", modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (a.pages.isEmpty()) Text("not used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else a.pages.forEach { p -> AssistChip(onClick = { onPage(p) }, label = { Text(p) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpenFile, modifier = Modifier.testTag("asset-open")) { Text("Open") }
            TextButton(onClick = onDownload, modifier = Modifier.testTag("asset-download")) { Text("Download") }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("asset-delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Thumb(a: AssetInfo, api: Api) {
    var img by remember(a.name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(a.name) {
        val bytes = runCatching { api.asset(a.path) }.getOrNull() ?: return@LaunchedEffect
        img = withContext(Dispatchers.Default) { decodeScaled(bytes, 256) }
    }
    img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

internal fun formatSize(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / 1024.0 / 1024)
    else -> "%.2f GB".format(n / 1024.0 / 1024 / 1024)
}

private fun formatAdded(ms: Long): String =
    if (ms <= 0) "" else DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private fun mimeFor(name: String): String =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

/** Opens a file that is not a picture in an app that can show it (PDF viewer, player…). */
private suspend fun openWithApp(context: Context, api: Api, a: AssetInfo) {
    val bytes = api.asset(a.path) ?: run { Toast.makeText(context, "Could not fetch the file", Toast.LENGTH_SHORT).show(); return }
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, a.name).apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeFor(a.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(Intent.createChooser(view, "Open ${a.name}")) }
    catch (e: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app on this phone opens this file", Toast.LENGTH_LONG).show() }
}

/** Saves to the phone: pictures into Pictures/Blockseq, other files into Download/Blockseq (no storage permission). */
private suspend fun download(context: Context, api: Api, a: AssetInfo) {
    val bytes = api.asset(a.path) ?: run { Toast.makeText(context, "Could not fetch the file", Toast.LENGTH_SHORT).show(); return }
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            val image = a.kind == "image"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, a.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(a.name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (image) "Pictures/Blockseq" else "Download/Blockseq")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val r = context.contentResolver
            val target = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = r.insert(target, values) ?: error("no media store")
            r.openOutputStream(uri)!!.use { it.write(bytes) }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            r.update(uri, values, null, null)
        }.isSuccess
    }
    Toast.makeText(context, if (!ok) "Could not save it" else if (a.kind == "image") "Saved to Pictures/Blockseq" else "Saved to Download/Blockseq", Toast.LENGTH_SHORT).show()
}
