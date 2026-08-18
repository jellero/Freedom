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
    fun save(contact: FreedomContact): Boolean {
        val contacts = all().toMutableList()
        val existingIndex = contacts.indexOfFirst { it.freedomNumber == contact.freedomNumber }
        if (existingIndex >= 0) contacts[existingIndex] = contact else contacts += contact
        return persist(contacts)
    }

    @Synchronized
    fun delete(freedomNumber: String): Boolean =
        persist(all().filterNot { it.freedomNumber == freedomNumber })

    fun findByNumber(freedomNumber: String): FreedomContact? =
        all().firstOrNull { it.freedomNumber == FreedomNumber.normalize(freedomNumber) }

    fun findByFingerprint(fingerprint: String): FreedomContact? =
        all().firstOrNull { it.fingerprint.equals(fingerprint, ignoreCase = true) }

    fun findByDeviceId(deviceId: String): FreedomContact? =
        all().firstOrNull { it.deviceId == deviceId }

    private fun persist(contacts: List<FreedomContact>): Boolean {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject()
                    .put("name", contact.displayName)
                    .put("number", contact.freedomNumber)
                    .put("fingerprint", contact.fingerprint)
                    .put("network", contact.networkId)
                    .put("device_id", contact.deviceId)
                    .put("identity_key", contact.identityPublicKey)
                    .put("rendezvous", contact.rendezvousCapability)
                    .put("mailbox_key", contact.mailboxPublicKey)
                    .put("key_epoch", contact.keyEpoch)
            )
        }
        return preferences.edit().putString(KEY_CONTACTS, array.toString()).commit()
    }

    private fun decode(value: JSONObject): FreedomContact? {
        val number = value.optString("number")
        val fingerprint = value.optString("fingerprint")
        if (!FreedomNumber.isValid(number) || fingerprint.isBlank()) return null
        return FreedomContact(
            displayName = value.optString("name").take(48),
            freedomNumber = number,
            fingerprint = fingerprint,
            networkId = value.optString("network", "near-testnet"),
            deviceId = value.optString("device_id").takeIf { it.length == 64 },
            identityPublicKey = value.optString("identity_key").takeIf(String::isNotBlank),
            rendezvousCapability = value.optString("rendezvous").takeIf(String::isNotBlank),
            mailboxPublicKey = value.optString("mailbox_key").takeIf(String::isNotBlank),
            keyEpoch = value.optLong("key_epoch").takeIf { it > 0 }
        )
    }

    private companion object {
        const val PREFERENCES = "freedom.contacts.v1"
        const val KEY_CONTACTS = "contacts"
    }
}
