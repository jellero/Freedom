package dev.freedom.app.crypto

import java.security.PrivateKey
import java.security.PublicKey

interface DeviceIdentity {
    val privateKey: PrivateKey
    val publicKey: PublicKey

    val fingerprint: String
        get() = Crypto.fingerprint(publicKey)
}
