package app.blockseq.android.data

import app.blockseq.android.crypto.EncryptionSetup
import app.blockseq.android.outline.Block
import app.blockseq.android.outline.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class PageMeta(val id: String, val name: String, @SerialName("journal_day") val journalDay: Long? = null, val version: Long, @SerialName("updated_at") val updatedAt: Long = 0, val favorite: Boolean = false)

@Serializable
data class BlockHit(val id: String, val content: String, @SerialName("page_name") val pageName: String)

@Serializable
data class Backlinks(@SerialName("page_name") val pageName: String, val blocks: List<BlockHit>)

@Serializable
data class PageData(val name: String, val page: PageMeta? = null, val blocks: List<Block> = emptyList(), val backlinks: List<Backlinks> = emptyList())

@Serializable
data class JournalPage(val page: PageMeta, val blocks: List<Block>)

@Serializable
data class PageSummary(val name: String, @SerialName("journal_day") val journalDay: Long? = null, @SerialName("updated_at") val updatedAt: Long = 0, @SerialName("block_count") val blockCount: Long = 0, val favorite: Boolean = false, val tags: List<String> = emptyList(), val words: Long = 0)

@Serializable
data class SearchResults(val pages: List<String> = emptyList(), val blocks: List<BlockHit> = emptyList())

@Serializable
data class TaskHit(val id: String, val content: String, @SerialName("page_name") val pageName: String, @SerialName("journal_day") val journalDay: Long? = null)

@Serializable
data class Workbook(val id: String, val name: String)

@Serializable
private data class ConfigBody(val statuses: List<Status> = emptyList())

@Serializable
private data class EncryptionBody(val encryption: EncryptionSetup? = null)

@Serializable
private data class PairResult(val token: String? = null, val id: String? = null)

@Serializable
private data class ErrorBody(val error: String? = null, val message: String? = null)

@Serializable
private data class SaveBody(@SerialName("base_version") val baseVersion: Long, val blocks: List<Block>, val overwrite: Boolean? = null)

/** The result of saving a page. */
sealed interface SaveResult {
    data class Saved(val page: PageMeta) : SaveResult
    /** Changed on another device: their version, to compare. */
    data class Conflict(val page: PageMeta, val blocks: List<Block>) : SaveResult
    /** Deleted or renamed on another device. */
    data object Deleted : SaveResult
    /** Pasted blocks whose ids belong to another page. */
    data class ForeignIds(val ids: List<String>) : SaveResult
}

class ApiException(val status: Int, val code: String?, message: String) : Exception(message)

/** The token was revoked or the password changed: pair again. */
class Unpaired : Exception("This phone is no longer paired with the server (it was revoked, or the server's password changed). Pair it again.")

/**
 * The Blockseq HTTP API (see ARCHITECTURE.md in the server repo). Every call
 * runs on the IO dispatcher and sends this phone's device token.
 */
class Api(private val base: String, private val token: String?, val workbook: String) {
    /** Identifies this app's own saves in the live-update stream, like a browser tab. */
    val clientId: String = UUID.randomUUID().toString()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun url(path: String, vararg query: Pair<String, String?>, global: Boolean = false): HttpUrl {
        val b = (base.trimEnd('/') + path).toHttpUrl().newBuilder()
        for ((k, v) in query) if (v != null) b.addQueryParameter(k, v)
        if (!global) b.addQueryParameter("wb", workbook)
        return b.build()
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("X-Client-Id", clientId).apply {
        token?.let { header("Authorization", "Bearer $it") }
    }

    private suspend fun call(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r -> r.code to (r.body?.string() ?: "") }
    }

    private fun fail(code: Int, body: String): Nothing {
        val err = runCatching { json.decodeFromString<ErrorBody>(body) }.getOrNull()
        if (code == 401 && err?.error == "device") throw Unpaired()
        throw ApiException(code, err?.error, err?.message ?: describe(code, err?.error))
    }

    private suspend inline fun <reified T> get(url: HttpUrl): T {
        val (code, body) = call(request(url).get().build())
        if (code !in 200..299) fail(code, body)
        return json.decodeFromString(body)
    }

    suspend fun page(name: String): PageData = get(url("/api/page", "name" to name))
    suspend fun journals(before: Long? = null, limit: Int = 10): List<JournalPage> =
        get(url("/api/journals", "before" to before?.toString(), "limit" to limit.toString()))
    suspend fun pages(): List<PageSummary> = get(url("/api/pages"))
    /** The page a block lives on (for ((block references))). */
    suspend fun blockPage(id: String): String = get<BlockHit>(url("/api/block", "id" to id)).pageName
    suspend fun search(q: String): SearchResults = get(url("/api/search", "q" to q, "limit" to "50"))
    suspend fun tasks(): List<TaskHit> = get(url("/api/tasks"))
    suspend fun statuses(): List<Status> = get<ConfigBody>(url("/api/config", global = true)).statuses
    suspend fun encryption(): EncryptionSetup? = get<EncryptionBody>(url("/api/encryption", global = true)).encryption
    suspend fun workbooks(): List<Workbook> = get(url("/api/workbooks", global = true))

    suspend fun savePage(name: String, baseVersion: Long, blocks: List<Block>, overwrite: Boolean = false): SaveResult {
        val body = json.encodeToString(SaveBody(baseVersion, blocks, if (overwrite) true else null))
        val req = request(url("/api/page", "name" to name)).put(body.toRequestBody(JSON)).build()
        val (code, text) = call(req)
        if (code == 200) return SaveResult.Saved(json.decodeFromJsonElement(PageMeta.serializer(), json.parseToJsonElement(text).let { (it as JsonObject)["page"]!! }))
        if (code == 409) {
            val o = json.parseToJsonElement(text) as JsonObject
            o["foreign_ids"]?.let { return SaveResult.ForeignIds(json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), it)) }
            if (o["deleted"]?.jsonPrimitive?.content == "true") return SaveResult.Deleted
            val page = o["page"]
            if (page != null && page !is kotlinx.serialization.json.JsonNull) {
                return SaveResult.Conflict(json.decodeFromJsonElement(PageMeta.serializer(), page), json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(Block.serializer()), o["blocks"]!!))
            }
        }
        fail(code, text)
    }

    /**
     * Changes one block's text (a status change from the task list). Refused
     * with a conflict when the block no longer reads [expected].
     */
    suspend fun putBlock(id: String, content: String, expected: String) {
        val body = json.encodeToString(mapOf("content" to content, "expected" to expected))
        val (code, text) = call(request(url("/api/block", "id" to id)).put(body.toRequestBody(JSON)).build())
        if (code !in 200..299) fail(code, text)
    }

    /** Server-sent events: one JSON object per change anywhere on the server. */
    fun events(): Flow<JsonObject> = callbackFlow {
        val sse = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
        val source = EventSources.createFactory(sse).newEventSource(
            request(url("/api/events", global = true)).header("Accept", "text/event-stream").build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    runCatching { json.parseToJsonElement(data) as JsonObject }.getOrNull()?.let { trySend(it) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(t ?: ApiException(response?.code ?: 0, null, "live updates stopped"))
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            },
        )
        awaitClose { source.cancel() }
    }

    /**
     * Uploads a file (a photo) to the workbook's assets. Returns the path to
     * put in the block text, e.g. `../assets/photo_1731_0.jpg`.
     */
    suspend fun uploadAsset(bytes: ByteArray, fileName: String, mime: String): String {
        val req = request(url("/api/assets"))
            .header("X-Filename", fileName)
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val (code, text) = call(req)
        if (code !in 200..299) fail(code, text)
        return (json.parseToJsonElement(text) as JsonObject)["path"]!!.jsonPrimitive.content
    }

    /** Deletes an uploaded file from the server (every block that shows it loses the picture). */
    suspend fun deleteAsset(name: String) {
        val (code, text) = call(request(url("/api/assets", "name" to name)).delete().build())
        if (code !in 200..299 && code != 404) fail(code, text)
    }

    /** An uploaded file (image) with this phone's token, as bytes. */
    suspend fun asset(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val name = path.substringAfterLast("/")
        val req = request(url("/assets/$name")).get().build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val JSON = "application/json".toMediaType()

        /** Pairs this phone: returns its token (null when the server has no password). */
        suspend fun pair(base: String, password: String, deviceName: String): String? {
            val api = Api(base, null, "main")
            val body = json.encodeToString(mapOf("password" to password, "name" to deviceName))
            val req = Request.Builder().url(api.url("/api/devices/pair", global = true)).post(body.toRequestBody(JSON)).build()
            val (code, text) = api.call(req)
            if (code !in 200..299) {
                val err = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
                throw ApiException(code, err?.error, err?.message ?: describe(code, err?.error))
            }
            return json.decodeFromString<PairResult>(text).token
        }

        fun describe(code: Int, error: String?): String = when {
            error == "conflict" -> "It was changed somewhere else."
            error == "storage" || code == 503 -> "The server could not store that just now."
            error == "not_found" || code == 404 -> "That could not be found."
            error == "no_space" -> "The server does not have enough free space."
            code == 401 -> "The server needs a password."
            code == 403 -> "The server refused that."
            else -> "The server had a problem ($code)."
        }
    }
}
