@file:Suppress("UseKtx")

package dev.freedom.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small AES-GCM store with transparent migration from one legacy plaintext preference. */
class EncryptedJsonStore(
    context: Context,
    private val preferencesName: String,
    private val legacyKey: String,
    private val encryptedKey: String,
    private val keyStoreAlias: String
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val aad = "$preferencesName:$encryptedKey".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun read(): String? {
        preferences.getString(encryptedKey, null)?.let { envelope ->
            return decrypt(envelope)
        }
        val legacy = preferences.getString(legacyKey, null) ?: return null
        write(legacy)
        return legacy
    }

    @Synchronized
    fun write(value: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(aad)
        val envelope = JSONObject()
            .put("v", 1)
            .put("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray())))
            .toString()
        return preferences.edit()
            .putString(encryptedKey, envelope)
            .remove(legacyKey)
            .commit()
    }

    private fun decrypt(rawEnvelope: String): String {
        return runCatching {
            val envelope = JSONObject(rawEnvelope)
            require(envelope.getInt("v") == 1) { "Versione archivio cifrato non supportata" }
            val iv = Base64.getDecoder().decode(envelope.getString("iv"))
            require(iv.size == GCM_NONCE_BYTES) { "Nonce archivio cifrato non valido" }
            val ciphertext = Base64.getDecoder().decode(envelope.getString("ciphertext"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrElse { throw IllegalStateException("Archivio locale cifrato non leggibile", it) }
    }

    private fun encryptionKey(): SecretKey {
        (keyStore.getKey(keyStoreAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyStoreAlias,
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

    private companion object {
        const val GCM_NONCE_BYTES = 12
    }
}
