@file:Suppress("UseKtx")

package dev.freedom.app.chain

import android.content.Context

enum class IdentityNetwork(
    val id: String,
    val displayName: String
) {
    NEAR_TESTNET("near-testnet", "NEAR Testnet"),
    NEAR_MAINNET("near-mainnet", "NEAR Mainnet");

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

    var customRpcEndpoint: String?
        get() = preferences.getString(KEY_CUSTOM_RPC, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val normalized = value?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() }
            if (normalized != null) {
                require(normalized.startsWith("https://")) {
                    "L'endpoint RPC deve usare HTTPS"
                }
            }
            check(preferences.edit().putString(KEY_CUSTOM_RPC, normalized).commit()) {
                "Unable to persist custom RPC endpoint"
            }
        }

    fun rpcEndpoints(): List<String> =
        customRpcEndpoint?.let(::listOf) ?: NearChainAdapter.TESTNET_RPC_ENDPOINTS

    private companion object {
        const val PREFERENCES = "freedom.chain.settings.v1"
        const val KEY_NETWORK = "identity-network"
        const val KEY_CUSTOM_RPC = "custom-rpc"
    }
}
