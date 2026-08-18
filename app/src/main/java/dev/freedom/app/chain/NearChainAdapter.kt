package dev.freedom.app.chain

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

data class ChainHealth(
    val contractId: String,
    val contractVersion: String,
    val protocolVersion: Int,
    val identityCurve: String,
    val blockHeight: Long,
    val rpcEndpoint: String
)

data class ChainDeviceRecord(
    val deviceId: String,
    val identityPublicKey: ByteArray,
    val keyEpoch: Long,
    val authNonce: Long,
    val active: Boolean,
    val protocolVersion: Int,
    val updatedAtNs: Long
)

interface ChainAdapter {
    fun checkHealth(): Result<ChainHealth>
    fun resolveDevice(deviceId: String): Result<ChainDeviceRecord?>
}

class NearChainAdapter(
    val contractId: String = TESTNET_CONTRACT_ID,
    private val rpcEndpoints: List<String> = TESTNET_RPC_ENDPOINTS,
    private val connectionFactory: (String) -> HttpURLConnection = { endpoint ->
        URL(endpoint).openConnection() as HttpURLConnection
    }
) : ChainAdapter {
    override fun checkHealth(): Result<ChainHealth> = runCatching {
        val response = callView("get_config", JSONObject())
        val config = response.value as? JSONObject
            ?: throw IllegalStateException("Risposta get_config NEAR non valida")
        require(config.getInt("protocol_version") == PROTOCOL_VERSION) {
            "Versione protocollo NEAR non compatibile"
        }
        require(config.getString("identity_curve") == "P-256") {
            "Curva identità NEAR non compatibile"
        }
        ChainHealth(
            contractId = contractId,
            contractVersion = config.getString("contract_version"),
            protocolVersion = config.getInt("protocol_version"),
            identityCurve = config.getString("identity_curve"),
            blockHeight = response.blockHeight,
            rpcEndpoint = response.rpcEndpoint
        )
    }

    override fun resolveDevice(deviceId: String): Result<ChainDeviceRecord?> = runCatching {
        require(DEVICE_ID.matches(deviceId)) { "Device ID non valido" }
        val response = callView(
            "get_device",
            JSONObject().put("device_id", deviceId)
        )
        if (response.value === JSONObject.NULL) return@runCatching null
        val value = response.value as JSONObject
        ChainDeviceRecord(
            deviceId = value.getString("device_id"),
            identityPublicKey = Base64.getDecoder().decode(value.getString("identity_public_key")),
            keyEpoch = value.getString("key_epoch").toLong(),
            authNonce = value.getString("auth_nonce").toLong(),
            active = value.getString("status") == "active",
            protocolVersion = value.getInt("protocol_version"),
            updatedAtNs = value.getString("updated_at_ns").toLong()
        )
    }

    fun storageBalance(accountId: String): Result<String> = runCatching {
        val response = callView(
            "storage_balance_of",
            JSONObject().put("account_id", accountId)
        )
        (response.value as JSONObject).getString("available")
    }

    private fun callView(methodName: String, arguments: JSONObject): ViewResponse {
        var lastFailure: Exception? = null
        for (endpoint in rpcEndpoints) {
            try {
                return callView(endpoint, methodName, arguments)
            } catch (failure: Exception) {
                lastFailure = failure
            }
        }
        throw IllegalStateException("Nessun endpoint RPC NEAR raggiungibile", lastFailure)
    }

    private fun callView(
        endpoint: String,
        methodName: String,
        arguments: JSONObject
    ): ViewResponse {
        val argsBase64 = Base64.getEncoder().encodeToString(
            arguments.toString().toByteArray(StandardCharsets.UTF_8)
        )
        val request = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", "freedom-android")
            .put("method", "query")
            .put(
                "params",
                JSONObject()
                    .put("request_type", "call_function")
                    .put("finality", "final")
                    .put("account_id", contractId)
                    .put("method_name", methodName)
                    .put("args_base64", argsBase64)
            )

        val connection = connectionFactory(endpoint).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(request.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) { "RPC NEAR HTTP $status" }

            val envelope = JSONObject(body)
            if (envelope.has("error")) {
                throw IllegalStateException(envelope.getJSONObject("error").optString("message", "Errore RPC NEAR"))
            }
            val result = envelope.getJSONObject("result")
            val bytes = result.getJSONArray("result").toByteArray()
            val decoded = String(bytes, StandardCharsets.UTF_8)
            return ViewResponse(
                value = if (decoded == "null") JSONObject.NULL else JSONObject(decoded),
                blockHeight = result.getLong("block_height"),
                rpcEndpoint = endpoint
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class ViewResponse(
        val value: Any,
        val blockHeight: Long,
        val rpcEndpoint: String
    )

    private fun JSONArray.toByteArray(): ByteArray =
        ByteArray(length()) { index -> getInt(index).toByte() }

    companion object {
        const val TESTNET_CONTRACT_ID = "freedom-registry-jellero.testnet"
        const val PROTOCOL_VERSION = 1

        val TESTNET_RPC_ENDPOINTS = listOf(
            "https://rpc.testnet.near.org",
            "https://test.rpc.fastnear.com"
        )

        private val DEVICE_ID = Regex("[0-9a-f]{64}")
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
    }
}
