package dev.freedom.app.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedJsonStoreInstrumentedTest {
    @Test
    fun migratesPlaintextAndRoundTripsCiphertext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime().toString()
        val preferencesName = "freedom.test.encrypted.$suffix"
        val legacyKey = "legacy"
        val encryptedKey = "encrypted"
        context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit().putString(legacyKey, "[{\"secret\":\"ciao\"}]").commit()

        val store = EncryptedJsonStore(
            context,
            preferencesName,
            legacyKey,
            encryptedKey,
            "freedom.test.encrypted.key.$suffix"
        )

        assertEquals("[{\"secret\":\"ciao\"}]", store.read())
        val preferences = context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
        assertFalse(preferences.contains(legacyKey))
        assertNotNull(preferences.getString(encryptedKey, null))
        assertEquals("[{\"secret\":\"ciao\"}]", store.read())
    }
}
