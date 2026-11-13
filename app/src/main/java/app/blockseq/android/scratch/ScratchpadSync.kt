package app.blockseq.android.scratch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.blockseq.android.data.Api
import app.blockseq.android.data.SaveResult
import app.blockseq.android.data.Store
import app.blockseq.android.data.Unpaired
import app.blockseq.android.outline.Block
import app.blockseq.android.render.Part
import app.blockseq.android.render.parts
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Brings the phone's Scratchpad and the server's in step: queued notes are
 * added as new rows at the end of the page (with the version check; on a
 * conflict the page is read again and the notes added again, so nothing on
 * the server is overwritten), then the page is cached for the widget.
 */
object ScratchpadSync {
    private const val MAX_TRIES = 4

    suspend fun sync(context: Context): Boolean {
        val store = Store(context)
        if (!store.paired) {
            ScratchpadStore.update(context) { it.copy(error = "Open Blockseq and pair this phone first.") }
            ScratchpadWidget.refresh(context)
            return false
        }
        val api = Api(store.server, store.token, "main")
        val ok = try {
            var done = false
            repeat(MAX_TRIES) {
                if (done) return@repeat
                val state = ScratchpadStore.load(context)
                val page = api.page(SCRATCHPAD)
                var blocks = page.blocks
                if (state.pending.isEmpty()) {
                    cache(context, api, blocks, page.page?.version ?: 0, sent = emptySet())
                    done = true
                    return@repeat
                }
                val have = blocks.map { it.id }.toHashSet()
                blocks = blocks + state.pending.filter { it.id !in have }.map { Block(it.id, null, it.content) }
                when (val r = api.savePage(SCRATCHPAD, page.page?.version ?: 0, blocks)) {
                    is SaveResult.Saved -> {
                        cache(context, api, blocks, r.page.version, sent = state.pending.map { it.id }.toSet())
                        done = true
                    }
                    is SaveResult.ForeignIds -> {
                        val bad = r.ids.toSet()
                        ScratchpadStore.update(context) { s -> s.copy(pending = s.pending.map { if (it.id in bad) it.copy(id = java.util.UUID.randomUUID().toString()) else it }) }
                    }
                    // Changed or deleted meanwhile: read it again and add the notes again.
                    else -> {}
                }
            }
            if (!done) error("The Scratchpad kept changing on the server; try again.")
            true
        } catch (e: Unpaired) {
            ScratchpadStore.update(context) { it.copy(error = "This phone is no longer paired. Open Blockseq to pair it again.") }
            false
        } catch (e: Exception) {
            ScratchpadStore.update(context) { it.copy(error = "Offline: ${e.message ?: "could not reach the server"}") }
            false
        }
        ScratchpadWidget.refresh(context)
        return ok
    }

    private suspend fun cache(context: Context, api: Api, blocks: List<Block>, version: Long, sent: Set<String>) {
        val depth = HashMap<String, Int>()
        val rows = blocks.map { b ->
            val d = b.parentId?.let { (depth[it] ?: 0) + 1 } ?: 0
            depth[b.id] = d
            ScratchRow(b.id, b.content, d)
        }
        thumbnails(context, api, rows)
        ScratchpadStore.update(context) { s ->
            s.copy(rows = rows, version = version, lastSync = System.currentTimeMillis(), pending = s.pending.filter { it.id !in sent }, error = null)
        }
    }

    /** Small copies of the pictures on the page, for the widget (at most 12, 480 px wide). */
    private suspend fun thumbnails(context: Context, api: Api, rows: List<ScratchRow>) {
        val dir = ScratchpadStore.imageDir(context)
        val wanted = rows.flatMap { r -> parts(r.content).filterIsInstance<Part.Image>().map { it.src } }.filter { it.contains("assets/") }.distinct().takeLast(12)
        val names = wanted.map { thumbName(it) }.toSet()
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
        for (src in wanted) {
            val f = File(dir, thumbName(src))
            if (f.exists()) continue
            val bytes = runCatching { api.asset(src) }.getOrNull() ?: continue
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 960) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: continue
            val scaled = if (bmp.width > 480) Bitmap.createScaledBitmap(bmp, 480, bmp.height * 480 / bmp.width, true) else bmp
            f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        }
    }

    fun thumbName(src: String) = src.substringAfterLast('/').substringBeforeLast('.') + ".jpg"

    /** Sync soon, as soon as there is a network (queued notes go out when the phone is back online). */
    fun request(context: Context) {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("scratchpad-sync", ExistingWorkPolicy.REPLACE, work)
    }

    /** Refresh the widget every 30 minutes while it is on the home screen. */
    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("scratchpad-refresh", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun unschedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("scratchpad-refresh")
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (ScratchpadSync.sync(applicationContext) || ScratchpadStore.load(applicationContext).pending.isEmpty()) Result.success() else Result.retry()
}
