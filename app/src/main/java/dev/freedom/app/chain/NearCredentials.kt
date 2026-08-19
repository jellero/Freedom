package dev.freedom.app.chain

import android.net.Uri
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

class NearCredentials private constructor(
    val accountId: String,
    privateSeed: ByteArray,
    publicKey: ByteArray
) {
    val privateSeed: ByteArray = privateSeed.copyOf()
    val publicKey: ByteArray = publicKey.copyOf()
    val publicKeyString: String get() = "ed25519:${Base58.encode(publicKey)}"

    fun encodedPrivateKey(): String = "ed25519:${Base58.encode(privateSeed + publicKey)}"

    companion object {
        private val ACCOUNT_ID = Regex("^(?=.{2,64}$)[a-z0-9]+(?:[-_.][a-z0-9]+)*$")

        fun parse(accountId: String, privateKey: String): NearCredentials {
            val normalizedAccount = accountId.trim().lowercase()
            require(ACCOUNT_ID.matches(normalizedAccount)) { "Account ID NEAR non valido" }

            val normalizedKey = privateKey.trim()
            require(normalizedKey.startsWith("ed25519:")) { "La chiave deve iniziare con ed25519:" }
            val decoded = Base58.decode(normalizedKey.removePrefix("ed25519:"))
            require(decoded.size == 32 || decoded.size == 64) {
                "Lunghezza della chiave privata NEAR non valida"
            }
            val seed = decoded.copyOfRange(0, 32)
            val derivedPublicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
            if (decoded.size == 64) {
                require(decoded.copyOfRange(32, 64).contentEquals(derivedPublicKey)) {
                    "Chiave privata e chiave pubblica NEAR non corrispondono"
                }
            }
            return NearCredentials(normalizedAccount, seed, derivedPublicKey)
        }
    }
}

object NearKeyQrCodec {
    private const val SCHEME = "freedom"
    private const val HOST = "near-key"

    fun encode(credentials: NearCredentials, network: IdentityNetwork): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("v", "1")
            .appendQueryParameter("network", network.id)
            .appendQueryParameter("account", credentials.accountId)
            .appendQueryParameter("private_key", credentials.encodedPrivateKey())
            .build()
            .toString()

    fun decode(value: String, expectedNetwork: IdentityNetwork): NearCredentials {
        val uri = Uri.parse(value.trim())
        require(uri.scheme == SCHEME && uri.host == HOST && uri.getQueryParameter("v") == "1") {
            "QR chiave Freedom non valido"
        }
        require(uri.getQueryParameter("network") == expectedNetwork.id) {
            "La chiave appartiene a una rete NEAR diversa"
        }
        return NearCredentials.parse(
            uri.getQueryParameter("account").orEmpty(),
            uri.getQueryParameter("private_key").orEmpty()
        )
    }
}
