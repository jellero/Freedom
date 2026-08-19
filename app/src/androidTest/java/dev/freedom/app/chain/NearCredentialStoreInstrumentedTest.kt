package dev.freedom.app.chain

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearCredentialStoreInstrumentedTest {
    @Test
    fun savesAndLoadsCredentialWithAndroidKeystoreIv() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = NearCredentialStore(context)
        val credentials = NearCredentials.parse("alice.testnet", PRIVATE_KEY)

        store.save(credentials)
        val loaded = store.load().getOrThrow()

        assertEquals(credentials.accountId, loaded.accountId)
        assertEquals(credentials.publicKeyString, loaded.publicKeyString)
        assertArrayEquals(credentials.privateSeed, loaded.privateSeed)
    }

    private companion object {
        const val PRIVATE_KEY =
            "ed25519:5zGSi5wkCuWHpVs13xjLb367HyQ1rQiqPSALgEoyD81qUfJ44rvkF6bUjWNsqkMWfWyRpyJRvMPg6EAwFL3Mbdwa"
    }
}
