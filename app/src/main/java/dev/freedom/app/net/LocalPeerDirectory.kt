package dev.freedom.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import dev.freedom.app.contact.FreedomContact
import dev.freedom.app.contact.FreedomNumber
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class PeerPresence(
    val freedomNumber: String,
    val displayName: String,
    val fingerprint: String,
    val host: String,
    val port: Int
)

class LocalPeerDirectory(
    context: Context,
    private val ownContact: FreedomContact,
    private val onPresenceChanged: () -> Unit
) : Closeable {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val peers = ConcurrentHashMap<String, PeerPresence>()
    private val pending = mutableMapOf<String, MutableList<(PeerPresence?) -> Unit>>()

    @Volatile
    private var started = false

    @Volatile
    private var registered = false

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            registered = true
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            registered = false
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.startsWith(SERVICE_TYPE)) return
            if (serviceInfo.serviceName.startsWith(serviceName())) return
            resolve(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val suffix = serviceInfo.serviceName.substringAfter(SERVICE_PREFIX, "")
            if (suffix.isBlank()) return
            val removed = peers.entries.firstOrNull { it.key.endsWith(suffix) }?.key
            if (removed != null) {
                peers.remove(removed)
                mainHandler.post(onPresenceChanged)
            }
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            safelyStopDiscovery()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    fun start() {
        if (started) return
        started = true

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = serviceName()
            serviceType = SERVICE_TYPE
            port = FreedomNode.PORT
            setAttribute(ATTRIBUTE_NUMBER, ownContact.freedomNumber)
            setAttribute(ATTRIBUTE_NAME, ownContact.displayName)
            setAttribute(ATTRIBUTE_FINGERPRINT, ownContact.fingerprint)
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: RuntimeException) {
            started = false
        }
    }

    fun presence(freedomNumber: String): PeerPresence? =
        peers[FreedomNumber.normalize(freedomNumber)]

    fun find(
        freedomNumber: String,
        timeoutMillis: Long = FIND_TIMEOUT_MILLIS,
        callback: (PeerPresence?) -> Unit
    ) {
        val normalized = FreedomNumber.normalize(freedomNumber)
        peers[normalized]?.let {
            mainHandler.post { callback(it) }
            return
        }

        synchronized(pending) {
            pending.getOrPut(normalized, ::mutableListOf).add(callback)
        }
        mainHandler.postDelayed({
            val shouldNotify = synchronized(pending) {
                val callbacks = pending[normalized] ?: return@postDelayed
                val removed = callbacks.remove(callback)
                if (callbacks.isEmpty()) pending.remove(normalized)
                removed
            }
            if (shouldNotify) callback(null)
        }, timeoutMillis)
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        try {
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val number = resolved.attribute(ATTRIBUTE_NUMBER)?.let(FreedomNumber::normalize)
                        ?: return
                    if (!FreedomNumber.isValid(number) || number == ownContact.freedomNumber) return
                    val fingerprint = resolved.attribute(ATTRIBUTE_FINGERPRINT) ?: return
                    val host = resolved.host?.hostAddress ?: return
                    val presence = PeerPresence(
                        freedomNumber = number,
                        displayName = resolved.attribute(ATTRIBUTE_NAME).orEmpty().take(48),
                        fingerprint = fingerprint,
                        host = host,
                        port = resolved.port
                    )
                    peers[number] = presence
                    val callbacks = synchronized(pending) { pending.remove(number).orEmpty() }
                    mainHandler.post {
                        callbacks.forEach { it(presence) }
                        onPresenceChanged()
                    }
                }
            })
        } catch (_: RuntimeException) {
            // Discovery remains active; Android can reject overlapping resolve requests.
        }
    }

    private fun NsdServiceInfo.attribute(key: String): String? =
        attributes[key]?.toString(StandardCharsets.UTF_8)?.trim()?.takeIf(String::isNotEmpty)

    override fun close() {
        if (!started) return
        started = false
        safelyStopDiscovery()
        if (registered) {
            try {
                manager.unregisterService(registrationListener)
            } catch (_: RuntimeException) {
                // Already unregistered by the platform.
            }
        }
        synchronized(pending) {
            pending.values.flatten().forEach { callback -> mainHandler.post { callback(null) } }
            pending.clear()
        }
        peers.clear()
    }

    private fun safelyStopDiscovery() {
        try {
            manager.stopServiceDiscovery(discoveryListener)
        } catch (_: RuntimeException) {
            // Discovery was not active or was already stopped.
        }
    }

    private fun serviceName(): String = SERVICE_PREFIX + ownContact.freedomNumber.takeLast(8)

    private companion object {
        const val SERVICE_TYPE = "_freedom._tcp."
        const val SERVICE_PREFIX = "Freedom-"
        const val ATTRIBUTE_NUMBER = "number"
        const val ATTRIBUTE_NAME = "name"
        const val ATTRIBUTE_FINGERPRINT = "fingerprint"
        const val FIND_TIMEOUT_MILLIS = 8_000L
    }
}
