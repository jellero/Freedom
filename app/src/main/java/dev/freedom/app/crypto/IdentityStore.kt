package dev.freedom.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class IdentityStore(context: Context) : DeviceIdentity {
    private val alias = "freedom.identity.m1"
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        ensureIdentity()
    }

    override val privateKey: PrivateKey
        get() = keyStore.getKey(alias, null) as PrivateKey

    override val publicKey: PublicKey
        get() = keyStore.getCertificate(alias).publicKey

    override val fingerprint: String
        get() = Crypto.fingerprint(publicKey)

    val deviceId: String by lazy {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (existing != null && DEVICE_ID.matches(existing)) return@lazy existing
        val generated = Crypto.randomBytes(32).toHex()
        check(preferences.edit().putString(KEY_DEVICE_ID, generated).commit()) {
            "Unable to persist Freedom device ID"
        }
        generated
    }

    val compressedPublicKey: ByteArray
        get() {
            val key = publicKey as ECPublicKey
            val x = key.w.affineX.toUnsignedFixed(32)
            val prefix = if (key.w.affineY.testBit(0)) 0x03 else 0x02
            return byteArrayOf(prefix.toByte()) + x
        }

    val rendezvousCapability: ByteArray by lazy {
        val existing = preferences.getString(KEY_RENDEZVOUS_CAPABILITY, null)
            ?.takeIf { RENDEZVOUS_CAPABILITY.matches(it) }
        if (existing != null) return@lazy existing.hexToBytes()
        val generated = Crypto.randomBytes(32)
        check(
            preferences.edit()
                .putString(KEY_RENDEZVOUS_CAPABILITY, generated.toHex())
                .commit()
        ) { "Unable to persist rendezvous capability" }
        generated
    }

    fun registrationSignature(contractId: String, protocolVersion: Int): ByteArray {
        val message = authorizationMessage(
            contractId = contractId,
            operation = REGISTER_OPERATION,
            authNonce = 0,
            keyEpoch = 1,
            protocolVersion = protocolVersion,
            keyMaterial = compressedPublicKey
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(message)
        return derToCanonicalRaw(signer.sign())
    }

    fun contactPublishSignature(
        contractId: String,
        freedomNumber: String,
        mailboxPublicKey: ByteArray,
        authNonce: Long,
        keyEpoch: Long = 1,
        protocolVersion: Int
    ): ByteArray {
        val keyMaterial = freedomNumber.toByteArray(Charsets.UTF_8) + rendezvousCapability +
            mailboxPublicKey
        val message = authorizationMessage(
            contractId = contractId,
            operation = PUBLISH_CONTACT_OPERATION,
            authNonce = authNonce,
            keyEpoch = keyEpoch,
            protocolVersion = protocolVersion,
            keyMaterial = keyMaterial
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(message)
        return derToCanonicalRaw(signer.sign())
    }

    fun messageSendSignature(
        contractId: String,
        recipientDeviceId: String,
        messageId: String,
        expiresAtNs: Long,
        ephemeralPublicKey: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        authNonce: Long,
        keyEpoch: Long,
        protocolVersion: Int
    ): ByteArray {
        val keyMaterial = messageId.hexToBytes() + recipientDeviceId.hexToBytes() +
            ByteBuffer.allocate(Long.SIZE_BYTES).putLong(expiresAtNs).array() +
            ephemeralPublicKey + nonce + Crypto.sha256(ciphertext)
        val message = authorizationMessage(
            contractId = contractId,
            operation = SEND_MESSAGE_OPERATION,
            authNonce = authNonce,
            keyEpoch = keyEpoch,
            protocolVersion = protocolVersion,
            keyMaterial = keyMaterial
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(message)
        return derToCanonicalRaw(signer.sign())
    }

    private fun ensureIdentity() {
        if (keyStore.containsAlias(alias)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private fun authorizationMessage(
        contractId: String,
        operation: Int,
        authNonce: Long,
        keyEpoch: Long,
        protocolVersion: Int,
        keyMaterial: ByteArray
    ): ByteArray {
        val contract = contractId.toByteArray(Charsets.UTF_8)
        val device = deviceId.hexToBytes()
        require(contract.size <= 0xffff && keyMaterial.size <= 0xffff)
        return ByteBuffer.allocate(
            AUTH_DOMAIN.size + 2 + contract.size + 1 + device.size + 8 + 8 + 2 + 2 + keyMaterial.size
        ).apply {
            put(AUTH_DOMAIN)
            putShort(contract.size.toShort())
            put(contract)
            put(operation.toByte())
            put(device)
            putLong(authNonce)
            putLong(keyEpoch)
            putShort(protocolVersion.toShort())
            putShort(keyMaterial.size.toShort())
            put(keyMaterial)
        }.array()
    }

    private fun derToCanonicalRaw(der: ByteArray): ByteArray {
        var offset = 0
        fun readByte(): Int = der.getOrNull(offset++)?.toInt()?.and(0xff)
            ?: throw IllegalArgumentException("Firma ECDSA DER troncata")
        fun readLength(): Int {
            val first = readByte()
            if (first and 0x80 == 0) return first
            val count = first and 0x7f
            require(count in 1..2) { "Lunghezza DER non valida" }
            var length = 0
            repeat(count) { length = (length shl 8) or readByte() }
            return length
        }
        require(readByte() == 0x30) { "Firma ECDSA DER non valida" }
        val sequenceLength = readLength()
        require(sequenceLength == der.size - offset) { "Lunghezza firma ECDSA non valida" }
        fun readInteger(): BigInteger {
            require(readByte() == 0x02) { "Intero ECDSA DER non valido" }
            val length = readLength()
            val end = offset + length
            require(length > 0 && end <= der.size) { "Intero ECDSA DER troncato" }
            val integer = BigInteger(1, der.copyOfRange(offset, end))
            offset = end
            return integer
        }
        val r = readInteger()
        var s = readInteger()
        require(offset == der.size)
        if (s > P256_HALF_ORDER) s = P256_ORDER.subtract(s)
        return r.toUnsignedFixed(32) + s.toUnsignedFixed(32)
    }

    private fun BigInteger.toUnsignedFixed(size: Int): ByteArray {
        val bytes = toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        require(bytes.size <= size) { "Intero crittografico troppo grande" }
        return ByteArray(size - bytes.size) + bytes
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val PREFERENCES = "freedom.identity.v1"
        const val KEY_DEVICE_ID = "device-id"
        const val KEY_RENDEZVOUS_CAPABILITY = "rendezvous-capability"
        const val REGISTER_OPERATION = 1
        const val PUBLISH_CONTACT_OPERATION = 4
        const val SEND_MESSAGE_OPERATION = 5
        val DEVICE_ID = Regex("[0-9a-f]{64}")
        val RENDEZVOUS_CAPABILITY = Regex("[0-9a-f]{64}")
        val AUTH_DOMAIN = "FREEDOM_REGISTRY_V1\u0000".toByteArray(Charsets.UTF_8)
        val P256_ORDER = BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
            16
        )
        val P256_HALF_ORDER = P256_ORDER.shiftRight(1)
    }
}
