package dev.freedom.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
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

/** Rotating mailbox keys limit how long historical on-chain ciphertext stays decryptable. */
class MailboxKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private var cachedEpochDay = Long.MIN_VALUE
    private var cachedKeyPairs: List<KeyPair> = emptyList()

    val keyPair: KeyPair get() = keyPairsNewestFirst().first()
    val privateKey: PrivateKey get() = keyPair.private
    val publicKey: PublicKey get() = keyPair.public
    val compressedPublicKey: ByteArray get() = Crypto.compressP256PublicKey(publicKey)

    @Synchronized
    fun privateKeysNewestFirst(): List<PrivateKey> = keyPairsNewestFirst().map(KeyPair::getPrivate)

    @Synchronized
    private fun keyPairsNewestFirst(): List<KeyPair> {
        val today = epochDay()
        if (cachedEpochDay == today && cachedKeyPairs.isNotEmpty()) return cachedKeyPairs

        val entries = loadEntries(today)
            .filter { it.epochDay >= today - RETAIN_PREVIOUS_DAYS }
            .toMutableList()
        if (entries.none { it.epochDay == today }) {
            val generated = Crypto.ephemeralEcKeyPair()
            entries += StoredKey(
                epochDay = today,
                encryptedPrivate = Base64.getEncoder().encodeToString(encrypt(generated.private.encoded)),
                encodedPublic = Base64.getEncoder().encodeToString(generated.public.encoded)
            )
        }
        val normalized = entries.distinctBy(StoredKey::epochDay).sortedByDescending(StoredKey::epochDay)
        persist(normalized)
        cachedKeyPairs = normalized.map(::decode)
        cachedEpochDay = today
        return cachedKeyPairs
    }

    private fun loadEntries(today: Long): List<StoredKey> {
        preferences.getString(KEY_RING, null)?.let { raw ->
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.getJSONObject(index)
                        add(StoredKey(value.getLong("epoch_day"), value.getString("private"), value.getString("public")))
                    }
                }
            }.getOrElse { throw IllegalStateException("Archivio chiavi mailbox non leggibile", it) }
        }
        val legacyPrivate = preferences.getString(KEY_ENCRYPTED_PRIVATE, null)
        val legacyPublic = preferences.getString(KEY_PUBLIC, null)
        return if (legacyPrivate != null && legacyPublic != null) {
            listOf(StoredKey(today, legacyPrivate, legacyPublic))
        } else emptyList()
    }

    private fun persist(entries: List<StoredKey>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("epoch_day", entry.epochDay).put("private", entry.encryptedPrivate).put("public", entry.encodedPublic))
        }
        check(preferences.edit().putString(KEY_RING, array.toString()).remove(KEY_ENCRYPTED_PRIVATE).remove(KEY_PUBLIC).commit()) {
            "Impossibile salvare le chiavi mailbox"
        }
    }

    private fun decode(entry: StoredKey): KeyPair = runCatching {
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(decrypt(Base64.getDecoder().decode(entry.encryptedPrivate)))
        )
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(entry.encodedPublic))
        )
        KeyPair(publicKey, privateKey)
    }.getOrElse { throw IllegalStateException("Chiave mailbox cifrata non leggibile", it) }

    private fun encryptionKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, value.copyOfRange(0, NONCE_BYTES)))
        return cipher.doFinal(value.copyOfRange(NONCE_BYTES, value.size))
    }

    private fun epochDay(): Long = System.currentTimeMillis() / MILLIS_PER_DAY

    private data class StoredKey(val epochDay: Long, val encryptedPrivate: String, val encodedPublic: String)

    private companion object {
        const val PREFERENCES = "freedom.mailbox.v1"
        const val KEY_ENCRYPTED_PRIVATE = "private"
        const val KEY_PUBLIC = "public"
        const val KEY_RING = "key_ring.v2"
        const val KEYSTORE_ALIAS = "freedom.mailbox.wrap.v1"
        const val NONCE_BYTES = 12
        const val RETAIN_PREVIOUS_DAYS = 2L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
