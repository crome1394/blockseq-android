package app.blockseq.android.net

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blockseq.android.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connects Tailscale while the app is open and the server is out of reach.
 *
 * [ensure] runs when the app comes to the front and before each live-stream
 * (re)connect. It connects Tailscale when the setting is on, Tailscale is
 * installed, the VPN is down, and the server can't be reached directly: on
 * mobile data always; on Wi-Fi or anything else when [ServerProbe] fails, so
 * home Wi-Fi stays direct and any other Wi-Fi uses Tailscale.
 *
 * The app keeps a live stream open, so the connection stays up while the app
 * is on screen. [onBackground] sets a system alarm that disconnects it
 * [LINGER_MS] later (the alarm sends DISCONNECT_VPN straight to Tailscale, so
 * it fires even if Android has frozen or killed this process); coming back
 * first cancels it. A connection the user made is never touched.
 */
class AutoTailscale(context: Context, private val store: Store) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()
    private var ours = false

    /** True while waiting for Tailscale to come up (shown in the status line). */
    var connecting by mutableStateOf(false)
        private set

    /** Bumped each time this class connects Tailscale. */
    var connects = 0
        private set

    /** Connects Tailscale if needed (see the class doc). True when it is now connected because of this call. */
    suspend fun ensure(): Boolean = lock.withLock {
        if (Tailscale.isVpnActive(appContext)) return false
        // Our connection went away (the alarm, or the user turned it off): no longer ours.
        ours = false
        if (!store.autoTailscale || store.server.isBlank()) return false
        if (!Tailscale.isInstalled(appContext)) return false
        val network = Tailscale.activeNetwork(appContext) ?: return false
        if (!Tailscale.isOnMobileData(appContext) && ServerProbe.reachable(network, store.server)) return false
        if (!Tailscale.setConnected(appContext, connect = true)) return false
        ours = true
        connects++
        connecting = true
        try {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { Tailscale.vpnActiveFlow(appContext).first { it } }
            // Give MagicDNS and routes a moment after the interface appears.
            delay(SETTLE_MS)
        } finally {
            connecting = false
        }
        Tailscale.isVpnActive(appContext)
    }

    fun onForeground() {
        alarms?.cancel(Tailscale.disconnectPendingIntent(appContext))
    }

    fun onBackground() {
        if (!ours) return
        // Allowed while idle so Doze doesn't hold it back; inexact, so no exact-alarm permission.
        alarms?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + LINGER_MS,
            Tailscale.disconnectPendingIntent(appContext),
        )
    }

    /** Setting turned off: drop a connection this class made. */
    fun disconnectOurs() {
        onForeground()
        if (ours) Tailscale.setConnected(appContext, connect = false)
        ours = false
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SETTLE_MS = 1_000L

        /** Time in the background before an auto-connected Tailscale is disconnected. */
        const val LINGER_MS = 30_000L
    }
}
