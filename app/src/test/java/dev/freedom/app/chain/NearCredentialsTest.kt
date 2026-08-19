package dev.freedom.app.chain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NearCredentialsTest {
    @Test
    fun parsesNearExtendedSecretAndDerivesExpectedPublicKey() {
        val credentials = NearCredentials.parse(ACCOUNT, PRIVATE_KEY)

        assertEquals(ACCOUNT, credentials.accountId)
        assertEquals(PUBLIC_KEY, credentials.publicKeyString)
        assertEquals(PRIVATE_KEY, credentials.encodedPrivateKey())
    }

    @Test
    fun rejectsMismatchedExtendedSecret() {
        val decoded = Base58.decode(PRIVATE_KEY.removePrefix("ed25519:"))
        decoded[63] = (decoded[63].toInt() xor 1).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            NearCredentials.parse(ACCOUNT, "ed25519:${Base58.encode(decoded)}")
        }
    }

    @Test
    fun base58PreservesLeadingZeroes() {
        val value = byteArrayOf(0, 0, 1, 2, 3, 0)
        assertArrayEquals(value, Base58.decode(Base58.encode(value)))
    }

    private companion object {
        const val ACCOUNT = "alice.testnet"
        const val PRIVATE_KEY =
            "ed25519:5zGSi5wkCuWHpVs13xjLb367HyQ1rQiqPSALgEoyD81qUfJ44rvkF6bUjWNsqkMWfWyRpyJRvMPg6EAwFL3Mbdwa"
        const val PUBLIC_KEY = "ed25519:FqVpU396PJvPTp8NawW9HkxHXUUn7TwP1LrusUWCRNpL"
    }
}
