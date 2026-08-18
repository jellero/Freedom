package dev.freedom.app.net

import dev.freedom.app.crypto.DeviceIdentity
import dev.freedom.app.protocol.FreedomSession
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class FreedomNode(
    private val identity: DeviceIdentity,
    private val trustVerifier: PeerTrustVerifier,
    private val listener: Listener
) : Closeable {

    interface Listener {
        fun onStatus(message: String)
        fun onPeerVerificationRequired(
            endpoint: String,
            remoteFingerprint: String,
            sessionId: String
        )
        fun onConnected(endpoint: String, remoteFingerprint: String, sessionId: String)
        fun onDisconnected(reason: String)
        fun onMessageSent(messageId: String, text: String)
        fun onMessageReceived(messageId: String, text: String)
        fun onAck(messageId: String)
        fun onError(message: String)
    }

    private class PendingSession(
        val peerId: String,
        val endpoint: String,
        val session: FreedomSession
    ) {
        @Volatile
        var timeout: ScheduledFuture<*>? = null
    }

    private val workerExecutor = ThreadPoolExecutor(
        CORE_WORKERS,
        MAX_WORKERS,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_QUEUED_TASKS),
        ThreadPoolExecutor.AbortPolicy()
    ).apply {
        allowCoreThreadTimeOut(true)
    }
    private val listenerExecutor = Executors.newSingleThreadExecutor()
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var started = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activeSession: FreedomSession? = null

    private var pendingSession: PendingSession? = null
    private var connectingSocket: Socket? = null
    private var connectionInProgress = false
    private var incomingHandshakes = 0

    fun start() {
        val shouldStart = synchronized(lock) {
            if (closed || started) false else {
                started = true
                true
            }
        }
        if (!shouldStart) return

        try {
            listenerExecutor.execute { listenLoop() }
        } catch (_: RejectedExecutionException) {
            listener.onError("Impossibile avviare il listener")
        }
    }

    fun connect(host: String) {
        val target = host.trim()
        if (target.isEmpty()) {
            listener.onError("Inserisci l'IP del peer")
            return
        }

        val canConnect = synchronized(lock) {
            if (
                closed ||
                connectionInProgress ||
                activeSession != null ||
                pendingSession != null
            ) {
                false
            } else {
                connectionInProgress = true
                true
            }
        }
        if (!canConnect) {
            listener.onError("Una connessione è già attiva o in corso")
            return
        }

        submitWorker(
            onRejected = {
                synchronized(lock) { connectionInProgress = false }
                listener.onError("Nodo occupato: riprova tra poco")
                listener.onDisconnected("Connessione non avviata")
            }
        ) {
            val socket = Socket()
            val registered = synchronized(lock) {
                if (closed || !connectionInProgress) false else {
                    connectingSocket = socket
                    true
                }
            }
            if (!registered) {
                closeQuietly(socket)
                return@submitWorker
            }

            try {
                listener.onStatus("Connessione a $target:$PORT…")
                socket.connect(InetSocketAddress(target, PORT), CONNECT_TIMEOUT_MILLIS)
                val peerId = socket.inetAddress.hostAddress ?: target
                val endpoint = "$peerId:$PORT"
                val session = handshakeWithDeadline(socket) {
                    FreedomSession.initiate(socket, identity)
                }

                val stillCurrent = synchronized(lock) {
                    if (connectingSocket === socket && connectionInProgress) {
                        connectingSocket = null
                        connectionInProgress = false
                        true
                    } else {
                        false
                    }
                }
                if (stillCurrent) {
                    handleEstablishedSession(session, peerId, endpoint)
                } else {
                    session.close()
                }
            } catch (e: Exception) {
                closeQuietly(socket)
                val shouldReport = synchronized(lock) {
                    if (connectingSocket === socket) {
                        connectingSocket = null
                        connectionInProgress = false
                        true
                    } else {
                        false
                    }
                }
                if (shouldReport && !closed) {
                    listener.onError("Connessione fallita: ${safeMessage(e)}")
                    listener.onDisconnected("Connessione non stabilita")
                }
            }
        }
    }

    fun approvePendingPeer() {
        val pending = synchronized(lock) {
            pendingSession.also { pendingSession = null }
        }
        if (pending == null) {
            listener.onError("Nessun peer in attesa di verifica")
            return
        }

        pending.timeout?.cancel(false)
        val trusted = try {
            trustVerifier.trustFirstUse(pending.peerId, pending.session.remoteFingerprint)
        } catch (e: Exception) {
            pending.session.close()
            listener.onError("Salvataggio del fingerprint fallito: ${safeMessage(e)}")
            listener.onDisconnected("Peer non autorizzato")
            return
        }
        if (!trusted) {
            pending.session.close()
            listener.onError("Il fingerprint del peer è cambiato durante la verifica")
            listener.onDisconnected("Peer non autorizzato")
            return
        }
        attachSession(pending.session, pending.endpoint)
    }

    fun rejectPendingPeer() {
        val pending = synchronized(lock) {
            pendingSession.also { pendingSession = null }
        }
        if (pending == null) return

        pending.timeout?.cancel(false)
        pending.session.close()
        listener.onDisconnected("Peer non autorizzato")
    }

    fun sendText(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        if (value.toByteArray(StandardCharsets.UTF_8).size > FreedomSession.MAX_TEXT_BYTES) {
            listener.onError(
                "Messaggio troppo grande: massimo ${FreedomSession.MAX_TEXT_BYTES / 1024} KiB UTF-8"
            )
            return
        }

        val session = activeSession
        if (session == null) {
            listener.onError("Nessun peer connesso")
            return
        }

        submitWorker(
            onRejected = { listener.onError("Nodo occupato: messaggio non inviato") }
        ) {
            if (activeSession !== session) {
                listener.onError("La sessione è cambiata: messaggio non inviato")
                return@submitWorker
            }

            try {
                val id = session.sendText(value)
                listener.onMessageSent(id, value)
            } catch (e: IllegalArgumentException) {
                listener.onError("Messaggio non valido: ${safeMessage(e)}")
            } catch (e: Exception) {
                listener.onError("Invio fallito: ${safeMessage(e)}")
                dropSession(session, "Errore di trasporto")
            }
        }
    }

    fun disconnect() {
        var connectionWasInProgress = false
        val resources = synchronized(lock) {
            connectionWasInProgress = connectionInProgress
            val active = activeSession
            val pending = pendingSession
            val connecting = connectingSocket
            activeSession = null
            pendingSession = null
            connectingSocket = null
            connectionInProgress = false
            Triple(active, pending, connecting)
        }

        resources.second?.timeout?.cancel(false)
        resources.first?.close()
        resources.second?.session?.close()
        closeQuietly(resources.third)
        if (
            connectionWasInProgress ||
            resources.first != null ||
            resources.second != null ||
            resources.third != null
        ) {
            listener.onDisconnected("Disconnesso")
        }
    }

    private fun listenLoop() {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(PORT), ACCEPT_BACKLOG)

            val installed = synchronized(lock) {
                if (closed) false else {
                    serverSocket = server
                    true
                }
            }
            if (!installed) return

            listener.onStatus("In ascolto sulla porta $PORT")
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    if (closed) break else throw e
                }

                if (!reserveIncomingHandshake()) {
                    closeQuietly(socket)
                    continue
                }
                submitIncomingHandshake(socket)
            }
        } catch (e: Exception) {
            if (!closed) listener.onError("Listener non disponibile: ${safeMessage(e)}")
        } finally {
            synchronized(lock) {
                if (serverSocket === server) serverSocket = null
            }
            closeQuietly(server)
        }
    }

    private fun reserveIncomingHandshake(): Boolean = synchronized(lock) {
        if (
            closed ||
            connectionInProgress ||
            activeSession != null ||
            pendingSession != null ||
            incomingHandshakes >= MAX_INCOMING_HANDSHAKES
        ) {
            false
        } else {
            incomingHandshakes++
            true
        }
    }

    private fun submitIncomingHandshake(socket: Socket) {
        val deadline = try {
            timeoutExecutor.schedule(
                { closeQuietly(socket) },
                HANDSHAKE_TIMEOUT_MILLIS.toLong(),
                TimeUnit.MILLISECONDS
            )
        } catch (_: RejectedExecutionException) {
            releaseIncomingHandshake()
            closeQuietly(socket)
            return
        }

        submitWorker(
            onRejected = {
                deadline.cancel(false)
                releaseIncomingHandshake()
                closeQuietly(socket)
            }
        ) {
            try {
                val peerId = socket.inetAddress.hostAddress
                    ?: socket.remoteSocketAddress.toString()
                val endpoint = "$peerId:${socket.port}"
                listener.onStatus("Handshake in ingresso da $endpoint…")
                val session = FreedomSession.accept(socket, identity)
                deadline.cancel(false)
                handleEstablishedSession(session, peerId, endpoint)
            } catch (e: Exception) {
                closeQuietly(socket)
                if (!closed) {
                    listener.onError("Handshake in ingresso rifiutato: ${safeMessage(e)}")
                }
            } finally {
                deadline.cancel(false)
                releaseIncomingHandshake()
            }
        }
    }

    private fun releaseIncomingHandshake() {
        synchronized(lock) {
            if (incomingHandshakes > 0) incomingHandshakes--
        }
    }

    private fun handshakeWithDeadline(
        socket: Socket,
        handshake: () -> FreedomSession
    ): FreedomSession {
        val deadline = timeoutExecutor.schedule(
            { closeQuietly(socket) },
            HANDSHAKE_TIMEOUT_MILLIS.toLong(),
            TimeUnit.MILLISECONDS
        )
        return try {
            handshake()
        } finally {
            deadline.cancel(false)
        }
    }

    private fun handleEstablishedSession(
        session: FreedomSession,
        peerId: String,
        endpoint: String
    ) {
        when (val trust = trustVerifier.evaluate(peerId, session.remoteFingerprint)) {
            PeerTrustVerifier.Result.FirstUse -> queuePeerVerification(
                session,
                peerId,
                endpoint
            )
            PeerTrustVerifier.Result.Trusted -> attachSession(session, endpoint)
            is PeerTrustVerifier.Result.Mismatch -> {
                session.close()
                listener.onError(
                    "Fingerprint cambiato per $peerId. Atteso: ${trust.expectedFingerprint}; " +
                        "ricevuto: ${session.remoteFingerprint}"
                )
                listener.onDisconnected("Identità peer non verificata")
            }
        }
    }

    private fun queuePeerVerification(
        session: FreedomSession,
        peerId: String,
        endpoint: String
    ) {
        val pending = PendingSession(peerId, endpoint, session)
        val accepted = synchronized(lock) {
            if (closed || activeSession != null || pendingSession != null) {
                false
            } else {
                pendingSession = pending
                true
            }
        }
        if (!accepted) {
            session.close()
            return
        }

        pending.timeout = try {
            timeoutExecutor.schedule(
                { expirePendingSession(pending) },
                PEER_APPROVAL_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        } catch (_: RejectedExecutionException) {
            synchronized(lock) {
                if (pendingSession === pending) pendingSession = null
            }
            session.close()
            return
        }

        listener.onPeerVerificationRequired(
            endpoint,
            session.remoteFingerprint,
            session.sessionIdHex
        )
    }

    private fun expirePendingSession(pending: PendingSession) {
        val expired = synchronized(lock) {
            if (pendingSession === pending) {
                pendingSession = null
                true
            } else {
                false
            }
        }
        if (expired) {
            pending.session.close()
            listener.onDisconnected("Verifica del peer scaduta")
        }
    }

    private fun attachSession(session: FreedomSession, endpoint: String) {
        val attached = synchronized(lock) {
            if (closed || activeSession != null || pendingSession != null) {
                false
            } else {
                activeSession = session
                true
            }
        }
        if (!attached) {
            session.close()
            listener.onError("Nuova sessione rifiutata: una sessione è già attiva")
            return
        }

        listener.onConnected(endpoint, session.remoteFingerprint, session.sessionIdHex)
        submitWorker(
            onRejected = { dropSession(session, "Risorse insufficienti per leggere la sessione") }
        ) {
            readLoop(session)
        }
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

    private fun submitWorker(onRejected: () -> Unit, task: () -> Unit) {
        try {
            workerExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            onRejected()
        }
    }

    override fun close() {
        val resources = synchronized(lock) {
            if (closed) return
            closed = true
            val values = arrayOf(
                serverSocket,
                activeSession,
                pendingSession?.session,
                connectingSocket
            )
            serverSocket = null
            activeSession = null
            pendingSession?.timeout?.cancel(false)
            pendingSession = null
            connectingSocket = null
            connectionInProgress = false
            values
        }

        resources.forEach { resource ->
            when (resource) {
                is ServerSocket -> closeQuietly(resource)
                is FreedomSession -> resource.close()
                is Socket -> closeQuietly(resource)
            }
        }
        listenerExecutor.shutdownNow()
        workerExecutor.shutdownNow()
        timeoutExecutor.shutdownNow()
    }

    companion object {
        const val PORT = 45_731

        private const val CORE_WORKERS = 2
        private const val MAX_WORKERS = 4
        private const val MAX_QUEUED_TASKS = 12
        private const val MAX_INCOMING_HANDSHAKES = 3
        private const val ACCEPT_BACKLOG = 16
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val HANDSHAKE_TIMEOUT_MILLIS = 15_000
        private const val PEER_APPROVAL_TIMEOUT_SECONDS = 60L

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

        private fun closeQuietly(resource: Closeable?) {
            try {
                resource?.close()
            } catch (_: Exception) {
            }
        }

        private fun safeMessage(error: Throwable): String =
            error.message?.take(160) ?: error.javaClass.simpleName
    }
}
