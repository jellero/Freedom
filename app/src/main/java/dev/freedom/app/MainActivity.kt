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
    private lateinit var sendButton: Button

    private val logLines = ArrayDeque<String>()
    private var nodeStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identity = IdentityStore()
        setContentView(buildUi())

        node = FreedomNode(identity, object : FreedomNode.Listener {
            override fun onStatus(message: String) = ui {
                statusView.text = "Stato: $message"
                appendLog("• $message")
            }

            override fun onConnected(endpoint: String, remoteFingerprint: String, sessionId: String) = ui {
                statusView.text = "Stato: connesso a $endpoint"
                remoteView.text = "Peer remoto:\n$remoteFingerprint\n\nVerifica questa impronta sull'altro dispositivo per escludere MITM nel bootstrap M1."
                sessionView.text = "Sessione effimera: ${sessionId.take(16)}…"
                connectButton.isEnabled = false
                disconnectButton.isEnabled = true
                sendButton.isEnabled = true
                appendLog("🔐 Sessione cifrata stabilita con $endpoint")
            }

            override fun onDisconnected(reason: String) = ui {
                statusView.text = "Stato: $reason"
                remoteView.text = "Peer remoto: —"
                sessionView.text = "Sessione: —"
                connectButton.isEnabled = hasLocalNetworkPermission()
                disconnectButton.isEnabled = false
                sendButton.isEnabled = false
                appendLog("× $reason")
            }

            override fun onMessageSent(messageId: String, text: String) = ui {
                appendLog("Io [${shortId(messageId)}]: $text")
            }

            override fun onMessageReceived(messageId: String, text: String) = ui {
                appendLog("Peer [${shortId(messageId)}]: $text")
            }

            override fun onAck(messageId: String) = ui {
                appendLog("✓ ACK ${shortId(messageId)}")
            }

            override fun onError(message: String) = ui {
                statusView.text = "Stato: errore"
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
            statusView.text = "Stato: accesso alla rete locale negato"
            localAddressView.text = "IP locale: permesso rete locale necessario\nPorta: ${FreedomNode.PORT}"
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
        statusView.text = "Stato: autorizzazione rete locale richiesta"
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
            "IP locale: non rilevato\nPorta: ${FreedomNode.PORT}"
        } else {
            "IP locale: ${addresses.joinToString(", ")}\nPorta: ${FreedomNode.PORT}"
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
            text = "Freedom · Android M1"
            textSize = 27f
            setTextColor(Color.rgb(20, 20, 20))
        })

        root.addView(TextView(this).apply {
            text = "P2P E2EE test client · nessun server di messaggistica"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(section("IDENTITÀ LOCALE"))
        root.addView(TextView(this).apply {
            text = "Fingerprint dispositivo:\n${identity.fingerprint}"
            setTextIsSelectable(true)
            textSize = 13f
            setPadding(0, dp(6), 0, dp(10))
        })

        localAddressView = TextView(this).apply {
            text = "IP locale: rilevamento…\nPorta: ${FreedomNode.PORT}"
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(localAddressView)

        root.addView(section("CONNESSIONE"))
        statusView = TextView(this).apply {
            text = "Stato: inizializzazione…"
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(statusView)

        hostInput = EditText(this).apply {
            hint = "IP peer, es. 192.168.1.42"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(hostInput, matchWidth())

        val connectionButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        connectButton = Button(this).apply {
            text = "Connetti"
            isEnabled = false
            setOnClickListener { node.connect(hostInput.text.toString()) }
        }
        disconnectButton = Button(this).apply {
            text = "Disconnetti"
            isEnabled = false
            setOnClickListener { node.disconnect() }
        }
        connectionButtons.addView(connectButton, weighted())
        connectionButtons.addView(disconnectButton, weighted())
        root.addView(connectionButtons, matchWidth())

        remoteView = TextView(this).apply {
            text = "Peer remoto: —"
            setTextIsSelectable(true)
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(remoteView)

        sessionView = TextView(this).apply {
            text = "Sessione: —"
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(sessionView)

        root.addView(section("MESSAGGI CIFRATI"))
        logView = TextView(this).apply {
            text = "Avvia Freedom su due telefoni collegati alla stessa rete Wi-Fi.\nSul telefono A inserisci l'IP mostrato dal telefono B."
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(244, 244, 244))
            minHeight = dp(180)
        }
        root.addView(logView, matchWidth())

        messageInput = EditText(this).apply {
            hint = "Messaggio"
            maxLines = 4
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(messageInput, matchWidth())

        sendButton = Button(this).apply {
            text = "Invia E2EE"
            isEnabled = false
            setOnClickListener {
                val text = messageInput.text.toString()
                if (text.isNotBlank()) {
                    node.sendText(text)
                    messageInput.setText("")
                }
            }
        }
        root.addView(sendButton, matchWidth())

        root.addView(TextView(this).apply {
            text = "M1 usa IP esplicito e trust-on-first-use con confronto fingerprint. Blockchain, DHT, relay, media e discovery automatico arrivano nei milestone successivi."
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
