package dev.freedom.app.chain

import java.math.BigInteger

internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val base = BigInteger.valueOf(58)

    fun encode(value: ByteArray): String {
        if (value.isEmpty()) return ""
        var number = BigInteger(1, value)
        val encoded = StringBuilder()
        while (number.signum() > 0) {
            val division = number.divideAndRemainder(base)
            encoded.append(ALPHABET[division[1].toInt()])
            number = division[0]
        }
        value.takeWhile { it == 0.toByte() }.forEach { _ -> encoded.append('1') }
        return encoded.reverse().toString()
    }

    fun decode(value: String): ByteArray {
        require(value.isNotEmpty()) { "Valore Base58 vuoto" }
        var number = BigInteger.ZERO
        value.forEach { character ->
            val digit = ALPHABET.indexOf(character)
            require(digit >= 0) { "Carattere Base58 non valido" }
            number = number.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }

        val magnitude = if (number.signum() == 0) ByteArray(0) else number.toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        val leadingZeroes = value.takeWhile { it == '1' }.length
        return ByteArray(leadingZeroes) + magnitude
    }
}
