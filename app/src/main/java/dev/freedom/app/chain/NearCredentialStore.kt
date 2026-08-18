package dev.freedom.app.chain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NearCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun hasCredentials(): Boolean = preferences.contains(KEY_CIPHERTEXT)

    fun accountId(): String? = load().getOrNull()?.accountId

    fun load(): Result<NearCredentials> = runCatching {
        val iv = Base64.getDecoder().decode(
            preferences.getString(KEY_IV, null) ?: error("Chiave NEAR non configurata")
        )
        val ciphertext = Base64.getDecoder().decode(
            preferences.getString(KEY_CIPHERTEXT, null) ?: error("Chiave NEAR non configurata")
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val plaintext = cipher.doFinal(ciphertext)
        try {
            val value = JSONObject(String(plaintext, StandardCharsets.UTF_8))
            NearCredentials.parse(value.getString("account_id"), value.getString("private_key"))
        } finally {
            plaintext.fill(0)
        }
    }

    fun save(credentials: NearCredentials) {
        val plaintext = JSONObject()
            .put("version", 1)
            .put("account_id", credentials.accountId)
            .put("private_key", credentials.encodedPrivateKey())
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            cipher.updateAAD(AAD)
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            require(iv.size == 12) { "IV AES-GCM Android non valido" }
            check(
                preferences.edit()
                    .putString(KEY_IV, Base64.getEncoder().encodeToString(iv))
                    .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                    .commit()
            ) { "Impossibile salvare la chiave NEAR" }
            val restored = load().getOrThrow()
            require(restored.accountId == credentials.accountId &&
                restored.publicKey.contentEquals(credentials.publicKey)
            ) { "Verifica della chiave NEAR salvata non riuscita" }
        } finally {
            plaintext.fill(0)
        }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Impossibile rimuovere la chiave NEAR" }
    }

    private fun encryptionKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "freedom.near.credentials.v1"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_ALIAS = "freedom.near.credentials.aes.v1"
        val AAD = "Freedom NEAR credentials v1".toByteArray(StandardCharsets.UTF_8)
    }
}
