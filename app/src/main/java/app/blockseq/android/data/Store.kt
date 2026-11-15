package app.blockseq.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What the app remembers: the server address, the workbook, and this phone's
 * device token. The token is encrypted with a key kept in the Android
 * Keystore (it cannot be read out of the phone). The password is never stored.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("blockseq", Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString("server", "") ?: ""
        set(v) = prefs.edit().putString("server", v).apply()

    var workbook: String
        get() = prefs.getString("workbook", "main") ?: "main"
        set(v) = prefs.edit().putString("workbook", v).apply()

    var deviceName: String
        get() = prefs.getString("device_name", "") ?: ""
        set(v) = prefs.edit().putString("device_name", v).apply()

    /** Action buttons (picture viewer, page actions) along the bottom, within thumb reach. */
    var controlsAtBottom: Boolean
        get() = prefs.getBoolean("controls_bottom", true)
        set(v) = prefs.edit().putBoolean("controls_bottom", v).apply()

    /** Long code blocks start folded (same settings and defaults as the web app). */
    var codeFold: CodeFold
        get() = CodeFold(prefs.getBoolean("code_fold", true), prefs.getInt("code_fold_lines", 20), prefs.getInt("code_fold_show", 6))
        set(v) = prefs.edit().putBoolean("code_fold", v.on).putInt("code_fold_lines", v.lines).putInt("code_fold_show", v.show).apply()

    /** Where the search field sits on the Search tab. */
    var searchAtBottom: Boolean
        get() = prefs.getBoolean("search_bottom", false)
        set(v) = prefs.edit().putBoolean("search_bottom", v).apply()

    /** Striped rows on the Journals tab: "off", "subtle" (default), "stronger" or "accent". */
    // Observable, so the Journals tab under the settings card redraws at once.
    private val stripesState = androidx.compose.runtime.mutableStateOf(prefs.getString("journal_stripes", "subtle") ?: "subtle")
    var journalStripes: String
        get() = stripesState.value
        set(v) { stripesState.value = v; prefs.edit().putString("journal_stripes", v).apply() }

    /** The editing bar inline (between the edited block and the row above) instead of above the keyboard. */
    // Observable, so an open page switches at once when changed in Settings.
    private val editBarInlineState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("edit_bar_inline", false))
    var editBarInline: Boolean
        get() = editBarInlineState.value
        set(v) { editBarInlineState.value = v; prefs.edit().putBoolean("edit_bar_inline", v).apply() }

    /** The day weeks start on in calendars (java.time DayOfWeek value: 7 = Sunday, the default). */
    var weekStart: Int
        get() = prefs.getInt("week_start", 7)
        set(v) = prefs.edit().putInt("week_start", v).apply()

    /** Connect Tailscale while the app is open and the server is out of reach (mobile data, other Wi-Fi). */
    var autoTailscale: Boolean
        get() = prefs.getBoolean("auto_tailscale", true)
        set(v) = prefs.edit().putBoolean("auto_tailscale", v).apply()

    /** Show the Tailscale key in the left pane (tap to connect or disconnect). */
    var showTailscaleButton: Boolean
        get() = prefs.getBoolean("tailscale_button", true)
        set(v) = prefs.edit().putBoolean("tailscale_button", v).apply()

    /** The Tailscale connection now up was made by the app (kept across an app restart, so it is still cleaned up). */
    var tailscaleOurs: Boolean
        get() = prefs.getBoolean("tailscale_ours", false)
        set(v) = prefs.edit().putBoolean("tailscale_ours", v).apply()

    /** Paired: a server address, and a token unless the server needs none. */
    val paired: Boolean get() = server.isNotEmpty() && prefs.getBoolean("paired", false)

    var token: String?
        get() = prefs.getString("token", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(v) {
            prefs.edit().putString("token", v?.let(::encrypt)).putBoolean("paired", true).apply()
        }

    fun forget() {
        prefs.edit().remove("token").putBoolean("paired", false).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(text: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val b = Base64.getEncoder()
        return b.encodeToString(c.iv) + ":" + b.encodeToString(c.doFinal(text.toByteArray()))
    }

    private fun decrypt(stored: String): String {
        val (iv, ct) = stored.split(':').let { it[0] to it[1] }
        val d = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, d.decode(iv)))
        return String(c.doFinal(d.decode(ct)))
    }

    private companion object {
        const val ALIAS = "blockseq-device-token"
    }
}

/** Folding long code blocks: on/off, "long" means more than [lines], [show] lines stay visible. */
data class CodeFold(val on: Boolean = true, val lines: Int = 20, val show: Int = 6)
