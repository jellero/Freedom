package dev.freedom.app.contact

import dev.freedom.app.crypto.Crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class FreedomContactTest {
    @Test
    fun freedomNumberMatchesContractVector() {
        val compressedGenerator = hex(
            "036b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
        )

        assertEquals("71110821717511868363", FreedomNumber.fromCompressedPublicKey(compressedGenerator))
    }

    @Test
    fun freedomNumberIsStableAndChecksummed() {
        val keyPair = Crypto.ephemeralEcKeyPair()
        val number = FreedomNumber.fromPublicKey(keyPair.public)

        assertEquals(20, number.length)
        assertTrue(FreedomNumber.isValid(number))
        assertEquals(number, FreedomNumber.fromPublicKey(keyPair.public))
        assertEquals(number, FreedomNumber.fromFingerprint(Crypto.fingerprint(keyPair.public)))

        val changedLastDigit = number.dropLast(1) + ((number.last().digitToInt() + 1) % 10)
        assertFalse(FreedomNumber.isValid(changedLastDigit))
    }

    @Test
    fun contactQrRoundTripsWithoutPrivateMaterial() {
        val keyPair = Crypto.ephemeralEcKeyPair()
        val contact = FreedomContact(
            displayName = "Giulia & Luca",
            freedomNumber = FreedomNumber.fromPublicKey(keyPair.public),
            fingerprint = Crypto.fingerprint(keyPair.public),
            networkId = "near-testnet"
        )

        val encoded = FreedomContactCodec.encode(contact)

        assertTrue(encoded.startsWith("freedom://contact?"))
        assertFalse(encoded.contains("private", ignoreCase = true))
        assertEquals(contact, FreedomContactCodec.decode(encoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun contactQrRejectsNumberThatDoesNotMatchEmbeddedIdentity() {
        val identity = Crypto.ephemeralEcKeyPair()
        val other = Crypto.ephemeralEcKeyPair()
        val mailbox = Crypto.ephemeralEcKeyPair()
        FreedomContactCodec.encode(
            FreedomContact(
                displayName = "Mallory",
                freedomNumber = FreedomNumber.fromPublicKey(other.public),
                fingerprint = Crypto.fingerprint(identity.public),
                networkId = "near-testnet",
                deviceId = "ab".repeat(32),
                identityPublicKey = Base64.getEncoder().encodeToString(Crypto.compressP256PublicKey(identity.public)),
                mailboxPublicKey = Base64.getEncoder().encodeToString(Crypto.compressP256PublicKey(mailbox.public)),
                keyEpoch = 1
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun contactQrRejectsInvalidNumber() {
        FreedomContactCodec.decode(
            "freedom://contact?v=1&network=near-testnet&number=12345678901234567890" +
                "&name=Test&fingerprint=" + "AA:".repeat(31) + "AA"
        )
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
