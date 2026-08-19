package dev.freedom.app.protocol

import dev.freedom.app.crypto.Crypto
import dev.freedom.app.crypto.DeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FreedomSessionTest {
    @Test(timeout = 10_000)
    fun mutuallyAuthenticatedSessionsExchangeTextAndAck() {
        sessionPair().use { pair ->
            assertEquals(pair.responderIdentity.fingerprint, pair.initiator.remoteFingerprint)
            assertEquals(pair.initiatorIdentity.fingerprint, pair.responder.remoteFingerprint)
            assertEquals(pair.initiator.sessionIdHex, pair.responder.sessionIdHex)

            val messageId = pair.initiator.sendText("ciao")
            val incoming = pair.responder.read()
            assertTrue(incoming is FreedomSession.Incoming.Text)
            incoming as FreedomSession.Incoming.Text
            assertEquals(messageId, incoming.messageId)
            assertEquals("ciao", incoming.text)

            pair.responder.sendAck(messageId)
            assertEquals(FreedomSession.Incoming.Ack(messageId), pair.initiator.read())
        }
    }

    @Test(timeout = 10_000)
    fun oversizedLocalMessageIsRejectedWithoutBreakingSession() {
        sessionPair().use { pair ->
            assertThrows(IllegalArgumentException::class.java) {
                pair.initiator.sendText("a".repeat(FreedomSession.MAX_TEXT_BYTES + 1))
            }

            val messageId = pair.initiator.sendText("ancora connesso")
            val incoming = pair.responder.read() as FreedomSession.Incoming.Text
            assertEquals(messageId, incoming.messageId)
            assertEquals("ancora connesso", incoming.text)
        }
    }

    private fun sessionPair(): SessionPair {
        val initiatorIdentity = TestIdentity()
        val responderIdentity = TestIdentity()
        val acceptExecutor = Executors.newSingleThreadExecutor()

        ServerSocket(0).use { server ->
            val responderFuture = acceptExecutor.submit<FreedomSession> {
                FreedomSession.accept(server.accept(), responderIdentity)
            }
            val initiator = FreedomSession.initiate(
                Socket("127.0.0.1", server.localPort),
                initiatorIdentity
            )
            val responder = responderFuture.get(5, TimeUnit.SECONDS)
            acceptExecutor.shutdownNow()
            return SessionPair(
                initiator,
                responder,
                initiatorIdentity,
                responderIdentity
            )
        }
    }

    private class TestIdentity : DeviceIdentity {
        private val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        override val privateKey: PrivateKey = keyPair.private
        override val publicKey: PublicKey = keyPair.public
        override val fingerprint: String = Crypto.fingerprint(publicKey)
    }

    private class SessionPair(
        val initiator: FreedomSession,
        val responder: FreedomSession,
        val initiatorIdentity: DeviceIdentity,
        val responderIdentity: DeviceIdentity
    ) : AutoCloseable {
        override fun close() {
            initiator.close()
            responder.close()
        }
    }
}
