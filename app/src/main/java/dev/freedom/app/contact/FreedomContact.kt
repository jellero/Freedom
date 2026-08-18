package dev.freedom.app.contact

import dev.freedom.app.crypto.Crypto
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.PublicKey

data class FreedomContact(
    val displayName: String,
    val freedomNumber: String,
    val fingerprint: String,
    val networkId: String
)

object FreedomNumber {
    private val modulus = BigInteger.TEN.pow(PAYLOAD_LENGTH)

    fun fromPublicKey(publicKey: PublicKey): String {
        return fromDigest(Crypto.sha256(publicKey.encoded))
    }

    fun fromFingerprint(fingerprint: String): String {
        val digest = fingerprint.split(':').map { it.toInt(16).toByte() }.toByteArray()
        require(digest.size == 32)
        return fromDigest(digest)
    }

    private fun fromDigest(digest: ByteArray): String {
        val payload = BigInteger(1, digest)
            .mod(modulus)
            .toString()
            .padStart(PAYLOAD_LENGTH, '0')
        return payload + checkDigit(payload)
    }

    fun normalize(value: String): String = value.filter(Char::isDigit)

    fun format(value: String): String {
        val normalized = normalize(value)
        return normalized.chunked(4).joinToString(" ")
    }

    fun isValid(value: String): Boolean {
        val normalized = normalize(value)
        if (normalized.length != TOTAL_LENGTH) return false
        if (normalized.all { it == '0' }) return false

        var sum = 0
        for (index in normalized.indices.reversed()) {
            var digit = normalized[index].digitToInt()
            if ((normalized.lastIndex - index) % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum % 10 == 0
    }

    private fun checkDigit(payload: String): Int {
        var sum = 0
        for (index in payload.indices.reversed()) {
            var digit = payload[index].digitToInt()
            if ((payload.lastIndex - index) % 2 == 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return (10 - (sum % 10)) % 10
    }

    private const val PAYLOAD_LENGTH = 11
    private const val TOTAL_LENGTH = 12
}

object FreedomContactCodec {
    fun encode(contact: FreedomContact): String {
        require(FreedomNumber.isValid(contact.freedomNumber))
        require(isFingerprint(contact.fingerprint))
        return buildString {
            append("freedom://contact?v=1")
            append("&network=").append(encodeValue(contact.networkId))
            append("&number=").append(contact.freedomNumber)
            append("&name=").append(encodeValue(contact.displayName.take(MAX_NAME_LENGTH)))
            append("&fingerprint=").append(encodeValue(contact.fingerprint.uppercase()))
        }
    }

    fun decode(rawValue: String): FreedomContact {
        val uri = URI(rawValue.trim())
        require(uri.scheme.equals("freedom", ignoreCase = true))
        require(uri.host.equals("contact", ignoreCase = true))
        val values = uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.contains('=') }
            .associate { part ->
                val (key, value) = part.split('=', limit = 2)
                decodeValue(key) to decodeValue(value)
            }

        require(values["v"] == "1")
        val number = FreedomNumber.normalize(values.getValue("number"))
        require(FreedomNumber.isValid(number))
        val fingerprint = values.getValue("fingerprint").uppercase()
        require(isFingerprint(fingerprint))
        val network = values.getValue("network").trim()
        require(network.isNotBlank() && network.length <= 40)
        val name = values["name"]?.trim().orEmpty().take(MAX_NAME_LENGTH)

        return FreedomContact(
            displayName = name,
            freedomNumber = number,
            fingerprint = fingerprint,
            networkId = network
        )
    }

    private fun isFingerprint(value: String): Boolean =
        FINGERPRINT.matches(value.uppercase())

    private fun encodeValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decodeValue(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private val FINGERPRINT = Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")
    private const val MAX_NAME_LENGTH = 48
}
