package dev.freedom.app.crypto

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.AlgorithmParameters
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach(digest::update)
        return digest.digest()
    }

    fun fingerprint(publicKey: PublicKey): String =
        sha256(publicKey.encoded).joinToString(":") { "%02X".format(it) }

    fun ephemeralEcKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        return generator.generateKeyPair()
    }

    fun decodeEcPublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun decodeCompressedP256PublicKey(encoded: ByteArray): PublicKey {
        require(encoded.size == 33 && (encoded[0] == 2.toByte() || encoded[0] == 3.toByte()))
        val curve = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256r1")
        val point = curve.curve.decodePoint(encoded).normalize()
        val parameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(
                ECPoint(point.affineXCoord.toBigInteger(), point.affineYCoord.toBigInteger()),
                parameters
            )
        )
    }

    fun compressP256PublicKey(publicKey: PublicKey): ByteArray {
        val key = publicKey as? ECPublicKey
            ?: throw IllegalArgumentException("Chiave pubblica P-256 non valida")
        val x = key.w.affineX.toUnsignedFixed(32)
        val prefix = if (key.w.affineY.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefix.toByte()) + x
    }

    fun encryptChainMessage(
        recipientPublicKey: ByteArray,
        senderDeviceId: String,
        recipientDeviceId: String,
        messageId: String,
        expiresAtNs: Long,
        plaintext: ByteArray
    ): ChainMessageEnvelope {
        require(plaintext.isNotEmpty() && plaintext.size <= 3_900) { "Messaggio troppo lungo" }
        val ephemeral = ephemeralEcKeyPair()
        val shared = ecdh(ephemeral.private, decodeCompressedP256PublicKey(recipientPublicKey))
        val salt = chainMessageSalt(senderDeviceId, recipientDeviceId, messageId)
        val key = hkdfSha256(shared, salt, CHAIN_MESSAGE_INFO, 32)
        val nonce = randomBytes(12)
        val aad = chainMessageAad(senderDeviceId, recipientDeviceId, messageId, expiresAtNs)
        return ChainMessageEnvelope(
            ephemeralPublicKey = compressP256PublicKey(ephemeral.public),
            nonce = nonce,
            ciphertext = aesGcmEncrypt(key, nonce, aad, plaintext)
        )
    }

    fun decryptChainMessage(
        privateKey: PrivateKey,
        senderDeviceId: String,
        recipientDeviceId: String,
        messageId: String,
        expiresAtNs: Long,
        ephemeralPublicKey: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val shared = ecdh(privateKey, decodeCompressedP256PublicKey(ephemeralPublicKey))
        val salt = chainMessageSalt(senderDeviceId, recipientDeviceId, messageId)
        val key = hkdfSha256(shared, salt, CHAIN_MESSAGE_INFO, 32)
        val aad = chainMessageAad(senderDeviceId, recipientDeviceId, messageId, expiresAtNs)
        return aesGcmDecrypt(key, nonce, aad, ciphertext)
    }

    private fun chainMessageSalt(
        senderDeviceId: String,
        recipientDeviceId: String,
        messageId: String
    ): ByteArray = sha256(
        senderDeviceId.hexToBytes(),
        recipientDeviceId.hexToBytes(),
        messageId.hexToBytes()
    )

    private fun chainMessageAad(
        senderDeviceId: String,
        recipientDeviceId: String,
        messageId: String,
        expiresAtNs: Long
    ): ByteArray = senderDeviceId.hexToBytes() + recipientDeviceId.hexToBytes() +
        messageId.hexToBytes() + longBytes(expiresAtNs)

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey, random)
        signature.update(data)
        return signature.sign()
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signatureBytes: ByteArray): Boolean {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey)
        signature.update(data)
        return signature.verify(signatureBytes)
    }

    fun ecdh(privateKey: PrivateKey, remotePublicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(remotePublicKey, true)
        return agreement.generateSecret()
    }

    fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        require(outputLength in 1..(255 * 32))
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = extract.doFinal(inputKeyMaterial)

        val result = ByteArray(outputLength)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLength) {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()

            val count = minOf(previous.size, outputLength - offset)
            System.arraycopy(previous, 0, result, offset, count)
            offset += count
            counter++
        }
        return result
    }

    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun java.math.BigInteger.toUnsignedFixed(size: Int): ByteArray {
        val bytes = toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        require(bytes.size <= size)
        return ByteArray(size - bytes.size) + bytes
    }

    private val CHAIN_MESSAGE_INFO = "Freedom on-chain message v1".toByteArray(Charsets.UTF_8)
}

data class ChainMessageEnvelope(
    val ephemeralPublicKey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray
)
