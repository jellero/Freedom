@file:Suppress("UseKtx")

package dev.freedom.app.contact

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ContactRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<FreedomContact> {
        val raw = preferences.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    decode(value)?.let(::add)
                }
            }.sortedBy { it.displayName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(contact: FreedomContact) {
        val contacts = all().toMutableList()
        val existingIndex = contacts.indexOfFirst { it.freedomNumber == contact.freedomNumber }
        if (existingIndex >= 0) contacts[existingIndex] = contact else contacts += contact
        persist(contacts)
    }

    @Synchronized
    fun delete(freedomNumber: String) {
        persist(all().filterNot { it.freedomNumber == freedomNumber })
    }

    fun findByNumber(freedomNumber: String): FreedomContact? =
        all().firstOrNull { it.freedomNumber == FreedomNumber.normalize(freedomNumber) }

    fun findByFingerprint(fingerprint: String): FreedomContact? =
        all().firstOrNull { it.fingerprint.equals(fingerprint, ignoreCase = true) }

    private fun persist(contacts: List<FreedomContact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject()
                    .put("name", contact.displayName)
                    .put("number", contact.freedomNumber)
                    .put("fingerprint", contact.fingerprint)
                    .put("network", contact.networkId)
            )
        }
        check(preferences.edit().putString(KEY_CONTACTS, array.toString()).commit()) {
            "Unable to persist contacts"
        }
    }

    private fun decode(value: JSONObject): FreedomContact? {
        val number = value.optString("number")
        val fingerprint = value.optString("fingerprint")
        if (!FreedomNumber.isValid(number) || fingerprint.isBlank()) return null
        return FreedomContact(
            displayName = value.optString("name").take(48),
            freedomNumber = number,
            fingerprint = fingerprint,
            networkId = value.optString("network", "near-testnet")
        )
    }

    private companion object {
        const val PREFERENCES = "freedom.contacts.v1"
        const val KEY_CONTACTS = "contacts"
    }
}
