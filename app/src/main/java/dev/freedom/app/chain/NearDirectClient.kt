package dev.freedom.app.chain

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class NearAccessKeyPermission(
    val receiverId: String,
    val methodNames: Set<String>,
    val allowance: String?
)

data class NearTransactionResult(val transactionHash: String)

class NearDirectClient(
    private val contractId: String,
    private val rpcEndpoints: List<String>,
    private val connectionFactory: (String) -> HttpURLConnection = { endpoint ->
        URL(endpoint).openConnection() as HttpURLConnection
    }
) {
    fun validateRestrictedKey(credentials: NearCredentials): Result<NearAccessKeyPermission> =
        runCatching {
            val accessKey = queryAccessKey(credentials)
            val permissionValue = accessKey.result.get("permission")
            require(permissionValue is JSONObject) {
                "Chiave Full Access rifiutata: crea una Function-Call key dedicata a Freedom"
            }
            val functionCall = permissionValue.optJSONObject("FunctionCall")
                ?: throw IllegalArgumentException("Permessi della chiave NEAR non riconosciuti")
            val receiver = functionCall.getString("receiver_id")
            require(receiver == contractId) {
                "La chiave non è limitata al contratto $contractId"
            }
            val methods = functionCall.getJSONArray("method_names").toStringSet()
            require(methods.isEmpty() || methods.containsAll(REQUIRED_METHODS)) {
                "La chiave non autorizza tutti i metodi Freedom necessari"
            }
            NearAccessKeyPermission(
                receiverId = receiver,
                methodNames = methods,
                allowance = functionCall.optString("allowance").takeIf { it.isNotBlank() && it != "null" }
            )
        }

    fun callFunction(
        credentials: NearCredentials,
        methodName: String,
        arguments: JSONObject,
        gas: Long = DEFAULT_GAS
    ): Result<NearTransactionResult> = runCatching {
        require(methodName in REQUIRED_METHODS) { "Metodo Freedom non autorizzato" }
        require(gas in 1..MAX_GAS) { "Quantità di gas NEAR non valida" }
        val accessKey = queryAccessKey(credentials)
        val nonce = accessKey.result.getLong("nonce") + 1
        val blockHash = Base58.decode(accessKey.result.getString("block_hash"))
        require(blockHash.size == 32) { "Block hash NEAR non valido" }

        val transaction = BorshWriter().apply {
            writeString(credentials.accountId)
            writeByte(ED25519_KEY_TYPE)
            writeBytes(credentials.publicKey)
            writeU64(nonce)
            writeString(contractId)
            writeBytes(blockHash)
            writeU32(1)
            writeByte(FUNCTION_CALL_ACTION)
            writeString(methodName)
            writeByteVector(arguments.toString().toByteArray(StandardCharsets.UTF_8))
            writeU64(gas)
            writeU128Zero()
        }.toByteArray()
        val transactionHash = MessageDigest.getInstance("SHA-256").digest(transaction)
        val signature = Ed25519Signer().run {
            init(true, Ed25519PrivateKeyParameters(credentials.privateSeed, 0))
            update(transactionHash, 0, transactionHash.size)
            generateSignature()
        }
        val signedTransaction = transaction + byteArrayOf(ED25519_KEY_TYPE) + signature
        val signedBase64 = Base64.getEncoder().encodeToString(signedTransaction)
        val response = rpcWithFallback(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", "freedom-android")
                .put("method", "send_tx")
                .put(
                    "params",
                    JSONObject()
                        .put("signed_tx_base64", signedBase64)
                        .put("wait_until", "FINAL")
                )
        ).result
        val hash = response.optJSONObject("transaction")?.optString("hash")
            ?: Base58.encode(transactionHash)
        NearTransactionResult(hash)
    }

    private fun queryAccessKey(credentials: NearCredentials): RpcResponse = rpcWithFallback(
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", "freedom-android")
            .put("method", "query")
            .put(
                "params",
                JSONObject()
                    .put("request_type", "view_access_key")
                    .put("finality", "final")
                    .put("account_id", credentials.accountId)
                    .put("public_key", credentials.publicKeyString)
            )
    )

    private fun rpcWithFallback(request: JSONObject): RpcResponse {
        var lastFailure: Exception? = null
        for (endpoint in rpcEndpoints) {
            try {
                return rpc(endpoint, request)
            } catch (failure: Exception) {
                lastFailure = failure
            }
        }
        throw IllegalStateException("Nessun endpoint RPC NEAR raggiungibile", lastFailure)
    }

    private fun rpc(endpoint: String, request: JSONObject): RpcResponse {
        val connection = connectionFactory(endpoint).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(request.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) { "RPC NEAR HTTP $status" }
            val envelope = JSONObject(body)
            if (envelope.has("error")) {
                val error = envelope.getJSONObject("error")
                val cause = error.optJSONObject("cause")?.optString("name")
                throw IllegalStateException(cause ?: error.optString("message", "Errore RPC NEAR"))
            }
            return RpcResponse(envelope.getJSONObject("result"), endpoint)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.toStringSet(): Set<String> =
        buildSet { repeat(length()) { add(getString(it)) } }

    private data class RpcResponse(val result: JSONObject, val endpoint: String)

    private class BorshWriter {
        private val output = ByteArrayOutputStream()

        fun writeByte(value: Byte) = output.write(value.toInt())
        fun writeBytes(value: ByteArray) = output.write(value)
        fun writeString(value: String) = writeByteVector(value.toByteArray(StandardCharsets.UTF_8))
        fun writeByteVector(value: ByteArray) {
            writeU32(value.size)
            writeBytes(value)
        }
        fun writeU32(value: Int) = writeBytes(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        )
        fun writeU64(value: Long) = writeBytes(
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        )
        fun writeU128Zero() = writeBytes(ByteArray(16))
        fun toByteArray(): ByteArray = output.toByteArray()
    }

    companion object {
        val REQUIRED_METHODS = setOf(
            "register_device",
            "publish_contact",
            "send_message"
        )
        private const val DEFAULT_GAS = 30_000_000_000_000L
        private const val MAX_GAS = 300_000_000_000_000L
        private const val ED25519_KEY_TYPE: Byte = 0
        private const val FUNCTION_CALL_ACTION: Byte = 2
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
