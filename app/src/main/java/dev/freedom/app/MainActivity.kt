package dev.freedom.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.freedom.app.chain.ChainSettings
import dev.freedom.app.chain.ChainHealth
import dev.freedom.app.chain.IdentityNetwork
import dev.freedom.app.chain.NearChainAdapter
import dev.freedom.app.chat.ChatMessage
import dev.freedom.app.chat.ChatRepository
import dev.freedom.app.contact.ContactRepository
import dev.freedom.app.contact.FreedomContact
import dev.freedom.app.contact.FreedomContactCodec
import dev.freedom.app.contact.FreedomNumber
import dev.freedom.app.contact.QrCodeRenderer
import dev.freedom.app.crypto.IdentityStore
import dev.freedom.app.diagnostics.CrashReporter
import dev.freedom.app.net.FreedomNode
import dev.freedom.app.net.LocalPeerDirectory
import dev.freedom.app.net.PeerPresence
import dev.freedom.app.net.PeerTrustVerifier
import dev.freedom.app.net.SharedPreferencesPeerTrustStore
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private enum class Screen { CHATS, CONTACTS, SETTINGS, IDENTITY, CHAT }

    private lateinit var identity: IdentityStore
    private lateinit var contacts: ContactRepository
    private lateinit var chats: ChatRepository
    private lateinit var chainSettings: ChainSettings
    private lateinit var nearChain: NearChainAdapter
    private lateinit var node: FreedomNode

    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var appRoot: View

    private var directory: LocalPeerDirectory? = null
    private var currentScreen = Screen.CHATS
    private var currentContact: FreedomContact? = null
    private var connectedContactNumber: String? = null
    private var activeRemoteFingerprint: String? = null
    private var communicationStarted = false
    private var chatStatusRes = R.string.chat_waiting
    private var chainHealth: ChainHealth? = null
    private var chainHealthError: String? = null
    private var chainHealthLoading = false
    private var systemBottomInset = 0
    private var imeBottomInset = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val chainExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.record(this, "main_activity_on_create")
        identity = IdentityStore()
        contacts = ContactRepository(this)
        chats = ChatRepository(this)
        chainSettings = ChainSettings(this)
        nearChain = NearChainAdapter()

        buildChrome()
        node = buildNode()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.IDENTITY, Screen.CHAT -> showChats()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
        showChats()
        startCommunicationOrRequestPermission()
        mainHandler.postDelayed(::showCrashReportIfPresent, CRASH_REPORT_DELAY_MILLIS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCAL_NETWORK) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCommunication()
        } else {
            showMessage(getString(R.string.local_permission_denied))
        }
    }

    override fun onDestroy() {
        CrashReporter.record(this, "main_activity_on_destroy")
        directory?.close()
        if (::node.isInitialized) node.close()
        chainExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun ownContact(): FreedomContact = FreedomContact(
        displayName = getString(R.string.contact_default_name),
        freedomNumber = FreedomNumber.fromPublicKey(identity.publicKey),
        fingerprint = identity.fingerprint,
        networkId = chainSettings.network.id
    )

    private fun buildChrome() {
        val toolbarHeight = dp(72)
        val navigationHeight = dp(80)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.freedom_surface))
        }

        toolbar = MaterialToolbar(this).apply {
            setBackgroundColor(color(R.color.freedom_surface))
            setTitleTextColor(color(R.color.freedom_on_surface))
            setSubtitleTextColor(color(R.color.freedom_on_surface_variant))
            setTitleTextAppearance(
                this@MainActivity,
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            )
            contentInsetStartWithNavigation = dp(10)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(MATCH, toolbarHeight))

        content = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.freedom_surface))
        }
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        bottomNavigation = BottomNavigationView(this).apply {
            inflateMenu(R.menu.main_navigation)
            setBackgroundColor(color(R.color.freedom_surface))
            itemActiveIndicatorColor = getColorStateList(R.color.freedom_primary_container)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_chats -> showChats()
                    R.id.navigation_contacts -> showContacts()
                    R.id.navigation_settings -> showSettings()
                    else -> return@setOnItemSelectedListener false
                }
                true
            }
        }
        root.addView(bottomNavigation, LinearLayout.LayoutParams(MATCH, navigationHeight))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            systemBottomInset = systemBars.bottom
            imeBottomInset = ime.bottom

            toolbar.setPadding(
                toolbar.paddingLeft,
                systemBars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )
            toolbar.layoutParams = (toolbar.layoutParams as LinearLayout.LayoutParams).apply {
                height = toolbarHeight + systemBars.top
            }

            bottomNavigation.setPadding(
                bottomNavigation.paddingLeft,
                bottomNavigation.paddingTop,
                bottomNavigation.paddingRight,
                systemBars.bottom
            )
            bottomNavigation.layoutParams =
                (bottomNavigation.layoutParams as LinearLayout.LayoutParams).apply {
                    height = navigationHeight + systemBars.bottom
                }

            content.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                if (bottomNavigation.visibility == View.GONE) {
                    maxOf(systemBars.bottom, ime.bottom)
                } else {
                    0
                }
            )

            WindowInsetsCompat.CONSUMED
        }

        setContentView(root)
        appRoot = root
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildNode(): FreedomNode = FreedomNode(
        identity,
        PeerTrustVerifier(SharedPreferencesPeerTrustStore(this)),
        object : FreedomNode.Listener {
            override fun onStatus(message: String) = ui {
                if (currentScreen == Screen.CHAT) showMessage(message)
            }

            override fun onPeerVerificationRequired(
                endpoint: String,
                remoteFingerprint: String,
                sessionId: String
            ) = ui {
                val known = contacts.findByFingerprint(remoteFingerprint)
                val expected = currentContact
                when {
                    known != null -> {
                        currentContact = known
                        node.approvePendingPeer()
                    }
                    expected != null && expected.fingerprint.equals(remoteFingerprint, ignoreCase = true) -> {
                        node.approvePendingPeer()
                    }
                    expected != null -> {
                        node.rejectPendingPeer()
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.identity_mismatch_title)
                            .setMessage(R.string.identity_mismatch_body)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    else -> confirmUnknownPeer(remoteFingerprint)
                }
            }

            override fun onConnected(
                endpoint: String,
                remoteFingerprint: String,
                sessionId: String
            ) = ui {
                activeRemoteFingerprint = remoteFingerprint
                val contact = contacts.findByFingerprint(remoteFingerprint) ?: currentContact
                currentContact = contact
                connectedContactNumber = contact?.freedomNumber
                chatStatusRes = R.string.chat_connected
                if (currentScreen == Screen.CHAT && contact != null) renderChat(contact)
                else showMessage(getString(R.string.chat_connected))
            }

            override fun onDisconnected(reason: String) = ui {
                connectedContactNumber = null
                activeRemoteFingerprint = null
                chatStatusRes = R.string.chat_offline
                currentContact?.let { if (currentScreen == Screen.CHAT) renderChat(it) }
            }

            override fun onMessageSent(messageId: String, text: String) = ui {
                activeContactForMessage()?.let { contact ->
                    chats.add(
                        ChatMessage(
                            messageId = messageId,
                            contactNumber = contact.freedomNumber,
                            text = text,
                            outgoing = true,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    if (currentScreen == Screen.CHAT) renderChat(contact)
                }
            }

            override fun onMessageReceived(messageId: String, text: String) = ui {
                activeContactForMessage()?.let { contact ->
                    chats.add(
                        ChatMessage(
                            messageId = messageId,
                            contactNumber = contact.freedomNumber,
                            text = text,
                            outgoing = false,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    if (currentScreen == Screen.CHAT) renderChat(contact)
                    else showMessage("${contact.displayName}: $text")
                }
            }

            override fun onAck(messageId: String) = Unit

            override fun onError(message: String) = ui { showMessage(message) }
        }
    )

    private fun activeContactForMessage(): FreedomContact? =
        activeRemoteFingerprint?.let(contacts::findByFingerprint) ?: currentContact

    private fun startCommunicationOrRequestPermission() {
        if (hasLocalNetworkPermission()) {
            startCommunication()
            return
        }
        showMessage(getString(R.string.local_permission_explanation))
        requestPermissions(arrayOf(LOCAL_NETWORK_PERMISSION), REQUEST_LOCAL_NETWORK)
    }

    private fun startCommunication() {
        if (!communicationStarted) {
            communicationStarted = true
            node.start()
        }
        restartDirectory()
    }

    private fun restartDirectory() {
        if (!hasLocalNetworkPermission()) return
        directory?.close()
        directory = LocalPeerDirectory(this, ownContact()) {
            when (currentScreen) {
                Screen.CHATS -> showChats()
                Screen.CONTACTS -> showContacts()
                else -> Unit
            }
        }.also(LocalPeerDirectory::start)
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun showChats() {
        currentScreen = Screen.CHATS
        prepareTopLevel(
            title = getString(R.string.home_title),
            subtitle = getString(R.string.home_subtitle),
            selectedItem = R.id.navigation_chats
        )

        val frame = FrameLayout(this)
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val column = pageColumn(bottomPadding = 96)
        scroll.addView(column)

        column.addView(identitySummaryCard())
        column.addView(sectionLabel(R.string.recent_conversations), margins(top = 28))

        val contactList = contacts.all()
        if (contactList.isEmpty()) {
            column.addView(
                emptyState(R.string.empty_chats_title, R.string.empty_chats_body),
                margins(top = 12)
            )
        } else {
            contactList.forEach { contact -> column.addView(contactCard(contact)) }
        }

        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(addContactFab(), FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(20)
            bottomMargin = dp(20)
        })
        setPage(frame)
    }

    private fun showContacts() {
        currentScreen = Screen.CONTACTS
        prepareTopLevel(
            title = getString(R.string.contacts_title),
            subtitle = getString(R.string.contacts_subtitle),
            selectedItem = R.id.navigation_contacts
        )

        val frame = FrameLayout(this)
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val column = pageColumn(bottomPadding = 96)
        scroll.addView(column)

        val share = actionCard(
            title = getString(R.string.identity_title),
            subtitle = FreedomNumber.format(ownContact().freedomNumber),
            icon = R.drawable.ic_qr
        ) { showIdentity() }
        column.addView(share)

        val contactList = contacts.all()
        if (contactList.isEmpty()) {
            column.addView(
                emptyState(R.string.empty_contacts_title, R.string.empty_contacts_body),
                margins(top = 20)
            )
        } else {
            contactList.forEach { contact -> column.addView(contactCard(contact, allowDelete = true)) }
        }

        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(addContactFab(), FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(20)
            bottomMargin = dp(20)
        })
        setPage(frame)
    }

    private fun showSettings() {
        currentScreen = Screen.SETTINGS
        prepareTopLevel(
            title = getString(R.string.settings_title),
            subtitle = getString(R.string.settings_subtitle),
            selectedItem = R.id.navigation_settings
        )

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val column = pageColumn()
        scroll.addView(column)

        column.addView(sectionLabel(R.string.identity_network_section))
        column.addView(networkCard(IdentityNetwork.NEAR_TESTNET, R.string.near_testnet_description))
        column.addView(networkCard(IdentityNetwork.NEAR_MAINNET, R.string.near_mainnet_description))
        column.addView(networkCard(IdentityNetwork.LOCAL, R.string.local_only_description))

        column.addView(sectionLabel(R.string.chain_status_section), margins(top = 28))
        val currentHealth = chainHealth
        val statusTitle: String
        val statusBody: String
        val statusColor: Int
        when {
            chainSettings.network == IdentityNetwork.LOCAL -> {
                statusTitle = getString(R.string.chain_local_mode)
                statusBody = getString(R.string.chain_local_mode_body)
                statusColor = color(R.color.freedom_outline)
            }
            chainSettings.network == IdentityNetwork.NEAR_MAINNET -> {
                statusTitle = getString(R.string.chain_not_connected)
                statusBody = getString(R.string.chain_mainnet_unavailable)
                statusColor = color(R.color.freedom_error)
            }
            currentHealth != null -> {
                statusTitle = getString(R.string.chain_connected)
                statusBody = getString(
                    R.string.chain_connected_body,
                    currentHealth.contractId,
                    currentHealth.contractVersion,
                    currentHealth.blockHeight
                )
                statusColor = color(R.color.freedom_success)
            }
            chainHealthError != null -> {
                statusTitle = getString(R.string.chain_connection_error)
                statusBody = getString(R.string.chain_connection_error_body)
                statusColor = color(R.color.freedom_error)
            }
            else -> {
                statusTitle = getString(R.string.chain_checking)
                statusBody = getString(R.string.chain_checking_body)
                statusColor = color(R.color.freedom_primary)
            }
        }
        column.addView(
            informationCard(
                title = statusTitle,
                body = statusBody,
                accentColor = statusColor
            )
        )

        column.addView(sectionLabel(R.string.network_costs_section), margins(top = 28))
        column.addView(
            informationCard(
                title = getString(R.string.sponsored_fees),
                body = getString(R.string.sponsored_fees_body),
                badge = getString(R.string.recommended),
                accentColor = color(R.color.freedom_success)
            )
        )
        column.addView(
            informationCard(
                title = getString(R.string.personal_wallet),
                body = getString(R.string.personal_wallet_body),
                accentColor = color(R.color.freedom_outline)
            )
        )

        column.addView(sectionLabel(R.string.security_section), margins(top = 28))
        column.addView(
            informationCard(
                title = getString(R.string.app_name),
                body = getString(R.string.security_body),
                accentColor = color(R.color.freedom_primary)
            )
        )

        column.addView(sectionLabel(R.string.diagnostics_section), margins(top = 28))
        column.addView(
            actionCard(
                title = getString(R.string.share_diagnostic_log),
                subtitle = getString(R.string.diagnostic_log_body),
                icon = R.drawable.ic_send
            ) { shareDiagnosticLog() },
            margins(top = 10)
        )
        setPage(scroll)
        refreshChainHealthIfNeeded()
    }

    private fun refreshChainHealthIfNeeded() {
        if (chainSettings.network != IdentityNetwork.NEAR_TESTNET) return
        if (chainHealth != null || chainHealthError != null || chainHealthLoading) return
        chainHealthLoading = true
        chainExecutor.execute {
            val result = nearChain.checkHealth()
            ui {
                chainHealthLoading = false
                chainHealth = result.getOrNull()
                chainHealthError = result.exceptionOrNull()?.message
                if (currentScreen == Screen.SETTINGS) showSettings()
            }
        }
    }

    private fun showIdentity() {
        currentScreen = Screen.IDENTITY
        prepareDetail(title = getString(R.string.identity_title))
        toolbar.menu.add(Menu.NONE, ACTION_SHARE, Menu.NONE, R.string.share_contact).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_send)
            setOnMenuItemClickListener {
                shareOwnContact()
                true
            }
        }

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val column = pageColumn().apply { gravity = Gravity.CENTER_HORIZONTAL }
        scroll.addView(column)

        column.addView(bodyText(R.string.identity_subtitle, centered = true), margins(bottom = 18))

        val qrCard = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(android.graphics.Color.WHITE)
            setContentPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val qrValue = FreedomContactCodec.encode(ownContact())
        qrCard.addView(ImageView(this).apply {
            setImageBitmap(QrCodeRenderer.render(qrValue, 720))
            contentDescription = getString(R.string.identity_title)
            adjustViewBounds = true
        }, ViewGroup.LayoutParams(dp(270), dp(270)))
        column.addView(qrCard, centeredParams(dp(306), WRAP))

        column.addView(sectionLabel(R.string.freedom_number_label), margins(top = 24))
        column.addView(TextView(this).apply {
            text = FreedomNumber.format(ownContact().freedomNumber)
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(color(R.color.freedom_on_surface))
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
        }, margins(top = 8))
        column.addView(TextView(this).apply {
            text = getString(R.string.network_label_format, chainSettings.network.displayName)
            textSize = 14f
            setTextColor(color(R.color.freedom_on_surface_variant))
            gravity = Gravity.CENTER
        }, margins(top = 8, bottom = 20))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.copy_number)
            setOnClickListener { copyOwnNumber() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        buttons.addView(MaterialButton(this).apply {
            text = getString(R.string.share_contact)
            setOnClickListener { shareOwnContact() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        column.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        setPage(scroll)
    }

    private fun openChat(contact: FreedomContact) {
        currentContact = contact
        currentScreen = Screen.CHAT
        chatStatusRes = if (connectedContactNumber == contact.freedomNumber) {
            R.string.chat_connected
        } else {
            R.string.chat_connecting
        }
        renderChat(contact)

        if (connectedContactNumber != null && connectedContactNumber != contact.freedomNumber) {
            node.disconnect()
            mainHandler.postDelayed({ connectToContact(contact) }, 250)
        } else if (connectedContactNumber != contact.freedomNumber) {
            connectToContact(contact)
        }
    }

    private fun renderChat(contact: FreedomContact) {
        currentScreen = Screen.CHAT
        prepareDetail(title = contact.displayName, subtitle = getString(chatStatusRes))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }

        val messageScroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }
        val messagesColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val messages = chats.messages(contact.freedomNumber)
        if (messages.isEmpty()) {
            messagesColumn.addView(
                emptyState(R.string.chat_waiting, R.string.empty_chats_body),
                margins(top = 40)
            )
        } else {
            messages.forEach { messagesColumn.addView(messageBubble(it)) }
        }
        messageScroll.addView(messagesColumn)
        root.addView(messageScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        if (connectedContactNumber != contact.freedomNumber) {
            root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.connect_contact)
                setOnClickListener { connectToContact(contact) }
            }, LinearLayout.LayoutParams(MATCH, dp(52)).apply { bottomMargin = dp(8) })
        }

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val inputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputFilledStyle).apply {
            hint = getString(R.string.message_placeholder)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor = color(R.color.freedom_surface_variant)
        }
        val input = TextInputEditText(inputLayout.context).apply {
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        inputLayout.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        composer.addView(inputLayout, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
        composer.addView(MaterialButton(this).apply {
            text = ""
            setIconResource(R.drawable.ic_send)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            isEnabled = connectedContactNumber == contact.freedomNumber
            contentDescription = getString(R.string.send_e2ee)
            insetTop = 0
            insetBottom = 0
            setOnClickListener {
                val value = input.text?.toString().orEmpty().trim()
                if (value.isNotEmpty()) node.sendText(value)
            }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(composer, LinearLayout.LayoutParams(MATCH, WRAP))
        setPage(root)

        mainHandler.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun connectToContact(contact: FreedomContact) {
        if (!hasLocalNetworkPermission()) {
            startCommunicationOrRequestPermission()
            return
        }
        currentContact = contact
        chatStatusRes = R.string.chat_connecting
        if (currentScreen == Screen.CHAT) renderChat(contact)
        directory?.find(contact.freedomNumber) { presence ->
            if (currentContact?.freedomNumber != contact.freedomNumber) return@find
            if (presence == null) {
                chatStatusRes = R.string.chat_offline
                if (currentScreen == Screen.CHAT) renderChat(contact)
                showMessage(getString(R.string.contact_not_found))
                return@find
            }
            if (!presence.fingerprint.equals(contact.fingerprint, ignoreCase = true)) {
                chatStatusRes = R.string.chat_offline
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.identity_mismatch_title)
                    .setMessage(R.string.identity_mismatch_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@find
            }
            node.connect(presence.host)
        }
    }

    private fun showAddContactOptions() {
        CrashReporter.record(this, "contacts_add_options_open")
        safelyRunContactAction {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_contact_title)
                .setItems(arrayOf(getString(R.string.scan_qr), getString(R.string.enter_number))) { _, which ->
                    if (which == 0) {
                        CrashReporter.record(this, "contacts_scan_selected")
                        scanContactQr()
                    } else {
                        CrashReporter.record(this, "contacts_manual_selected")
                        showNumberEntry()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun scanContactQr() {
        CrashReporter.record(this, "contacts_scanner_start")
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(this, options)
                .startScan()
                .addOnSuccessListener(this) { barcode ->
                    CrashReporter.record(this, "contacts_scanner_result_received")
                    val contact = try {
                        FreedomContactCodec.decode(barcode.rawValue.orEmpty())
                    } catch (_: Exception) {
                        null
                    }
                    if (contact == null || contact.freedomNumber == ownContact().freedomNumber) {
                        showMessage(getString(R.string.invalid_contact))
                    } else {
                        confirmScannedContact(contact)
                    }
                }
                .addOnFailureListener(this, ::handleScannerFailure)
        } catch (error: RuntimeException) {
            handleScannerFailure(error)
        } catch (error: LinkageError) {
            handleScannerFailure(error)
        }
    }

    private fun handleScannerFailure(error: Throwable) {
        Log.e(TAG, "Unable to start or complete the QR scanner", error)
        CrashReporter.record(this, "contacts_scanner_error:${error.javaClass.simpleName}")
        CrashReporter.recordHandledError(this, "contacts_scanner", error)
        if (!canShowUi()) return
        showMessage(
            getString(
                R.string.scanner_error_format,
                error.localizedMessage ?: error.javaClass.simpleName
            )
        )
        mainHandler.post { if (canShowUi()) showNumberEntry() }
    }

    private fun confirmScannedContact(scanned: FreedomContact) {
        CrashReporter.record(this, "contacts_scanned_confirm_open")
        val nameInput = textInput(
            hint = getString(R.string.contact_name_hint),
            initialValue = scanned.displayName.ifBlank { getString(R.string.contact_default_name) }
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contact_found)
            .setMessage(FreedomNumber.format(scanned.freedomNumber))
            .setView(nameInput.first)
            .setPositiveButton(R.string.save) { _, _ ->
                val saved = scanned.copy(
                    displayName = nameInput.second.text?.toString()?.trim()
                        .orEmpty()
                        .ifBlank { getString(R.string.contact_default_name) }
                        .take(48)
                )
                saveContact(saved)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNumberEntry() {
        CrashReporter.record(this, "contacts_manual_form_open")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), 0)
        }
        val nameInput = textInput(getString(R.string.contact_name_hint))
        val numberInput = textInput(getString(R.string.freedom_number_hint), numeric = true)
        form.addView(nameInput.first)
        form.addView(numberInput.first, margins(top = 10))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.enter_number)
            .setView(form)
            .setPositiveButton(R.string.find_contact, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = FreedomNumber.normalize(numberInput.second.text?.toString().orEmpty())
                if (!FreedomNumber.isValid(number) || number == ownContact().freedomNumber) {
                    numberInput.first.error = getString(R.string.invalid_number)
                    return@setOnClickListener
                }
                val requestedName = nameInput.second.text?.toString()?.trim().orEmpty()
                dialog.dismiss()
                CrashReporter.record(this, "contacts_manual_lookup_started")
                showMessage(getString(R.string.discovering_number_format, FreedomNumber.format(number)))
                directory?.find(number) { presence ->
                    if (presence == null) {
                        showMessage(getString(R.string.contact_not_found))
                    } else {
                        savePresenceAsContact(presence, requestedName)
                    }
                } ?: showMessage(getString(R.string.contact_not_found))
            }
        }
        dialog.show()
    }

    private fun savePresenceAsContact(presence: PeerPresence, requestedName: String) {
        val contact = FreedomContact(
            displayName = requestedName.ifBlank {
                presence.displayName.ifBlank { getString(R.string.contact_default_name) }
            }.take(48),
            freedomNumber = presence.freedomNumber,
            fingerprint = presence.fingerprint,
            networkId = chainSettings.network.id
        )
        saveContact(contact)
    }

    private fun saveContact(contact: FreedomContact) {
        CrashReporter.record(this, "contacts_save_started")
        safelyRunContactAction {
            if (!contacts.save(contact)) {
                CrashReporter.record(this, "contacts_save_storage_rejected")
                showMessage(getString(R.string.contact_save_error))
                return@safelyRunContactAction
            }
            CrashReporter.record(this, "contacts_save_success_render_started")
            showMessage(getString(R.string.contact_saved_format, contact.displayName))
            showContacts()
            CrashReporter.record(this, "contacts_save_success_render_completed")
        }
    }

    private fun confirmUnknownPeer(fingerprint: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unknown_peer_title)
            .setMessage(getString(R.string.unknown_peer_body_format, fingerprint))
            .setPositiveButton(R.string.authorize) { _, _ ->
                val number = try {
                    FreedomNumber.fromFingerprint(fingerprint)
                } catch (_: Exception) {
                    node.rejectPendingPeer()
                    return@setPositiveButton
                }
                val contact = FreedomContact(
                    displayName = getString(R.string.contact_default_name),
                    freedomNumber = number,
                    fingerprint = fingerprint,
                    networkId = chainSettings.network.id
                )
                if (!contacts.save(contact)) {
                    showMessage(getString(R.string.contact_save_error))
                    node.rejectPendingPeer()
                    return@setPositiveButton
                }
                currentContact = contact
                node.approvePendingPeer()
            }
            .setNegativeButton(R.string.reject) { _, _ -> node.rejectPendingPeer() }
            .setOnCancelListener { node.rejectPendingPeer() }
            .show()
    }

    private fun prepareTopLevel(title: String, subtitle: String, selectedItem: Int) {
        bottomNavigation.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(appRoot)
        if (bottomNavigation.selectedItemId != selectedItem) {
            bottomNavigation.menu.findItem(selectedItem).isChecked = true
        }
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        toolbar.menu.clear()
        toolbar.title = title
        toolbar.subtitle = subtitle
        toolbar.menu.add(Menu.NONE, ACTION_IDENTITY, Menu.NONE, R.string.my_identity_short).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_qr)
            setOnMenuItemClickListener {
                showIdentity()
                true
            }
        }
    }

    private fun prepareDetail(title: String, subtitle: String? = null) {
        bottomNavigation.visibility = View.GONE
        ViewCompat.requestApplyInsets(appRoot)
        toolbar.menu.clear()
        toolbar.title = title
        toolbar.subtitle = subtitle
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { showChats() }
    }

    private fun identitySummaryCard(): View = MaterialCardView(this).apply {
        radius = dp(26).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(color(R.color.freedom_primary_container))
        isClickable = true
        isFocusable = true
        setOnClickListener { showIdentity() }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(16), dp(18))
        }
        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_qr)
            setColorFilter(color(R.color.freedom_on_primary_container))
            contentDescription = getString(R.string.my_identity_short)
            background = circleDrawable(color(R.color.freedom_surface))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(titleText(getString(R.string.my_identity_short)))
        textColumn.addView(captionText(FreedomNumber.format(ownContact().freedomNumber)))
        row.addView(textColumn, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(row)
    }

    private fun addContactFab(): FloatingActionButton = FloatingActionButton(this).apply {
        setImageResource(R.drawable.ic_add)
        contentDescription = getString(R.string.add_contact)
        setOnClickListener { showAddContactOptions() }
    }

    private fun contactCard(contact: FreedomContact, allowDelete: Boolean = false): View =
        MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.freedom_outline)
            setCardBackgroundColor(color(R.color.freedom_surface))
            isClickable = true
            isFocusable = true
            setOnClickListener { openChat(contact) }
            if (allowDelete) setOnLongClickListener {
                confirmDeleteContact(contact)
                true
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            row.addView(avatar(contact.displayName), LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                marginEnd = dp(14)
            })
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(titleText(contact.displayName))
            val last = chats.lastMessage(contact.freedomNumber)
            texts.addView(captionText(last?.text ?: FreedomNumber.format(contact.freedomNumber), maxLines = 1))
            val nearby = directory?.presence(contact.freedomNumber) != null
            texts.addView(TextView(context).apply {
                text = getString(if (nearby) R.string.online_nearby else R.string.offline_or_remote)
                textSize = 12f
                setTextColor(color(if (nearby) R.color.freedom_success else R.color.freedom_on_surface_variant))
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(row)
            layoutParams = margins(top = 10)
        }

    private fun confirmDeleteContact(contact: FreedomContact) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_contact_title, contact.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                contacts.delete(contact.freedomNumber)
                showContacts()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun networkCard(network: IdentityNetwork, descriptionRes: Int): View {
        val selected = chainSettings.network == network
        return MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(if (selected) 2 else 1)
            strokeColor = color(if (selected) R.color.freedom_primary else R.color.freedom_outline)
            setCardBackgroundColor(
                color(if (selected) R.color.freedom_primary_container else R.color.freedom_surface)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                chainSettings.network = network
                chainHealth = null
                chainHealthError = null
                restartDirectory()
                showMessage(getString(R.string.network_saved_format, network.displayName))
                if (!network.chainOperational) showMessage(getString(R.string.network_unavailable))
                showSettings()
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
            }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(titleText(network.displayName))
            texts.addView(captionText(getString(descriptionRes)))
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (selected) row.addView(badge(getString(R.string.selected_network)))
            addView(row)
            layoutParams = margins(top = 10)
        }
    }

    private fun informationCard(
        title: String,
        body: String,
        badge: String? = null,
        accentColor: Int
    ): View = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = color(R.color.freedom_outline)
        setCardBackgroundColor(color(R.color.freedom_surface))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        row.addView(View(context).apply { background = circleDrawable(accentColor) },
            LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(12)
                topMargin = dp(6)
            })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(titleText(title), LinearLayout.LayoutParams(0, WRAP, 1f))
        if (badge != null) titleRow.addView(badge(badge))
        texts.addView(titleRow)
        texts.addView(captionText(body, maxLines = 6), margins(top = 5))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(row)
        layoutParams = margins(top = 10)
    }

    private fun actionCard(title: String, subtitle: String, icon: Int, action: () -> Unit): View =
        MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(R.color.freedom_primary_container))
            isClickable = true
            setOnClickListener { action() }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
            }
            row.addView(ImageView(context).apply {
                setImageResource(icon)
                setColorFilter(color(R.color.freedom_on_primary_container))
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(14) })
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(titleText(title))
            texts.addView(captionText(subtitle))
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(row)
        }

    private fun messageBubble(message: ChatMessage): View {
        val wrapper = FrameLayout(this)
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(
                    color(
                        if (message.outgoing) R.color.freedom_primary_container
                        else R.color.freedom_surface_variant
                    )
                )
            }
        }
        bubble.addView(TextView(this).apply {
            text = message.text
            textSize = 16f
            setTextColor(color(R.color.freedom_on_surface))
        })
        bubble.addView(TextView(this).apply {
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestampMillis))
            textSize = 11f
            gravity = Gravity.END
            setTextColor(color(R.color.freedom_on_surface_variant))
            setPadding(0, dp(3), 0, 0)
        })
        wrapper.addView(
            bubble,
            FrameLayout.LayoutParams(WRAP, WRAP, if (message.outgoing) Gravity.END else Gravity.START).apply {
                marginStart = if (message.outgoing) dp(52) else 0
                marginEnd = if (message.outgoing) 0 else dp(52)
            }
        )
        wrapper.layoutParams = margins(top = 6)
        return wrapper
    }

    private fun avatar(name: String): TextView = TextView(this).apply {
        text = name.trim().firstOrNull()?.uppercase() ?: "F"
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(color(R.color.freedom_on_primary_container))
        background = circleDrawable(color(R.color.freedom_avatar_1))
    }

    private fun emptyState(titleRes: Int, bodyRes: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(34), dp(28), dp(34))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(color(R.color.freedom_surface_variant))
        }
        addView(titleText(getString(titleRes)).apply { gravity = Gravity.CENTER })
        addView(captionText(getString(bodyRes), maxLines = 4).apply { gravity = Gravity.CENTER }, margins(top = 7))
    }

    private fun sectionLabel(resId: Int): TextView = TextView(this).apply {
        text = getString(resId)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(color(R.color.freedom_on_surface_variant))
    }

    private fun titleText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.freedom_on_surface))
    }

    private fun captionText(value: String, maxLines: Int = 2): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(color(R.color.freedom_on_surface_variant))
        this.maxLines = maxLines
        setPadding(0, dp(3), 0, 0)
    }

    private fun bodyText(resId: Int, centered: Boolean = false): TextView = TextView(this).apply {
        text = getString(resId)
        textSize = 15f
        setTextColor(color(R.color.freedom_on_surface_variant))
        if (centered) gravity = Gravity.CENTER
    }

    private fun badge(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.freedom_on_primary_container))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(color(R.color.freedom_primary_container))
        }
        setPadding(dp(9), dp(5), dp(9), dp(5))
    }

    private fun textInput(
        hint: String,
        initialValue: String = "",
        numeric: Boolean = false
    ): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = hint
        }
        val input = TextInputEditText(layout.context).apply {
            setText(initialValue)
            inputType = if (numeric) InputType.TYPE_CLASS_PHONE else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        layout.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        return layout to input
    }

    private fun copyOwnNumber() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.freedom_number_label), ownContact().freedomNumber))
        showMessage(getString(R.string.copied))
    }

    private fun shareOwnContact() {
        val contact = ownContact()
        val uri = FreedomContactCodec.encode(contact)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(
                            R.string.contact_share_text,
                            FreedomNumber.format(contact.freedomNumber),
                            uri
                        )
                    )
                },
                getString(R.string.share_contact)
            )
        )
    }

    private fun showCrashReportIfPresent() {
        if (!canShowUi() || !CrashReporter.hasUnseenCrash(this)) return
        CrashReporter.markCrashSeen(this)
        CrashReporter.record(this, "diagnostic_crash_prompt_shown")
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.crash_report_title)
            .setMessage(R.string.crash_report_body)
            .setPositiveButton(R.string.share_log) { _, _ -> shareDiagnosticLog() }
            .setNeutralButton(R.string.copy_log) { _, _ -> copyDiagnosticLog() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun copyDiagnosticLog() {
        val report = CrashReporter.reportForSharing(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostic_log), report))
        showMessage(getString(R.string.log_copied))
    }

    private fun shareDiagnosticLog() {
        CrashReporter.record(this, "diagnostic_log_share_requested")
        val report = CrashReporter.reportForSharing(this)
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostic_log))
                        putExtra(Intent.EXTRA_TEXT, report)
                    },
                    getString(R.string.share_log)
                )
            )
        }.onFailure {
            copyDiagnosticLog()
            showMessage(getString(R.string.share_log_fallback))
        }
    }

    private fun pageColumn(bottomPadding: Int = 28): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(bottomPadding))
    }

    private fun setPage(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        ViewCompat.requestApplyInsets(appRoot)
    }

    private fun showMessage(message: String) {
        if (!canShowUi() || !::appRoot.isInitialized || !appRoot.isAttachedToWindow) return
        runCatching {
            Snackbar.make(appRoot, message, Snackbar.LENGTH_LONG).apply {
                val layout = view.layoutParams as? ViewGroup.MarginLayoutParams
                if (layout != null) {
                    val navigationOffset = if (bottomNavigation.visibility == View.VISIBLE) {
                        bottomNavigation.height
                    } else {
                        maxOf(systemBottomInset, imeBottomInset)
                    }
                    layout.bottomMargin = navigationOffset + dp(8)
                    view.layoutParams = layout
                }
                show()
            }
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun safelyRunContactAction(action: () -> Unit) {
        if (!canShowUi()) return
        try {
            action()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Contact action failed", error)
            CrashReporter.record(this, "contacts_action_error:${error.javaClass.simpleName}")
            CrashReporter.recordHandledError(this, "contacts_action", error)
            showMessage(getString(R.string.contact_action_error))
        } catch (error: LinkageError) {
            Log.e(TAG, "Contact action failed because an Android component is unavailable", error)
            CrashReporter.record(this, "contacts_linkage_error:${error.javaClass.simpleName}")
            CrashReporter.recordHandledError(this, "contacts_linkage", error)
            showMessage(getString(R.string.contact_action_error))
        }
    }

    private fun canShowUi(): Boolean = !isFinishing && !isDestroyed

    private fun color(resId: Int): Int = getColor(resId)

    private fun circleDrawable(fillColor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun margins(
        start: Int = 0,
        top: Int = 0,
        end: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        marginStart = dp(start)
        topMargin = dp(top)
        marginEnd = dp(end)
        bottomMargin = dp(bottom)
    }

    private fun centeredParams(width: Int, height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val REQUEST_LOCAL_NETWORK = 2001
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        const val ACTION_IDENTITY = 3001
        const val ACTION_SHARE = 3002
        const val TAG = "FreedomContacts"
        const val CRASH_REPORT_DELAY_MILLIS = 700L
    }
}
