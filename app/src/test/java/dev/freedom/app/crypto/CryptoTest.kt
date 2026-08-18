package dev.freedom.app.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoTest {
    @Test
    fun hkdfMatchesRfc5869TestVectorOne() {
        val output = Crypto.hkdfSha256(
            inputKeyMaterial = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            outputLength = 42
        )

        assertArrayEquals(
            hex(
                "3cb25f25faacd57a90434f64d0362f2a" +
                    "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                    "34007208d5b887185865"
            ),
            output
        )
    }

    @Test
    fun aesGcmRoundTripAuthenticatesAad() {
        val key = Crypto.randomBytes(32)
        val nonce = Crypto.randomBytes(12)
        val aad = "session-context".toByteArray()
        val plaintext = "messaggio riservato".toByteArray()

        val ciphertext = Crypto.aesGcmEncrypt(key, nonce, aad, plaintext)

        assertArrayEquals(plaintext, Crypto.aesGcmDecrypt(key, nonce, aad, ciphertext))
        assertEquals(plaintext.size + 16, ciphertext.size)
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
