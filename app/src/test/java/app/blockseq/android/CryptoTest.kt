package app.blockseq.android

import app.blockseq.android.crypto.BlockCrypto
import app.blockseq.android.crypto.EncryptionSetup
import app.blockseq.android.crypto.WrongPassword
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoTest {
    @Serializable
    data class Vectors(val password: String, val setup: EncryptionSetup, val blob: String, val plain: String)

    // Made with the browser's WebCrypto, the same steps as web/js/crypto.js (see the test resources).
    private val v = Json { ignoreUnknownKeys = true }.decodeFromString<Vectors>(
        javaClass.classLoader!!.getResource("webcrypto-vectors.json")!!.readText(),
    )

    @Test
    fun decryptsWhatTheWebAppWrote() {
        val key = BlockCrypto.unlock(v.setup, v.password)
        assertEquals(v.plain, BlockCrypto.decrypt(key, v.blob))
    }

    @Test(expected = WrongPassword::class)
    fun wrongPasswordIsRefused() {
        BlockCrypto.unlock(v.setup, "not it")
    }

    @Test
    fun encryptRoundTripsWithFreshIvs() {
        val key = BlockCrypto.unlock(v.setup, v.password)
        val a = BlockCrypto.encrypt(key, "secret ✓")
        val b = BlockCrypto.encrypt(key, "secret ✓")
        assertNotEquals(a, b)
        assert(a.startsWith("v1:"))
        assertEquals("secret ✓", BlockCrypto.decrypt(key, a))
    }

    @Test(expected = IllegalArgumentException::class)
    fun absurdRoundCountsAreRefused() {
        BlockCrypto.unlock(v.setup.copy(iterations = 10_000_000), v.password)
    }
}
