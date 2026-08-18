package dev.freedom.app.contact

import dev.freedom.app.crypto.Crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreedomContactTest {
    @Test
    fun freedomNumberIsStableAndChecksummed() {
        val keyPair = Crypto.ephemeralEcKeyPair()
        val number = FreedomNumber.fromPublicKey(keyPair.public)

        assertEquals(12, number.length)
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
    fun contactQrRejectsInvalidNumber() {
        FreedomContactCodec.decode(
            "freedom://contact?v=1&network=near-testnet&number=123456789012" +
                "&name=Test&fingerprint=" + "AA:".repeat(31) + "AA"
        )
    }
}
