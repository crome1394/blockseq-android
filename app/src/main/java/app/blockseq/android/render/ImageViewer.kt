package app.blockseq.android.render

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.blockseq.android.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the viewer may offer for this picture, besides sharing and saving. */
class ImageActions(
    /** Takes the picture out of the block it is in (the file stays on the server). */
    val removeFromBlock: (() -> Unit)? = null,
)

/**
 * A picture full screen: pinch to zoom, double-tap to reset; share, save to
 * the phone, remove from the block, or delete the file from the server.
 */
@Composable
fun ImageViewer(src: String, alt: String, api: Api, actions: ImageActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = src.substringAfterLast('/')
    // In landscape the bar hides after a few seconds so the picture fills the screen; a tap brings it back.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var barShown by remember { mutableStateOf(true) }
    var barTouched by remember { mutableStateOf(0) }
    LaunchedEffect(landscape, barShown, barTouched, menu) {
        if (!landscape) { barShown = true; return@LaunchedEffect }
        if (barShown && !menu) { kotlinx.coroutines.delay(3000); barShown = false }
    }
    LaunchedEffect(src) {
        val b = runCatching { api.asset(src) }.getOrNull()
        if (b == null) { failed = true; return@LaunchedEffect }
        bytes = b
        image = withContext(Dispatchers.Default) { decodeScaled(b, 4096) }
        if (image == null) failed = true
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("image-viewer")) {
            val img = image
            when {
                img != null -> Image(
                    img, alt, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 8f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = { barShown = !barShown || !landscape; barTouched++ }, onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
                failed -> Text("Could not load this picture.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            val bottom = app.blockseq.android.ui.LocalControlsAtBottom.current
            androidx.compose.animation.AnimatedVisibility(
                barShown, Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter),
                enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
            ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0x99000000)).safeDrawingPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Text(name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(enabled = bytes != null, onClick = { bytes?.let { share(context, it, name) } }, modifier = Modifier.testTag("image-share")) {
                    Icon(Icons.Filled.Share, "Share", tint = Color.White)
                }
                IconButton(enabled = bytes != null, onClick = {
                    val b = bytes ?: return@IconButton
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { saveToPhotos(context, b, name) }.isSuccess }
                        Toast.makeText(context, if (ok) "Saved to Pictures/Blockseq" else "Could not save it", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.testTag("image-save")) { Icon(Icons.Filled.Download, "Save to phone", tint = Color.White) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("image-more")) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        actions.removeFromBlock?.let { remove ->
                            DropdownMenuItem(text = { Text("Remove from block") }, onClick = { menu = false; remove(); onClose() })
                        }
                        DropdownMenuItem(
                            text = { Text("Delete from server…") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this picture?") },
                text = { Text("The file $name is deleted from the server. Any block that shows it loses the picture. This cannot be undone from the app.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch {
                            val ok = runCatching { api.deleteAsset(name) }.isSuccess
                            if (ok) { imageCacheRemove(src); actions.removeFromBlock?.invoke(); onClose() }
                            Toast.makeText(context, if (ok) "Deleted $name" else "Could not delete it", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

/** Decodes an image no wider or taller than [max] pixels (memory stays bounded). */
fun decodeScaled(bytes: ByteArray, max: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
    "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "heic" -> "image/heic"; else -> "image/jpeg"
}

private fun share(context: Context, bytes: ByteArray, name: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // only the picture being shared stays
    val file = File(dir, name).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mimeOf(name)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share picture"))
}

/** Saves into Pictures/Blockseq through MediaStore (no storage permission needed). */
private fun saveToPhotos(context: Context, bytes: ByteArray, name: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, mimeOf(name))
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Blockseq")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no media store")
    resolver.openOutputStream(uri)!!.use { it.write(bytes) }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
}
