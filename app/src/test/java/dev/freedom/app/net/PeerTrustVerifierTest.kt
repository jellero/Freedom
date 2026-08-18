package dev.freedom.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerTrustVerifierTest {
    private val store = InMemoryPeerTrustStore()
    private val verifier = PeerTrustVerifier(store)

    @Test
    fun firstUseMustBeExplicitlyTrustedAndIsThenPinned() {
        assertEquals(
            PeerTrustVerifier.Result.FirstUse,
            verifier.evaluate("192.168.1.10", "AA:BB")
        )

        assertTrue(verifier.trustFirstUse("192.168.1.10", "AA:BB"))
        assertEquals(
            PeerTrustVerifier.Result.Trusted,
            verifier.evaluate("192.168.1.10", "AA:BB")
        )
    }

    @Test
    fun pinnedFingerprintCannotBeSilentlyOverwritten() {
        assertTrue(verifier.trustFirstUse("192.168.1.10", "AA:BB"))

        val result = verifier.evaluate("192.168.1.10", "CC:DD")

        assertEquals(
            PeerTrustVerifier.Result.Mismatch("AA:BB"),
            result
        )
        assertFalse(verifier.trustFirstUse("192.168.1.10", "CC:DD"))
        assertEquals("AA:BB", store.trustedFingerprint("192.168.1.10"))
    }

    private class InMemoryPeerTrustStore : PeerTrustStore {
        private val entries = mutableMapOf<String, String>()

        override fun trustedFingerprint(peerId: String): String? = entries[peerId]

        override fun saveTrustedFingerprint(peerId: String, fingerprint: String) {
            entries[peerId] = fingerprint
        }
    }
}
