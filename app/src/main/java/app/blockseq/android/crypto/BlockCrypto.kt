package app.blockseq.android.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * #Encrypted blocks, byte-compatible with web/js/crypto.js and src/crypto.rs
 * in the server repo. One random AES-256-GCM key encrypts every block; the
 * server stores it wrapped with a key derived from the password (PBKDF2-
 * SHA256). Decryption happens only on this phone; the key lives in memory
 * while unlocked and is never written anywhere.
 */
@kotlinx.serialization.Serializable
data class EncryptionSetup(
    val version: Int = 1,
    val salt: String,
    val iterations: Int,
    @kotlinx.serialization.SerialName("pw_iv") val pwIv: String,
    @kotlinx.serialization.SerialName("pw_data") val pwData: String,
    val check: String = "",
)

class WrongPassword : Exception("Wrong password")

object BlockCrypto {
    const val BLOB_PREFIX = "v1:"
    /** The web app's password rounds; a stored count above it is refused (as crypto.rs does). */
    const val MAX_ROUNDS = 600_000
    private val random = SecureRandom()
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        require(rounds in 1..MAX_ROUNDS) { "unsupported encryption settings" }
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun aes(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return c.doFinal(data)
    }

    /** The content key from the password. Slow on purpose (PBKDF2): call off the main thread. */
    fun unlock(setup: EncryptionSetup, password: String): ByteArray {
        val kek = derive(password, b64d.decode(setup.salt), setup.iterations)
        val raw = try {
            aes(Cipher.DECRYPT_MODE, kek, b64d.decode(setup.pwIv), b64d.decode(setup.pwData))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassword()
        }
        if (setup.check.isNotEmpty() && setup.check != checkOf(raw)) throw WrongPassword()
        return raw
    }

    /** The web app's key fingerprint ("blockseq-key-check:" + key, SHA-256, base64). */
    fun checkOf(raw: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("blockseq-key-check:".toByteArray())
        return b64e.encodeToString(md.digest(raw))
    }

    fun encrypt(key: ByteArray, text: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val ct = aes(Cipher.ENCRYPT_MODE, key, iv, text.toByteArray(Charsets.UTF_8))
        return "$BLOB_PREFIX${b64e.encodeToString(iv)}:${b64e.encodeToString(ct)}"
    }

    fun decrypt(key: ByteArray, blob: String): String {
        val parts = blob.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "not an encrypted blob" }
        return String(aes(Cipher.DECRYPT_MODE, key, b64d.decode(parts[1]), b64d.decode(parts[2])), Charsets.UTF_8)
    }
}
