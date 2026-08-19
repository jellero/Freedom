package dev.freedom.app.chain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class NearDirectClientTest {
    @Test
    fun signedFunctionCallMatchesNearApiJsReferenceFixture() {
        val requests = mutableListOf<ByteArrayOutputStream>()
        val responses = ArrayDeque(
            listOf(
                """{"jsonrpc":"2.0","result":{"nonce":41,"block_hash":"US517G5965aydkZ46HS38QLi7UQiSojurfbQfKCELFx"}}""",
                """{"jsonrpc":"2.0","result":{"final_execution_status":"FINAL","status":{"SuccessValue":""},"transaction":{"hash":"fixture-hash"},"receipts_outcome":[]}}"""
            )
        )
        val client = NearDirectClient(
            contractId = "freedom-registry-jellero.testnet",
            rpcEndpoints = listOf("https://rpc.test"),
            connectionFactory = { endpoint ->
                val request = ByteArrayOutputStream()
                requests += request
                FakeConnection(URL(endpoint), responses.removeFirst(), request)
            }
        )
        val credentials = NearCredentials.parse("alice.testnet", PRIVATE_KEY)

        val result = client.callFunction(
            credentials,
            "register_device",
            JSONObject().put("device_id", "00".repeat(32))
        ).getOrThrow()

        assertEquals("fixture-hash", result.transactionHash)
        val sendRequest = JSONObject(requests[1].toString(StandardCharsets.UTF_8.name()))
        assertEquals(
            EXPECTED_SIGNED_TRANSACTION,
            sendRequest.getJSONObject("params").getString("signed_tx_base64")
        )
    }

    @Test
    fun rejectsFinalTransactionWhenContractExecutionFailed() {
        val responses = ArrayDeque(
            listOf(
                """{"jsonrpc":"2.0","result":{"nonce":41,"block_hash":"US517G5965aydkZ46HS38QLi7UQiSojurfbQfKCELFx"}}""",
                """{"jsonrpc":"2.0","result":{"final_execution_status":"FAILURE","status":{"Failure":{"ActionError":{"kind":{"FunctionCallError":{"ExecutionError":"Smart contract panicked: Invalid authorization nonce"}}}}},"transaction":{"hash":"failed-hash"}}}"""
            )
        )
        val client = NearDirectClient(
            contractId = "freedom-registry-jellero.testnet",
            rpcEndpoints = listOf("https://rpc.test"),
            connectionFactory = { endpoint ->
                FakeConnection(URL(endpoint), responses.removeFirst(), ByteArrayOutputStream())
            }
        )

        val error = client.callFunction(
            NearCredentials.parse("alice.testnet", PRIVATE_KEY),
            "register_device",
            JSONObject().put("device_id", "00".repeat(32))
        ).exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("Invalid authorization nonce"))
    }

    private class FakeConnection(
        url: URL,
        private val response: String,
        private val request: ByteArrayOutputStream
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getOutputStream() = request
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
    }

    private companion object {
        const val PRIVATE_KEY =
            "ed25519:5zGSi5wkCuWHpVs13xjLb367HyQ1rQiqPSALgEoyD81qUfJ44rvkF6bUjWNsqkMWfWyRpyJRvMPg6EAwFL3Mbdwa"
        const val EXPECTED_SIGNED_TRANSACTION =
            "DQAAAGFsaWNlLnRlc3RuZXQA3HAUssZuQ0eaVHFwENtGzSB47R8/cmu8/0vhSnzJRP0qAAAAAAAAACAAAABmcmVlZG9tLXJlZ2lzdHJ5LWplbGxlcm8udGVzdG5ldAcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHAQAAAAIPAAAAcmVnaXN0ZXJfZGV2aWNlUAAAAHsiZGV2aWNlX2lkIjoiMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMCJ9AOBX60gbAAAAAAAAAAAAAAAAAAAAAAAAAHzYTqKnYrWA4M5ryTne2KHEYj1b6D8ExEwjvSyiVdQvchdGpWCxuDyK1a32iO3qvjnbjALTSnCzSEvzAYpHLw0="
    }
}
