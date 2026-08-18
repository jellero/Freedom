package dev.freedom.app

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.freedom.app.crypto.IdentityStore
import dev.freedom.app.net.FreedomNode
import dev.freedom.app.net.PeerTrustVerifier
import dev.freedom.app.net.SharedPreferencesPeerTrustStore
import java.util.ArrayDeque

class MainActivity : Activity() {
    private lateinit var identity: IdentityStore
    private lateinit var node: FreedomNode

    private lateinit var statusView: TextView
    private lateinit var localAddressView: TextView
    private lateinit var remoteView: TextView
    private lateinit var sessionView: TextView
    private lateinit var hostInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var logView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var trustButton: Button
    private lateinit var rejectButton: Button
    private lateinit var sendButton: Button

    private val logLines = ArrayDeque<String>()
    private var nodeStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identity = IdentityStore()
        setContentView(buildUi())

        node = FreedomNode(
            identity,
            PeerTrustVerifier(SharedPreferencesPeerTrustStore(this)),
            object : FreedomNode.Listener {
            override fun onStatus(message: String) = ui {
                statusView.text = getString(R.string.status_format, message)
                appendLog("• $message")
            }

            override fun onPeerVerificationRequired(
                endpoint: String,
                remoteFingerprint: String,
                sessionId: String
            ) = ui {
                statusView.text = getString(R.string.status_verification_required)
                remoteView.text = getString(
                    R.string.peer_to_verify_format,
                    endpoint,
                    remoteFingerprint
                )
                sessionView.text = getString(
                    R.string.session_pending_format,
                    sessionId.take(16)
                )
                connectButton.isEnabled = false
                disconnectButton.isEnabled = true
                trustButton.isEnabled = true
                rejectButton.isEnabled = true
                sendButton.isEnabled = false
                appendLog("⚠ Verifica manualmente il fingerprint di $endpoint")
            }

            override fun onConnected(endpoint: String, remoteFingerprint: String, sessionId: String) = ui {
                statusView.text = getString(R.string.status_connected_format, endpoint)
                remoteView.text = getString(R.string.peer_verified_format, remoteFingerprint)
                sessionView.text = getString(
                    R.string.session_ephemeral_format,
                    sessionId.take(16)
                )
                connectButton.isEnabled = false
                disconnectButton.isEnabled = true
                trustButton.isEnabled = false
                rejectButton.isEnabled = false
                sendButton.isEnabled = true
                appendLog("🔐 Sessione cifrata e identità verificata con $endpoint")
            }

            override fun onDisconnected(reason: String) = ui {
                statusView.text = getString(R.string.status_format, reason)
                remoteView.text = getString(R.string.remote_peer_empty)
                sessionView.text = getString(R.string.session_empty)
                connectButton.isEnabled = hasLocalNetworkPermission()
                disconnectButton.isEnabled = false
                trustButton.isEnabled = false
                rejectButton.isEnabled = false
                sendButton.isEnabled = false
                appendLog("× $reason")
            }

            override fun onMessageSent(messageId: String, text: String) = ui {
                appendLog("Io [${shortId(messageId)}]: $text")
                if (messageInput.text.toString().trim() == text) {
                    messageInput.setText("")
                }
            }

            override fun onMessageReceived(messageId: String, text: String) = ui {
                appendLog("Peer [${shortId(messageId)}]: $text")
            }

            override fun onAck(messageId: String) = ui {
                appendLog("✓ ACK ${shortId(messageId)}")
            }

            override fun onError(message: String) = ui {
                appendLog("⚠ $message")
            }
        })

        startNodeOrRequestPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCAL_NETWORK) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            appendLog("✓ Accesso alla rete locale autorizzato")
            refreshLocalAddresses()
            startNode()
        } else {
            statusView.text = getString(R.string.status_local_network_denied)
            localAddressView.text = getString(
                R.string.local_network_permission_required_format,
                FreedomNode.PORT
            )
            connectButton.isEnabled = false
            appendLog("⚠ Freedom M1 richiede l'accesso alla rete locale per collegarsi direttamente agli altri peer")
        }
    }

    override fun onDestroy() {
        if (::node.isInitialized) node.close()
        super.onDestroy()
    }

    private fun startNodeOrRequestPermission() {
        if (hasLocalNetworkPermission()) {
            refreshLocalAddresses()
            startNode()
            return
        }

        connectButton.isEnabled = false
        statusView.text = getString(R.string.status_local_network_requested)
        appendLog("• Autorizza la rete locale per permettere connessioni P2P dirette")
        requestPermissions(arrayOf(LOCAL_NETWORK_PERMISSION), REQUEST_LOCAL_NETWORK)
    }

    private fun startNode() {
        if (nodeStarted) return
        nodeStarted = true
        connectButton.isEnabled = true
        node.start()
    }

    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 37) return true
        return checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshLocalAddresses() {
        val addresses = FreedomNode.localIpv4Addresses()
        localAddressView.text = if (addresses.isEmpty()) {
            getString(R.string.local_address_not_detected_format, FreedomNode.PORT)
        } else {
            getString(
                R.string.local_address_format,
                addresses.joinToString(", "),
                FreedomNode.PORT
            )
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = getString(R.string.screen_title)
            textSize = 27f
            setTextColor(Color.rgb(20, 20, 20))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.screen_subtitle)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(section(getString(R.string.section_local_identity)))
        root.addView(TextView(this).apply {
            text = getString(R.string.local_fingerprint_format, identity.fingerprint)
            setTextIsSelectable(true)
            textSize = 13f
            setPadding(0, dp(6), 0, dp(10))
        })

        localAddressView = TextView(this).apply {
            text = getString(R.string.local_address_detecting_format, FreedomNode.PORT)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(localAddressView)

        root.addView(section(getString(R.string.section_connection)))
        statusView = TextView(this).apply {
            text = getString(R.string.status_initializing)
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(statusView)

        hostInput = EditText(this).apply {
            hint = getString(R.string.host_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(hostInput, matchWidth())

        val connectionButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        connectButton = Button(this).apply {
            text = getString(R.string.connect)
            isEnabled = false
            setOnClickListener {
                val host = hostInput.text.toString()
                if (host.isBlank()) {
                    node.connect(host)
                } else {
                    isEnabled = false
                    disconnectButton.isEnabled = true
                    node.connect(host)
                }
            }
        }
        disconnectButton = Button(this).apply {
            text = getString(R.string.disconnect)
            isEnabled = false
            setOnClickListener { node.disconnect() }
        }
        connectionButtons.addView(connectButton, weighted())
        connectionButtons.addView(disconnectButton, weighted())
        root.addView(connectionButtons, matchWidth())

        remoteView = TextView(this).apply {
            text = getString(R.string.remote_peer_empty)
            setTextIsSelectable(true)
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(remoteView)

        sessionView = TextView(this).apply {
            text = getString(R.string.session_empty)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(sessionView)

        val trustButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        trustButton = Button(this).apply {
            text = getString(R.string.trust_peer)
            isEnabled = false
            setOnClickListener {
                isEnabled = false
                rejectButton.isEnabled = false
                node.approvePendingPeer()
            }
        }
        rejectButton = Button(this).apply {
            text = getString(R.string.reject)
            isEnabled = false
            setOnClickListener {
                isEnabled = false
                trustButton.isEnabled = false
                node.rejectPendingPeer()
            }
        }
        trustButtons.addView(trustButton, weighted())
        trustButtons.addView(rejectButton, weighted())
        root.addView(trustButtons, matchWidth())

        root.addView(section(getString(R.string.section_encrypted_messages)))
        logView = TextView(this).apply {
            text = getString(R.string.connection_instructions)
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(244, 244, 244))
            minHeight = dp(180)
        }
        root.addView(logView, matchWidth())

        messageInput = EditText(this).apply {
            hint = getString(R.string.message_hint)
            maxLines = 4
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(messageInput, matchWidth())

        sendButton = Button(this).apply {
            text = getString(R.string.send_e2ee)
            isEnabled = false
            setOnClickListener {
                val text = messageInput.text.toString()
                if (text.isNotBlank()) {
                    node.sendText(text)
                }
            }
        }
        root.addView(sendButton, matchWidth())

        root.addView(TextView(this).apply {
            text = getString(R.string.spike_notice)
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(18), 0, 0)
        })

        return scroll
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.rgb(60, 60, 60))
    }

    private fun appendLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > 60) logLines.removeFirst()
        logView.text = logLines.joinToString("\n")
    }

    private fun shortId(id: String): String = id.take(8)

    private fun ui(block: () -> Unit) {
        runOnUiThread { block() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun weighted() = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    )

    companion object {
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val REQUEST_LOCAL_NETWORK = 1001
    }
}
