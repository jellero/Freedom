@file:Suppress("UseKtx")

package dev.freedom.app.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val messageId: String,
    val contactNumber: String,
    val text: String,
    val outgoing: Boolean,
    val timestampMillis: Long
)

class ChatRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun messages(contactNumber: String): List<ChatMessage> =
        readAll().filter { it.contactNumber == contactNumber }.sortedBy(ChatMessage::timestampMillis)

    @Synchronized
    fun add(message: ChatMessage) {
        val messages = (readAll() + message)
            .distinctBy(ChatMessage::messageId)
            .takeLast(MAX_MESSAGES)
        persist(messages)
    }

    fun hasConversation(contactNumber: String): Boolean = messages(contactNumber).isNotEmpty()

    fun lastMessage(contactNumber: String): ChatMessage? = messages(contactNumber).lastOrNull()

    private fun readAll(): List<ChatMessage> {
        val raw = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val number = item.optString("number")
                    val text = item.optString("text")
                    if (id.isBlank() || number.isBlank() || text.isBlank()) continue
                    add(
                        ChatMessage(
                            messageId = id,
                            contactNumber = number,
                            text = text,
                            outgoing = item.optBoolean("outgoing"),
                            timestampMillis = item.optLong("timestamp")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.messageId)
                    .put("number", message.contactNumber)
                    .put("text", message.text)
                    .put("outgoing", message.outgoing)
                    .put("timestamp", message.timestampMillis)
            )
        }
        check(preferences.edit().putString(KEY_MESSAGES, array.toString()).commit()) {
            "Unable to persist messages"
        }
    }

    private companion object {
        const val PREFERENCES = "freedom.messages.v1"
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 500
    }
}
