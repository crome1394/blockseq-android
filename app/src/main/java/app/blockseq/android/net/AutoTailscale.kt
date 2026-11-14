package app.blockseq.android.net

import android.app.AlarmManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
 * Connects Tailscale while the app is open and the server is out of reach,
 * and drops it again when it is no longer needed.
 *
 * [ensure] runs when the app comes to the front and before each live-stream
 * (re)connect. It connects Tailscale when the setting is on, Tailscale is
 * installed, the VPN is down, and the server can't be reached directly: on
 * mobile data always; on Wi-Fi or anything else when [ServerProbe] fails, so
 * home Wi-Fi stays direct and any other Wi-Fi uses Tailscale.
 *
 * A connection this class made is dropped:
 *
 * - when the phone joins a Wi-Fi network while the app is open
 *   ([networkJoined]). The caller then runs [ensure] again, which leaves
 *   Tailscale off at home and reconnects it on any other Wi-Fi. (With the VPN
 *   up there is no reliable way to ask "is the server reachable without
 *   it?", so it is dropped first and asked then.)
 * - [LINGER_MS] after the app leaves the screen ([onBackground]), by a system
 *   alarm that sends DISCONNECT_VPN straight to Tailscale, so it fires even
 *   if Android has frozen or killed this process. Coming back first cancels it.
 *
 * A connection the user made (in Tailscale, or with the key in the left pane,
 * [toggle]) is never touched.
 */
class AutoTailscale(context: Context, private val store: Store) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    /** The connection now up is ours. Kept in [Store], so it survives the app being killed. */
    private var ours: Boolean
        get() = store.tailscaleOurs
        set(v) { if (store.tailscaleOurs != v) store.tailscaleOurs = v }

    /** The network we were on when we last connected: joining it again is not news. */
    private var decidedOn: Network? = null

    /** The user turned Tailscale off with the key: don't turn it straight back on. */
    private var heldOff = false

    private var wifiWatch: ConnectivityManager.NetworkCallback? = null

    /** True while waiting for Tailscale to come up (shown in the status line). */
    var connecting by mutableStateOf(false)
        private set

    /** Bumped each time this class connects Tailscale. */
    var connects = 0
        private set

    /**
     * Connects Tailscale if needed (see the class doc); [on] is the network to
     * judge by (default: the phone's active one). True when it is now
     * connected because of this call.
     */
    suspend fun ensure(on: Network? = null): Boolean = lock.withLock {
        if (Tailscale.isVpnActive(appContext)) return false
        // Our connection went away (the alarm, or the user turned it off): no longer ours.
        ours = false
        if (heldOff || !store.autoTailscale || store.server.isBlank()) return false
        if (!Tailscale.isInstalled(appContext)) return false
        val network = on ?: Tailscale.activeNetwork(appContext) ?: return false
        if (!Tailscale.isMobileData(appContext, network) && ServerProbe.reachable(network, store.server)) return false
        if (!Tailscale.setConnected(appContext, connect = true)) return false
        ours = true
        decidedOn = network
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

    /**
     * The phone joined a Wi-Fi network. Drops our connection so the caller's
     * next [ensure] can ask whether this network reaches the server directly
     * (home). True when it dropped it.
     */
    suspend fun networkJoined(network: Network): Boolean = lock.withLock {
        heldOff = false
        if (!ours || network == decidedOn || !Tailscale.isVpnActive(appContext)) return false
        Tailscale.setConnected(appContext, connect = false)
        ours = false
        withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { Tailscale.vpnActiveFlow(appContext).first { !it } }
        true
    }

    /** The key in the left pane: connect or disconnect by hand. Either way the result is the user's, not ours. */
    fun toggle() {
        val on = Tailscale.isVpnActive(appContext)
        heldOff = on
        ours = false
        alarms?.cancel(Tailscale.disconnectPendingIntent(appContext))
        Tailscale.setConnected(appContext, connect = !on)
    }

    /** The app came to the front: keep the connection, and watch for Wi-Fi networks. [onWifi] runs on a system thread. */
    fun onForeground(onWifi: (Network) -> Unit) {
        alarms?.cancel(Tailscale.disconnectPendingIntent(appContext))
        heldOff = false
        if (wifiWatch == null) wifiWatch = Tailscale.watchWifi(appContext, onWifi)
    }

    fun onBackground() {
        wifiWatch?.let { Tailscale.unwatch(appContext, it) }
        wifiWatch = null
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
        alarms?.cancel(Tailscale.disconnectPendingIntent(appContext))
        if (ours && Tailscale.isVpnActive(appContext)) Tailscale.setConnected(appContext, connect = false)
        ours = false
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val DISCONNECT_TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 1_000L

        /** Time in the background before an auto-connected Tailscale is disconnected. */
        const val LINGER_MS = 30_000L
    }
}
