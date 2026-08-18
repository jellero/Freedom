package dev.freedom.app.protocol

import dev.freedom.app.crypto.Crypto
import dev.freedom.app.crypto.IdentityStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class FreedomSession private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val txKey: ByteArray,
    private val rxKey: ByteArray,
    private val txDirection: Int,
    private val rxDirection: Int,
    val remoteFingerprint: String,
    val sessionIdHex: String
) : Closeable {

    sealed interface Incoming {
        data class Text(val messageId: String, val text: String) : Incoming
        data class Ack(val messageId: String) : Incoming
    }

    private var txSequence = 0L
    private var rxSequence = 0L
    private val sessionId = hexToBytes(sessionIdHex)

    fun sendText(text: String): String {
        require(text.isNotBlank())
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES)
        val messageId = UUID.randomUUID().toString()
        sendPayload(encodeText(messageId, text))
        return messageId
    }

    fun sendAck(messageId: String) {
        sendPayload(encodeAck(messageId))
    }

    fun read(): Incoming {
        val magic = input.readInt()
        if (magic != FRAME_MAGIC) throw ProtocolException("Invalid encrypted frame magic")

        val sequence = input.readLong()
        if (sequence <= rxSequence) throw ProtocolException("Replay detected: sequence=$sequence")
        if (sequence != rxSequence + 1) throw ProtocolException("Unexpected sequence gap")

        val ciphertext = readBlob(input, MAX_FRAME_BYTES)
        val nonce = nonce(rxDirection, sequence)
        val aad = frameAad(rxDirection, sequence)
        val plaintext = Crypto.aesGcmDecrypt(rxKey, nonce, aad, ciphertext)
        rxSequence = sequence
        return decodePayload(plaintext)
    }

    @Synchronized
    private fun sendPayload(payload: ByteArray) {
        val sequence = ++txSequence
        val nonce = nonce(txDirection, sequence)
        val aad = frameAad(txDirection, sequence)
        val ciphertext = Crypto.aesGcmEncrypt(txKey, nonce, aad, payload)

        synchronized(output) {
            output.writeInt(FRAME_MAGIC)
            output.writeLong(sequence)
            writeBlob(output, ciphertext)
            output.flush()
        }
    }

    private fun frameAad(direction: Int, sequence: Long): ByteArray =
        Crypto.sha256(
            "Freedom-M1-Frame".toByteArray(StandardCharsets.UTF_8),
            sessionId,
            Crypto.intBytes(direction),
            Crypto.longBytes(sequence)
        )

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val HANDSHAKE_MAGIC = 0x46524431 // FRD1
        private const val FRAME_MAGIC = 0x46524631 // FRF1
        private const val VERSION = 1
        private const val INITIATOR_TO_RESPONDER = 1
        private const val RESPONDER_TO_INITIATOR = 2
        private const val MAX_HANDSHAKE_BLOB = 16 * 1024
        private const val MAX_FRAME_BYTES = 256 * 1024
        private const val MAX_TEXT_BYTES = 64 * 1024

        fun initiate(socket: Socket, identity: IdentityStore): FreedomSession =
            handshake(socket, identity, initiator = true)

        fun accept(socket: Socket, identity: IdentityStore): FreedomSession =
            handshake(socket, identity, initiator = false)

        private fun handshake(
            socket: Socket,
            identity: IdentityStore,
            initiator: Boolean
        ): FreedomSession {
            socket.tcpNoDelay = true
            socket.soTimeout = 15_000
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val ephemeral = Crypto.ephemeralEcKeyPair()
            val localNonce = Crypto.randomBytes(32)
            val localIdentity = identity.publicKey.encoded
            val localEphemeral = ephemeral.public.encoded

            val remoteNonce: ByteArray
            val remoteIdentityEncoded: ByteArray
            val remoteEphemeralEncoded: ByteArray
            val transcript: ByteArray

            if (initiator) {
                output.writeInt(HANDSHAKE_MAGIC)
                output.writeInt(VERSION)
                writeBlob(output, localNonce)
                writeBlob(output, localIdentity)
                writeBlob(output, localEphemeral)
                output.flush()

                requireHandshakeHeader(input)
                remoteNonce = readBlob(input, MAX_HANDSHAKE_BLOB)
                remoteIdentityEncoded = readBlob(input, MAX_HANDSHAKE_BLOB)
                remoteEphemeralEncoded = readBlob(input, MAX_HANDSHAKE_BLOB)
                val serverSignature = readBlob(input, MAX_HANDSHAKE_BLOB)

                transcript = transcript(
                    clientNonce = localNonce,
                    serverNonce = remoteNonce,
                    clientIdentity = localIdentity,
                    serverIdentity = remoteIdentityEncoded,
                    clientEphemeral = localEphemeral,
                    serverEphemeral = remoteEphemeralEncoded
                )

                val remoteIdentity = Crypto.decodeEcPublicKey(remoteIdentityEncoded)
                if (!Crypto.verify(remoteIdentity, transcript, serverSignature)) {
                    throw ProtocolException("Responder identity signature rejected")
                }

                writeBlob(output, Crypto.sign(identity.privateKey, transcript))
                output.flush()
            } else {
                requireHandshakeHeader(input)
                remoteNonce = readBlob(input, MAX_HANDSHAKE_BLOB)
                remoteIdentityEncoded = readBlob(input, MAX_HANDSHAKE_BLOB)
                remoteEphemeralEncoded = readBlob(input, MAX_HANDSHAKE_BLOB)

                transcript = transcript(
                    clientNonce = remoteNonce,
                    serverNonce = localNonce,
                    clientIdentity = remoteIdentityEncoded,
                    serverIdentity = localIdentity,
                    clientEphemeral = remoteEphemeralEncoded,
                    serverEphemeral = localEphemeral
                )

                output.writeInt(HANDSHAKE_MAGIC)
                output.writeInt(VERSION)
                writeBlob(output, localNonce)
                writeBlob(output, localIdentity)
                writeBlob(output, localEphemeral)
                writeBlob(output, Crypto.sign(identity.privateKey, transcript))
                output.flush()

                val clientSignature = readBlob(input, MAX_HANDSHAKE_BLOB)
                val remoteIdentity = Crypto.decodeEcPublicKey(remoteIdentityEncoded)
                if (!Crypto.verify(remoteIdentity, transcript, clientSignature)) {
                    throw ProtocolException("Initiator identity signature rejected")
                }
            }

            val remoteEphemeral = Crypto.decodeEcPublicKey(remoteEphemeralEncoded)
            val sharedSecret = Crypto.ecdh(ephemeral.private, remoteEphemeral)
            val material = Crypto.hkdfSha256(
                inputKeyMaterial = sharedSecret,
                salt = transcript,
                info = "Freedom-M1-Session-v1".toByteArray(StandardCharsets.UTF_8),
                outputLength = 64
            )
            sharedSecret.fill(0)

            val initiatorToResponder = material.copyOfRange(0, 32)
            val responderToInitiator = material.copyOfRange(32, 64)
            material.fill(0)

            val sessionId = Crypto.sha256(
                "Freedom-M1-Session-ID".toByteArray(StandardCharsets.UTF_8),
                transcript
            ).copyOfRange(0, 16)

            socket.soTimeout = 0
            val remoteIdentity = Crypto.decodeEcPublicKey(remoteIdentityEncoded)
            return FreedomSession(
                socket = socket,
                input = input,
                output = output,
                txKey = if (initiator) initiatorToResponder else responderToInitiator,
                rxKey = if (initiator) responderToInitiator else initiatorToResponder,
                txDirection = if (initiator) INITIATOR_TO_RESPONDER else RESPONDER_TO_INITIATOR,
                rxDirection = if (initiator) RESPONDER_TO_INITIATOR else INITIATOR_TO_RESPONDER,
                remoteFingerprint = Crypto.fingerprint(remoteIdentity),
                sessionIdHex = sessionId.joinToString("") { "%02x".format(it) }
            )
        }

        private fun requireHandshakeHeader(input: DataInputStream) {
            val magic = input.readInt()
            val version = input.readInt()
            if (magic != HANDSHAKE_MAGIC) throw ProtocolException("Invalid handshake magic")
            if (version != VERSION) throw ProtocolException("Unsupported protocol version: $version")
        }

        private fun transcript(
            clientNonce: ByteArray,
            serverNonce: ByteArray,
            clientIdentity: ByteArray,
            serverIdentity: ByteArray,
            clientEphemeral: ByteArray,
            serverEphemeral: ByteArray
        ): ByteArray = Crypto.sha256(
            "Freedom-M1-Handshake-v1".toByteArray(StandardCharsets.UTF_8),
            clientNonce,
            serverNonce,
            clientIdentity,
            serverIdentity,
            clientEphemeral,
            serverEphemeral
        )

        private fun nonce(direction: Int, sequence: Long): ByteArray =
            Crypto.intBytes(direction) + Crypto.longBytes(sequence)

        private fun encodeText(messageId: String, text: String): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeByte(1)
                writeString(out, messageId)
                writeString(out, text)
            }
            return bytes.toByteArray()
        }

        private fun encodeAck(messageId: String): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeByte(2)
                writeString(out, messageId)
            }
            return bytes.toByteArray()
        }

        private fun decodePayload(payload: ByteArray): Incoming {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                return when (input.readUnsignedByte()) {
                    1 -> Incoming.Text(
                        messageId = readString(input, 256),
                        text = readString(input, MAX_TEXT_BYTES)
                    )
                    2 -> Incoming.Ack(readString(input, 256))
                    else -> throw ProtocolException("Unknown inner frame type")
                }
            }
        }

        private fun writeString(output: DataOutputStream, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeBlob(output, bytes)
        }

        private fun readString(input: DataInputStream, maxBytes: Int): String =
            String(readBlob(input, maxBytes), StandardCharsets.UTF_8)

        private fun writeBlob(output: DataOutputStream, bytes: ByteArray) {
            output.writeInt(bytes.size)
            output.write(bytes)
        }

        private fun readBlob(input: DataInputStream, maxBytes: Int): ByteArray {
            val size = input.readInt()
            if (size < 0 || size > maxBytes) throw ProtocolException("Invalid blob size: $size")
            return ByteArray(size).also(input::readFully)
        }

        private fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0)
            return ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

class ProtocolException(message: String) : Exception(message)
