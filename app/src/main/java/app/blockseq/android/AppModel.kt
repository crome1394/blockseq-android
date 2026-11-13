package app.blockseq.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.blockseq.android.crypto.BlockCrypto
import app.blockseq.android.crypto.EncryptionSetup
import app.blockseq.android.data.Api
import app.blockseq.android.data.Store
import app.blockseq.android.data.Unpaired
import app.blockseq.android.outline.Statuses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide state: the paired server, the API client for the chosen workbook,
 * task statuses, and the encryption key while unlocked (memory only).
 */
class AppModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    var api by mutableStateOf<Api?>(null)
        private set
    var statuses by mutableStateOf(Statuses.DEFAULT)
        private set
    var setup by mutableStateOf<EncryptionSetup?>(null)
        private set

    /** The content key while unlocked. Never stored; dropped on lock. */
    var key by mutableStateOf<ByteArray?>(null)
        private set

    /** Bumped when every open view should reload (workbook switch, unlock). */
    var generation by mutableStateOf(0)
        private set

    var unpairedMessage by mutableStateOf<String?>(null)

    /** The live-update stream to the server is open (shown in the left pane). */
    var connected by mutableStateOf(false)

    /** When something last loaded from or saved to the server. */
    var lastSync by mutableStateOf<Long?>(null)
        private set

    fun markSynced() {
        lastSync = System.currentTimeMillis()
    }

    /** Settings → Controls at the bottom (kept in Store; here so screens redraw). */
    var controlsAtBottom by mutableStateOf(store.controlsAtBottom)
        private set

    var codeFold by mutableStateOf(store.codeFold)
        private set

    fun changeCodeFold(f: app.blockseq.android.data.CodeFold) {
        store.codeFold = f
        codeFold = f
    }

    fun changeControlsAtBottom(on: Boolean) {
        store.controlsAtBottom = on
        controlsAtBottom = on
    }

    init {
        if (store.paired) connect()
    }

    fun connect() {
        api = Api(store.server, store.token, store.workbook)
        generation++
        refreshSettings()
    }

    fun refreshSettings() {
        val a = api ?: return
        viewModelScope.launch {
            runCatching { statuses = Statuses(a.statuses().ifEmpty { Statuses.DEFAULT.list }) }.onFailure(::handle)
            runCatching { setup = a.encryption() }
        }
    }

    /** Pairs this phone with a server. Throws with a readable message on failure. */
    suspend fun pair(server: String, password: String, name: String) {
        val base = normalize(server)
        val token = Api.pair(base, password, name)
        store.server = base
        store.deviceName = name
        store.workbook = "main"
        store.token = token
        unpairedMessage = null
        connect()
        // A Scratchpad widget on the home screen shows this server's page from now on.
        app.blockseq.android.scratch.ScratchpadSync.request(getApplication())
    }

    fun switchWorkbook(id: String) {
        store.workbook = id
        connect()
    }

    fun unpair(message: String? = null) {
        store.forget()
        api = null
        key = null
        unpairedMessage = message
    }

    /** The server revoked this phone: back to pairing, with the reason. */
    fun handle(e: Throwable) {
        if (e is Unpaired) unpair(e.message)
    }

    /** Unlocks #Encrypted blocks. Slow (PBKDF2), so it runs off the main thread. */
    suspend fun unlock(password: String) {
        val s = setup ?: api?.encryption().also { setup = it } ?: error("No encryption password is set on the server yet.")
        val started = System.nanoTime()
        key = withContext(Dispatchers.Default) { BlockCrypto.unlock(s, password) }
        android.util.Log.i("Blockseq", "unlocked in ${(System.nanoTime() - started) / 1_000_000} ms")
        generation++
    }

    fun lock() {
        key?.fill(0)
        key = null
        generation++
    }

    companion object {
        /** "notes.example.com" → "https://notes.example.com". */
        fun normalize(server: String): String {
            val s = server.trim().trimEnd('/')
            return if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        }
    }
}
