package dev.freedom.app.net

import dev.freedom.app.crypto.IdentityStore
import dev.freedom.app.protocol.FreedomSession
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class FreedomNode(
    private val identity: IdentityStore,
    private val listener: Listener
) : Closeable {

    interface Listener {
        fun onStatus(message: String)
        fun onConnected(endpoint: String, remoteFingerprint: String, sessionId: String)
        fun onDisconnected(reason: String)
        fun onMessageSent(messageId: String, text: String)
        fun onMessageReceived(messageId: String, text: String)
        fun onAck(messageId: String)
        fun onError(message: String)
    }

    private val executor = Executors.newCachedThreadPool()
    private val lock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activeSession: FreedomSession? = null

    fun start() {
        executor.execute { listenLoop() }
    }

    fun connect(host: String) {
        val target = host.trim()
        if (target.isEmpty()) {
            listener.onError("Inserisci l'IP del peer")
            return
        }

        executor.execute {
            var socket: Socket? = null
            try {
                listener.onStatus("Connessione a $target:$PORT…")
                socket = Socket()
                socket.connect(InetSocketAddress(target, PORT), 10_000)
                val session = FreedomSession.initiate(socket, identity)
                socket = null
                attachSession(session, "$target:$PORT")
            } catch (e: Exception) {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                listener.onError("Connessione fallita: ${safeMessage(e)}")
            }
        }
    }

    fun sendText(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return

        executor.execute {
            val session = activeSession
            if (session == null) {
                listener.onError("Nessun peer connesso")
                return@execute
            }

            try {
                val id = session.sendText(value)
                listener.onMessageSent(id, value)
            } catch (e: Exception) {
                listener.onError("Invio fallito: ${safeMessage(e)}")
                dropSession(session, "Errore di trasporto")
            }
        }
    }

    fun disconnect() {
        val session = synchronized(lock) {
            val current = activeSession
            activeSession = null
            current
        }
        session?.close()
        if (session != null) listener.onDisconnected("Disconnesso")
    }

    private fun listenLoop() {
        try {
            val server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
            serverSocket = server
            listener.onStatus("In ascolto sulla porta $PORT")

            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    if (closed) break else throw e
                }

                executor.execute {
                    try {
                        val remote = socket.remoteSocketAddress.toString()
                        listener.onStatus("Handshake in ingresso da $remote…")
                        val session = FreedomSession.accept(socket, identity)
                        attachSession(session, remote)
                    } catch (e: Exception) {
                        try {
                            socket.close()
                        } catch (_: Exception) {
                        }
                        listener.onError("Handshake in ingresso rifiutato: ${safeMessage(e)}")
                    }
                }
            }
        } catch (e: Exception) {
            if (!closed) listener.onError("Listener non disponibile: ${safeMessage(e)}")
        }
    }

    private fun attachSession(session: FreedomSession, endpoint: String) {
        val previous = synchronized(lock) {
            val old = activeSession
            activeSession = session
            old
        }
        previous?.close()

        listener.onConnected(endpoint, session.remoteFingerprint, session.sessionIdHex)
        executor.execute { readLoop(session) }
    }

    private fun readLoop(session: FreedomSession) {
        try {
            while (!closed && activeSession === session) {
                when (val incoming = session.read()) {
                    is FreedomSession.Incoming.Text -> {
                        listener.onMessageReceived(incoming.messageId, incoming.text)
                        session.sendAck(incoming.messageId)
                    }
                    is FreedomSession.Incoming.Ack -> listener.onAck(incoming.messageId)
                }
            }
        } catch (e: Exception) {
            if (!closed && activeSession === session) {
                dropSession(session, "Sessione terminata: ${safeMessage(e)}")
            }
        }
    }

    private fun dropSession(session: FreedomSession, reason: String) {
        val removed = synchronized(lock) {
            if (activeSession === session) {
                activeSession = null
                true
            } else {
                false
            }
        }
        session.close()
        if (removed) listener.onDisconnected(reason)
    }

    override fun close() {
        closed = true
        disconnect()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdownNow()
    }

    companion object {
        const val PORT = 45_731

        fun localIpv4Addresses(): List<String> {
            val addresses = mutableListOf<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (!network.isUp || network.isLoopback) continue
                    val inetAddresses = network.inetAddresses
                    while (inetAddresses.hasMoreElements()) {
                        val address = inetAddresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            address.hostAddress?.let(addresses::add)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return addresses.distinct()
        }

        private fun safeMessage(error: Throwable): String =
            error.message?.take(160) ?: error.javaClass.simpleName
    }
}
