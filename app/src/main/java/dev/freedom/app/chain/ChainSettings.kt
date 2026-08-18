@file:Suppress("UseKtx")

package dev.freedom.app.chain

import android.content.Context

enum class IdentityNetwork(
    val id: String,
    val displayName: String,
    val chainOperational: Boolean
) {
    NEAR_TESTNET("near-testnet", "NEAR Testnet", true),
    NEAR_MAINNET("near-mainnet", "NEAR Mainnet", false),
    LOCAL("local", "Solo rete locale", true);

    companion object {
        fun fromId(value: String?): IdentityNetwork =
            entries.firstOrNull { it.id == value } ?: NEAR_TESTNET
    }
}

class ChainSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var network: IdentityNetwork
        get() = IdentityNetwork.fromId(preferences.getString(KEY_NETWORK, null))
        set(value) {
            check(preferences.edit().putString(KEY_NETWORK, value.id).commit()) {
                "Unable to persist identity network"
            }
        }

    private companion object {
        const val PREFERENCES = "freedom.chain.settings.v1"
        const val KEY_NETWORK = "identity-network"
    }
}
