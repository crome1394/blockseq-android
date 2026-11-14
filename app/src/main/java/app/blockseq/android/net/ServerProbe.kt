package app.blockseq.android.net

import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Can the Blockseq server be reached directly (without Tailscale)?
 *
 * Asks `/api/health` (no login needed) over [Network]. A 200 or 503 means
 * Blockseq itself answered. Anything else (no answer, a proxy refusing an
 * outside address, a captive-portal redirect) means it is out of reach.
 */
object ServerProbe {
    private const val TIMEOUT_MS = 2_500L
    private const val TOTAL_TIMEOUT_MS = 3_500L

    // DNS lookups can't be interrupted; run them here so a slow one is
    // abandoned at the timeout instead of holding up the caller.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun reachable(network: Network, server: String): Boolean {
        val url = (server.trimEnd('/') + "/api/health").toHttpUrlOrNull() ?: return false
        val attempt = scope.async {
            runCatching {
                val http = OkHttpClient.Builder()
                    .socketFactory(network.socketFactory)
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
                    })
                    .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                http.newCall(Request.Builder().url(url).get().build()).execute().use { it.code == 200 || it.code == 503 }
            }.getOrDefault(false)
        }
        return withTimeoutOrNull(TOTAL_TIMEOUT_MS) { attempt.await() } ?: run {
            attempt.cancel()
            false
        }
    }
}
