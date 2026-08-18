package dev.freedom.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MailboxKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val keyPair: KeyPair by lazy { loadOrCreate() }
    val privateKey: PrivateKey get() = keyPair.private
    val publicKey: PublicKey get() = keyPair.public
    val compressedPublicKey: ByteArray get() = Crypto.compressP256PublicKey(publicKey)

    private fun loadOrCreate(): KeyPair {
        val encrypted = preferences.getString(KEY_ENCRYPTED_PRIVATE, null)
        val encodedPublic = preferences.getString(KEY_PUBLIC, null)
        if (encrypted != null && encodedPublic != null) {
            return runCatching {
                val privateBytes = decrypt(Base64.getDecoder().decode(encrypted))
                val privateKey = KeyFactory.getInstance("EC")
                    .generatePrivate(PKCS8EncodedKeySpec(privateBytes))
                val publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encodedPublic)))
                KeyPair(publicKey, privateKey)
            }.getOrElse { throw IllegalStateException("Chiave mailbox cifrata non leggibile", it) }
        }

        val generated = Crypto.ephemeralEcKeyPair()
        val encryptedPrivate = encrypt(generated.private.encoded)
        check(
            preferences.edit()
                .putString(KEY_ENCRYPTED_PRIVATE, Base64.getEncoder().encodeToString(encryptedPrivate))
                .putString(KEY_PUBLIC, Base64.getEncoder().encodeToString(generated.public.encoded))
                .commit()
        ) { "Impossibile salvare la chiave mailbox" }
        return generated
    }

    private fun encryptionKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    private fun decrypt(value: ByteArray): ByteArray {
        require(value.size > NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey(),
            GCMParameterSpec(128, value.copyOfRange(0, NONCE_BYTES))
        )
        return cipher.doFinal(value.copyOfRange(NONCE_BYTES, value.size))
    }

    private companion object {
        const val PREFERENCES = "freedom.mailbox.v1"
        const val KEY_ENCRYPTED_PRIVATE = "private"
        const val KEY_PUBLIC = "public"
        const val KEYSTORE_ALIAS = "freedom.mailbox.wrap.v1"
        const val NONCE_BYTES = 12
    }
}
