package dev.freedom.app.net

import android.content.Context

interface PeerTrustStore {
    fun trustedFingerprint(peerId: String): String?
    fun saveTrustedFingerprint(peerId: String, fingerprint: String)
}

class SharedPreferencesPeerTrustStore(context: Context) : PeerTrustStore {
    private val preferences = context.getSharedPreferences(
        "freedom.peer-trust.m1",
        Context.MODE_PRIVATE
    )

    override fun trustedFingerprint(peerId: String): String? =
        preferences.getString(peerId, null)

    override fun saveTrustedFingerprint(peerId: String, fingerprint: String) {
        check(preferences.edit().putString(peerId, fingerprint).commit()) {
            "Impossibile salvare il fingerprint del peer"
        }
    }
}

class PeerTrustVerifier(private val store: PeerTrustStore) {
    sealed interface Result {
        data object FirstUse : Result
        data object Trusted : Result
        data class Mismatch(val expectedFingerprint: String) : Result
    }

    @Synchronized
    fun evaluate(peerId: String, fingerprint: String): Result {
        val expected = store.trustedFingerprint(peerId) ?: return Result.FirstUse
        return if (expected == fingerprint) {
            Result.Trusted
        } else {
            Result.Mismatch(expected)
        }
    }

    @Synchronized
    fun trustFirstUse(peerId: String, fingerprint: String): Boolean {
        return when (evaluate(peerId, fingerprint)) {
            Result.FirstUse -> {
                store.saveTrustedFingerprint(peerId, fingerprint)
                true
            }
            Result.Trusted -> true
            is Result.Mismatch -> false
        }
    }
}
