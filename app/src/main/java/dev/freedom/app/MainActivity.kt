package dev.freedom.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import dev.freedom.app.chain.ChainDeviceRecord
import dev.freedom.app.chain.ChainMessageRecord
import dev.freedom.app.chain.IdentityNetwork
import dev.freedom.app.chain.NearChainAdapter
import dev.freedom.app.chain.NearCredentialStore
import dev.freedom.app.chain.NearCredentials
import dev.freedom.app.chain.NearDirectClient
import dev.freedom.app.chain.NearKeyQrCodec
import dev.freedom.app.chat.ChatMessage
import dev.freedom.app.chat.ChatRepository
import dev.freedom.app.contact.ContactRepository
import dev.freedom.app.contact.FreedomContact
import dev.freedom.app.contact.FreedomContactCodec
import dev.freedom.app.contact.FreedomNumber
import dev.freedom.app.contact.QrCodeRenderer
import dev.freedom.app.crypto.IdentityStore
import dev.freedom.app.crypto.Crypto
import dev.freedom.app.crypto.MailboxKeyStore
import dev.freedom.app.diagnostics.CrashReporter
import java.text.DateFormat
import java.math.BigInteger
import java.util.Base64
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private enum class Screen { CHATS, CONTACTS, SETTINGS, IDENTITY, CHAT }
    private enum class NearKeyState { CHECKING, ACTIVE, INVALID }

    private lateinit var identity: IdentityStore
    private lateinit var mailboxIdentity: MailboxKeyStore
    private lateinit var contacts: ContactRepository
    private lateinit var chats: ChatRepository
    private lateinit var chainSettings: ChainSettings
    private lateinit var nearChain: NearChainAdapter
    private lateinit var nearDirect: NearDirectClient
    private lateinit var nearCredentialStore: NearCredentialStore

    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var appRoot: View

    private var currentScreen = Screen.CHATS
    private var currentContact: FreedomContact? = null
    private var connectedContactNumber: String? = null
    private var chatStatusRes = R.string.chat_waiting
    private var chainHealth: ChainHealth? = null
    private var chainHealthError: String? = null
    private var chainHealthLoading = false
    private var nearOperationStatus: String? = null
    private var nearOperationInProgress = false
    private var nearKeyState: NearKeyState? = null
    private var nearKeyStateDetail: String? = null
    private var nearKeyCheckInProgress = false
    private var nearSelfTestInProgress = false
    private var nearSelfTestStatus: String? = null
    private var messageSendInProgress = false
    private var mailboxPollingStarted = false
    private var mailboxPollErrorReported = false
    private var systemBottomInset = 0
    private var imeBottomInset = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val chainExecutor = Executors.newSingleThreadExecutor()
    private val mailboxPollRunnable = object : Runnable {
        override fun run() {
            pollBlockchainMailbox()
            if (mailboxPollingStarted) {
                mainHandler.postDelayed(this, MAILBOX_POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.record(this, "main_activity_on_create")
        identity = IdentityStore(this)
        mailboxIdentity = MailboxKeyStore(this)
        contacts = ContactRepository(this)
        chats = ChatRepository(this)
        chainSettings = ChainSettings(this)
        if (chainSettings.network != IdentityNetwork.NEAR_TESTNET) {
            chainSettings.network = IdentityNetwork.NEAR_TESTNET
        }
        nearCredentialStore = NearCredentialStore(this)
        rebuildNearClients()

        buildChrome()
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
        mainHandler.postDelayed(::showCrashReportIfPresent, CRASH_REPORT_DELAY_MILLIS)
    }

    override fun onStart() {
        super.onStart()
        startMailboxPolling()
        refreshOwnIdentityOnChain()
    }

    override fun onStop() {
        stopMailboxPolling()
        super.onStop()
    }

    override fun onDestroy() {
        CrashReporter.record(this, "main_activity_on_destroy")
        stopMailboxPolling()
        chainExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun ownContact(): FreedomContact = FreedomContact(
        displayName = getString(R.string.contact_default_name),
        freedomNumber = FreedomNumber.fromPublicKey(identity.publicKey),
        fingerprint = identity.fingerprint,
        networkId = chainSettings.network.id,
        deviceId = identity.deviceId,
        identityPublicKey = Base64.getEncoder().encodeToString(identity.compressedPublicKey),
        mailboxPublicKey = Base64.getEncoder().encodeToString(mailboxIdentity.compressedPublicKey),
        keyEpoch = 1
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

        column.addView(sectionLabel(R.string.chain_status_section))
        val currentHealth = chainHealth
        val statusTitle: String
        val statusBody: String
        val statusColor: Int
        when {
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

        if (chainSettings.network == IdentityNetwork.NEAR_TESTNET) {
            column.addView(sectionLabel(R.string.near_access_section), margins(top = 28))
            val configuredAccount = nearCredentialStore.accountId()
            val keyTitle: String
            val keyBody: String
            val keyColor: Int
            when {
                configuredAccount == null -> {
                    keyTitle = getString(R.string.near_key_inactive)
                    keyBody = getString(R.string.near_key_not_configured)
                    keyColor = color(R.color.freedom_outline)
                }
                nearKeyState == NearKeyState.ACTIVE -> {
                    keyTitle = getString(R.string.near_key_active)
                    keyBody = getString(R.string.near_key_active_for, configuredAccount)
                    keyColor = color(R.color.freedom_success)
                }
                nearKeyState == NearKeyState.INVALID -> {
                    keyTitle = getString(R.string.near_key_invalid)
                    keyBody = nearKeyStateDetail ?: getString(R.string.invalid_near_key)
                    keyColor = color(R.color.freedom_error)
                }
                else -> {
                    keyTitle = getString(R.string.near_key_checking)
                    keyBody = getString(R.string.near_key_checking_body, configuredAccount)
                    keyColor = color(R.color.freedom_primary)
                }
            }
            column.addView(
                informationCard(
                    title = keyTitle,
                    body = keyBody,
                    badge = if (nearKeyState == NearKeyState.ACTIVE) getString(R.string.active) else null,
                    accentColor = keyColor
                )
            )
            column.addView(
                actionCard(
                    title = getString(
                        if (configuredAccount == null) R.string.configure_near_key
                        else R.string.change_near_key
                    ),
                    subtitle = configuredAccount?.let {
                        getString(R.string.near_key_configured_for, it)
                    } ?: getString(R.string.near_key_not_configured),
                    icon = R.drawable.ic_qr
                ) { showNearKeyOptions() },
                margins(top = 10)
            )
            if (configuredAccount != null) {
                column.addView(
                    actionCard(
                        title = getString(R.string.activate_device_on_chain),
                        subtitle = when {
                            nearOperationInProgress -> getString(R.string.near_activation_in_progress)
                            nearOperationStatus != null -> nearOperationStatus.orEmpty()
                            else -> getString(R.string.activate_device_on_chain_body)
                        },
                        icon = R.drawable.ic_contacts
                    ) { activateOwnDeviceOnChain() },
                    margins(top = 10)
                )
            }
        }

        column.addView(sectionLabel(R.string.network_costs_section), margins(top = 28))
        column.addView(
            informationCard(
                title = getString(R.string.direct_fees),
                body = getString(R.string.direct_fees_body),
                accentColor = color(R.color.freedom_success)
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

        column.addView(sectionLabel(R.string.advanced_section), margins(top = 28))
        column.addView(
            actionCard(
                title = getString(R.string.near_connection_advanced),
                subtitle = chainSettings.customRpcEndpoint
                    ?: getString(R.string.near_connection_automatic),
                icon = R.drawable.ic_settings
            ) { showRpcConfiguration() },
            margins(top = 10)
        )

        column.addView(sectionLabel(R.string.diagnostics_section), margins(top = 28))
        column.addView(
            actionCard(
                title = getString(R.string.near_full_test),
                subtitle = when {
                    nearSelfTestInProgress -> getString(R.string.near_full_test_running)
                    nearSelfTestStatus != null -> nearSelfTestStatus.orEmpty()
                    else -> getString(R.string.near_full_test_body)
                },
                icon = R.drawable.ic_send
            ) { runNearSelfTest() },
            margins(top = 10)
        )
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
        refreshNearKeyStatusIfNeeded()
    }

    private fun rebuildNearClients() {
        val endpoints = chainSettings.rpcEndpoints()
        nearChain = NearChainAdapter(rpcEndpoints = endpoints)
        nearDirect = NearDirectClient(NearChainAdapter.TESTNET_CONTRACT_ID, endpoints)
    }

    private fun showNearKeyOptions() {
        val options = mutableListOf(
            getString(R.string.scan_secret_key_qr),
            getString(R.string.enter_secret_key_manually)
        )
        if (nearCredentialStore.hasCredentials()) options += getString(R.string.remove_near_key)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.configure_near_key)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> scanNearKeyQr()
                    1 -> showNearKeyManualEntry()
                    else -> confirmRemoveNearKey()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun scanNearKeyQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw == null) {
                    showMessage(getString(R.string.invalid_near_key_qr))
                    return@addOnSuccessListener
                }
                runCatching { NearKeyQrCodec.decode(raw, chainSettings.network) }
                    .onSuccess(::validateAndSaveNearKey)
                    .onFailure { showMessage(it.message ?: getString(R.string.invalid_near_key_qr)) }
            }
            .addOnFailureListener {
                showMessage(getString(R.string.scanner_error_format, it.javaClass.simpleName))
            }
    }

    private fun showNearKeyManualEntry() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val account = textInput(getString(R.string.near_account_hint))
        val privateKey = textInput(
            hint = getString(R.string.near_private_key_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        privateKey.first.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        container.addView(account.first)
        container.addView(privateKey.first, margins(top = 12))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.enter_secret_key_manually)
            .setMessage(R.string.near_key_security_warning)
            .setView(container)
            .setPositiveButton(R.string.verify_and_save) { _, _ ->
                runCatching {
                    NearCredentials.parse(
                        account.second.text?.toString().orEmpty(),
                        privateKey.second.text?.toString().orEmpty()
                    )
                }.onSuccess(::validateAndSaveNearKey)
                    .onFailure { showMessage(it.message ?: getString(R.string.invalid_near_key)) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun validateAndSaveNearKey(credentials: NearCredentials) {
        if (nearOperationInProgress) return
        nearOperationInProgress = true
        nearKeyState = NearKeyState.CHECKING
        nearKeyStateDetail = null
        nearOperationStatus = getString(R.string.near_key_validation_in_progress)
        if (currentScreen == Screen.SETTINGS) showSettings()
        chainExecutor.execute {
            val result = nearDirect.validateRestrictedKey(credentials)
            if (result.isSuccess) runCatching { nearCredentialStore.save(credentials) }
                .onFailure { failure ->
                    ui {
                        CrashReporter.record(this, "near_key_save_failed:${failure.javaClass.simpleName}")
                        CrashReporter.recordHandledError(this, "near_key_save", failure)
                        nearOperationInProgress = false
                        nearKeyState = NearKeyState.INVALID
                        nearKeyStateDetail = failure.message
                        nearOperationStatus = failure.message
                        showMessage(failure.message ?: getString(R.string.invalid_near_key))
                        if (currentScreen == Screen.SETTINGS) showSettings()
                    }
                }
                .onSuccess {
                    ui {
                        nearOperationInProgress = false
                        nearKeyState = NearKeyState.ACTIVE
                        nearKeyStateDetail = null
                        nearOperationStatus = getString(R.string.near_key_saved)
                        showMessage(getString(R.string.near_key_saved))
                        if (currentScreen == Screen.SETTINGS) showSettings()
                    }
                }
            else ui {
                nearOperationInProgress = false
                nearKeyState = NearKeyState.INVALID
                nearKeyStateDetail = result.exceptionOrNull()?.message
                nearOperationStatus = result.exceptionOrNull()?.message
                showMessage(nearOperationStatus ?: getString(R.string.invalid_near_key))
                if (currentScreen == Screen.SETTINGS) showSettings()
            }
        }
    }

    private fun confirmRemoveNearKey() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_near_key)
            .setMessage(R.string.remove_near_key_body)
            .setPositiveButton(R.string.remove) { _, _ ->
                nearCredentialStore.clear()
                nearOperationStatus = null
                nearKeyState = null
                nearKeyStateDetail = null
                showSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRpcConfiguration() {
        val rpc = textInput(
            hint = getString(R.string.rpc_endpoint_hint),
            initialValue = chainSettings.customRpcEndpoint.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.configure_rpc)
            .setMessage(R.string.configure_rpc_body)
            .setView(rpc.first)
            .setPositiveButton(R.string.save) { _, _ ->
                runCatching {
                    chainSettings.customRpcEndpoint = rpc.second.text?.toString().orEmpty()
                        .takeIf { it.isNotBlank() }
                    rebuildNearClients()
                    chainHealth = null
                    chainHealthError = null
                    nearKeyState = null
                    nearKeyStateDetail = null
                }.onSuccess {
                    showSettings()
                }.onFailure { showMessage(it.message ?: getString(R.string.invalid_rpc_endpoint)) }
            }
            .setNeutralButton(R.string.restore_default) { _, _ ->
                chainSettings.customRpcEndpoint = null
                rebuildNearClients()
                chainHealth = null
                chainHealthError = null
                nearKeyState = null
                nearKeyStateDetail = null
                showSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun activateOwnDeviceOnChain() {
        if (nearOperationInProgress) return
        val credentials = nearCredentialStore.load().getOrElse {
            CrashReporter.record(this, "near_key_load_failed:${it.javaClass.simpleName}")
            CrashReporter.recordHandledError(this, "near_key_load", it)
            showMessage(it.message ?: getString(R.string.invalid_near_key))
            return
        }
        nearOperationInProgress = true
        nearOperationStatus = getString(R.string.near_activation_in_progress)
        showSettings()
        chainExecutor.execute {
            val result = runCatching {
                ensureOwnIdentityOnChain(credentials)
                getString(R.string.device_and_number_active)
            }
            ui {
                nearOperationInProgress = false
                nearOperationStatus = result.getOrElse {
                    it.message ?: getString(R.string.device_activation_failed)
                }
                showMessage(nearOperationStatus.orEmpty())
                if (currentScreen == Screen.SETTINGS) showSettings()
            }
        }
    }

    private fun ensureOwnIdentityOnChain(credentials: NearCredentials): ChainDeviceRecord {
        nearDirect.validateRestrictedKey(credentials).getOrThrow()
        var device = nearChain.resolveDevice(identity.deviceId).getOrThrow()
        if (device != null) {
            require(device.active) { "Il dispositivo risulta revocato su NEAR" }
            require(device.identityPublicKey.contentEquals(identity.compressedPublicKey)) {
                "Il Device ID è già associato a una chiave identità diversa"
            }
        } else {
            val balance = BigInteger(nearChain.storageBalance(credentials.accountId).getOrThrow())
            require(balance.signum() > 0) { getString(R.string.storage_credit_missing) }
            val publicKey = identity.compressedPublicKey
            val signature = identity.registrationSignature(
                NearChainAdapter.TESTNET_CONTRACT_ID,
                NearChainAdapter.PROTOCOL_VERSION
            )
            val arguments = org.json.JSONObject()
                .put("device_id", identity.deviceId)
                .put("identity_public_key", Base64.getEncoder().encodeToString(publicKey))
                .put("protocol_version", NearChainAdapter.PROTOCOL_VERSION)
                .put("signature", Base64.getEncoder().encodeToString(signature))
            nearDirect.callFunction(credentials, "register_device", arguments).getOrThrow()
            device = nearChain.resolveDevice(identity.deviceId).getOrThrow()
                ?: error("Registrazione del dispositivo non visibile su NEAR")
        }

        val ownNumber = ownContact().freedomNumber
        val published = nearChain.resolveContact(ownNumber).getOrThrow()
        val mailboxKey = mailboxIdentity.compressedPublicKey
        if (published?.deviceId != identity.deviceId ||
            !published.mailboxPublicKey.contentEquals(mailboxKey)
        ) {
            val publishNonce = device.authNonce + 1
            val signature = identity.contactPublishSignature(
                contractId = NearChainAdapter.TESTNET_CONTRACT_ID,
                freedomNumber = ownNumber,
                mailboxPublicKey = mailboxKey,
                authNonce = publishNonce,
                keyEpoch = device.keyEpoch,
                protocolVersion = NearChainAdapter.PROTOCOL_VERSION
            )
            val publishArguments = org.json.JSONObject()
                .put("device_id", identity.deviceId)
                .put("freedom_number", ownNumber)
                .put("mailbox_public_key", Base64.getEncoder().encodeToString(mailboxKey))
                .put("auth_nonce", publishNonce.toString())
                .put("signature", Base64.getEncoder().encodeToString(signature))
            nearDirect.callFunction(credentials, "publish_contact", publishArguments).getOrThrow()
            device = nearChain.resolveDevice(identity.deviceId).getOrThrow()
                ?: error("Pubblicazione del numero non visibile su NEAR")
        }
        return device
    }

    private fun runNearSelfTest() {
        if (nearSelfTestInProgress || nearOperationInProgress) return
        val credentials = nearCredentialStore.load().getOrElse {
            CrashReporter.record(this, "near_key_load_failed:${it.javaClass.simpleName}")
            CrashReporter.recordHandledError(this, "near_key_load", it)
            showMessage(it.message ?: getString(R.string.invalid_near_key))
            return
        }
        nearSelfTestInProgress = true
        nearSelfTestStatus = getString(R.string.near_full_test_running)
        CrashReporter.record(this, "near_self_test_started")
        if (currentScreen == Screen.SETTINGS) showSettings()
        chainExecutor.execute {
            var stage = "rpc"
            var transactionHash: String? = null
            val result = runCatching {
                nearChain.checkHealth().getOrThrow()
                stage = "key"
                nearDirect.validateRestrictedKey(credentials).getOrThrow()
                stage = "identity"
                ensureOwnIdentityOnChain(credentials)
                stage = "encrypt"
                val marker = "freedom-self-test-${Crypto.randomBytes(12).toHex()}"
                    .toByteArray(Charsets.UTF_8)
                stage = "write"
                val sent = sendEncryptedMessage(
                    credentials = credentials,
                    recipientDeviceId = identity.deviceId,
                    recipientMailboxPublicKey = mailboxIdentity.compressedPublicKey,
                    plaintext = marker
                )
                transactionHash = sent.second
                CrashReporter.record(this, "near_self_test_tx:${sent.second.take(16)}")
                stage = "read"
                val stored = nearChain.getMessages(identity.deviceId).getOrThrow()
                    .firstOrNull { it.messageId == sent.first }
                    ?: error("Il messaggio di test non è leggibile dopo la finalizzazione")
                stage = "decrypt"
                val decrypted = decryptMailboxMessage(stored)
                require(decrypted.contentEquals(marker)) { "Contenuto decifrato non valido" }
                sent.second
            }
            ui {
                nearSelfTestInProgress = false
                result.onSuccess { hash ->
                    mailboxPollErrorReported = false
                    nearSelfTestStatus = getString(R.string.near_full_test_success, hash.take(12))
                    CrashReporter.record(this, "near_self_test_success")
                    showMessage(getString(R.string.near_full_test_success, hash.take(12)))
                }.onFailure { error ->
                    val wrapped = IllegalStateException(
                        "Test NEAR fallito nella fase $stage" +
                            (transactionHash?.let { " (tx=${it.take(24)})" } ?: ""),
                        error
                    )
                    CrashReporter.record(this, "near_self_test_failed:$stage")
                    CrashReporter.recordHandledError(this, "near_self_test_$stage", wrapped)
                    nearSelfTestStatus = getString(
                        R.string.near_full_test_failed,
                        stage,
                        error.message ?: error.javaClass.simpleName
                    )
                    showMessage(nearSelfTestStatus.orEmpty())
                }
                if (currentScreen == Screen.SETTINGS) showSettings()
            }
        }
    }

    private fun sendEncryptedMessage(
        credentials: NearCredentials,
        recipientDeviceId: String,
        recipientMailboxPublicKey: ByteArray,
        plaintext: ByteArray
    ): Pair<String, String> {
        val sender = nearChain.resolveDevice(identity.deviceId).getOrThrow()
            ?: error("Dispositivo Freedom non attivato su NEAR")
        require(sender.active) { "Dispositivo Freedom revocato su NEAR" }
        val messageId = Crypto.randomBytes(32).toHex()
        val expiresAtNs = Math.multiplyExact(
            System.currentTimeMillis() + MESSAGE_TTL_MILLIS,
            1_000_000L
        )
        val envelope = Crypto.encryptChainMessage(
            recipientPublicKey = recipientMailboxPublicKey,
            senderDeviceId = identity.deviceId,
            recipientDeviceId = recipientDeviceId,
            messageId = messageId,
            expiresAtNs = expiresAtNs,
            plaintext = plaintext
        )
        val authNonce = sender.authNonce + 1
        val signature = identity.messageSendSignature(
            contractId = NearChainAdapter.TESTNET_CONTRACT_ID,
            recipientDeviceId = recipientDeviceId,
            messageId = messageId,
            expiresAtNs = expiresAtNs,
            ephemeralPublicKey = envelope.ephemeralPublicKey,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
            authNonce = authNonce,
            keyEpoch = sender.keyEpoch,
            protocolVersion = NearChainAdapter.PROTOCOL_VERSION
        )
        val arguments = org.json.JSONObject()
            .put("sender_device_id", identity.deviceId)
            .put("recipient_device_id", recipientDeviceId)
            .put("message_id", messageId)
            .put("expires_at_ns", expiresAtNs.toString())
            .put("ephemeral_public_key", Base64.getEncoder().encodeToString(envelope.ephemeralPublicKey))
            .put("nonce", Base64.getEncoder().encodeToString(envelope.nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(envelope.ciphertext))
            .put("auth_nonce", authNonce.toString())
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val transaction = nearDirect.callFunction(credentials, "send_message", arguments).getOrThrow()
        return messageId to transaction.transactionHash
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

    private fun refreshNearKeyStatusIfNeeded() {
        if (!nearCredentialStore.hasCredentials()) {
            nearKeyState = null
            nearKeyStateDetail = null
            return
        }
        if (nearKeyCheckInProgress || nearKeyState != null) return
        val credentials = nearCredentialStore.load().getOrElse {
            nearKeyState = NearKeyState.INVALID
            nearKeyStateDetail = it.message
            return
        }
        nearKeyCheckInProgress = true
        nearKeyState = NearKeyState.CHECKING
        chainExecutor.execute {
            val result = nearDirect.validateRestrictedKey(credentials)
            ui {
                nearKeyCheckInProgress = false
                nearKeyState = if (result.isSuccess) NearKeyState.ACTIVE else NearKeyState.INVALID
                nearKeyStateDetail = result.exceptionOrNull()?.message
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
        chatStatusRes = R.string.chat_connecting
        renderChat(contact)
        connectToContact(contact)
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
            isEnabled = connectedContactNumber == contact.freedomNumber &&
                nearCredentialStore.hasCredentials() && !messageSendInProgress
            contentDescription = getString(R.string.send_e2ee)
            insetTop = 0
            insetBottom = 0
            setOnClickListener {
                val value = input.text?.toString().orEmpty().trim()
                if (value.isNotEmpty()) {
                    input.text?.clear()
                    sendBlockchainMessage(contact, value)
                }
            }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(composer, LinearLayout.LayoutParams(MATCH, WRAP))
        setPage(root)

        mainHandler.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun connectToContact(contact: FreedomContact) {
        currentContact = contact
        chatStatusRes = R.string.chat_connecting
        if (currentScreen == Screen.CHAT) renderChat(contact)
        chainExecutor.execute {
            val result = nearChain.resolveContact(contact.freedomNumber)
            ui {
                if (currentContact?.freedomNumber != contact.freedomNumber) return@ui
                result.onSuccess { chainContact ->
                    if (chainContact == null) {
                        connectedContactNumber = null
                        chatStatusRes = R.string.chat_offline
                        showMessage(getString(R.string.contact_not_found_on_chain))
                    } else {
                        val publicKey = runCatching {
                            Crypto.decodeCompressedP256PublicKey(chainContact.identityPublicKey)
                        }.getOrNull()
                        if (publicKey == null ||
                            FreedomNumber.fromPublicKey(publicKey) != chainContact.freedomNumber ||
                            chainContact.freedomNumber != contact.freedomNumber ||
                            !Crypto.fingerprint(publicKey).equals(contact.fingerprint, ignoreCase = true)
                        ) {
                            connectedContactNumber = null
                            chatStatusRes = R.string.chat_offline
                            showMessage(getString(R.string.identity_mismatch_body))
                        } else {
                            val updated = contact.copy(
                                deviceId = chainContact.deviceId,
                                identityPublicKey = Base64.getEncoder().encodeToString(chainContact.identityPublicKey),
                                mailboxPublicKey = Base64.getEncoder().encodeToString(chainContact.mailboxPublicKey),
                                keyEpoch = chainContact.keyEpoch
                            )
                            contacts.save(updated)
                            currentContact = updated
                            connectedContactNumber = updated.freedomNumber
                            chatStatusRes = R.string.chat_connected
                        }
                    }
                }.onFailure { error ->
                    connectedContactNumber = null
                    chatStatusRes = R.string.chat_offline
                    CrashReporter.recordHandledError(this, "near_contact_resolve", error)
                    showMessage(error.message ?: getString(R.string.chain_not_connected))
                }
                if (currentScreen == Screen.CHAT) renderChat(currentContact ?: contact)
            }
        }
    }

    private fun sendBlockchainMessage(contact: FreedomContact, text: String) {
        if (messageSendInProgress) return
        val credentials = nearCredentialStore.load().getOrElse {
            showMessage(getString(R.string.invalid_near_key))
            return
        }
        val recipientDeviceId = contact.deviceId
        val mailboxPublicKey = contact.mailboxPublicKey?.let {
            runCatching { Base64.getDecoder().decode(it) }.getOrNull()
        }
        if (recipientDeviceId == null || mailboxPublicKey == null) {
            showMessage(getString(R.string.contact_not_found_on_chain))
            connectToContact(contact)
            return
        }
        messageSendInProgress = true
        if (currentScreen == Screen.CHAT) renderChat(contact)
        chainExecutor.execute {
            val result = runCatching {
                val sent = sendEncryptedMessage(
                    credentials,
                    recipientDeviceId,
                    mailboxPublicKey,
                    text.toByteArray(Charsets.UTF_8)
                )
                ChatMessage(
                    messageId = sent.first,
                    contactNumber = contact.freedomNumber,
                    text = text,
                    outgoing = true,
                    timestampMillis = System.currentTimeMillis()
                )
            }
            ui {
                messageSendInProgress = false
                result.onSuccess { message ->
                    chats.add(message)
                    CrashReporter.record(this, "near_message_sent")
                }.onFailure { error ->
                    CrashReporter.record(this, "near_message_send_failed:${error.javaClass.simpleName}")
                    CrashReporter.recordHandledError(this, "near_message_send", error)
                    showMessage(error.message ?: getString(R.string.message_send_failed))
                }
                if (currentScreen == Screen.CHAT) renderChat(currentContact ?: contact)
            }
        }
    }

    private fun startMailboxPolling() {
        if (mailboxPollingStarted) return
        mailboxPollingStarted = true
        mainHandler.postDelayed(mailboxPollRunnable, 2_000L)
    }

    private fun stopMailboxPolling() {
        mailboxPollingStarted = false
        mainHandler.removeCallbacks(mailboxPollRunnable)
    }

    private fun refreshOwnIdentityOnChain() {
        if (!nearCredentialStore.hasCredentials() || chainExecutor.isShutdown) return
        val credentials = nearCredentialStore.load().getOrElse { return }
        chainExecutor.execute {
            runCatching { ensureOwnIdentityOnChain(credentials) }
                .onFailure { error ->
                    CrashReporter.record(this, "near_identity_refresh_failed:${error.javaClass.simpleName}")
                    CrashReporter.recordHandledError(this, "near_identity_refresh", error)
                }
        }
    }

    private fun pollBlockchainMailbox() {
        if (!nearCredentialStore.hasCredentials() || chainExecutor.isShutdown) return
        chainExecutor.execute {
            val result = nearChain.getMessages(identity.deviceId)
            result.onSuccess { messages ->
                mailboxPollErrorReported = false
                val received = mutableListOf<Pair<FreedomContact, ChatMessage>>()
                messages.forEach { stored ->
                    if (stored.senderDeviceId == identity.deviceId) return@forEach
                    val contact = contacts.findByDeviceId(stored.senderDeviceId) ?: return@forEach
                    if (chats.contains(stored.messageId)) return@forEach
                    runCatching {
                        val plaintext = decryptMailboxMessage(stored)
                        val text = plaintext.toString(Charsets.UTF_8)
                        require(text.isNotBlank())
                        contact to ChatMessage(
                            messageId = stored.messageId,
                            contactNumber = contact.freedomNumber,
                            text = text,
                            outgoing = false,
                            timestampMillis = stored.sentAtNs / 1_000_000L
                        )
                    }.onSuccess(received::add)
                }
                if (received.isNotEmpty()) ui {
                    received.forEach { (contact, message) ->
                        chats.add(message)
                        CrashReporter.record(this, "near_message_received")
                        if (currentScreen != Screen.CHAT) showMessage(getString(R.string.new_message_from, contact.displayName))
                    }
                    currentContact?.let { if (currentScreen == Screen.CHAT) renderChat(it) }
                }
            }.onFailure { error ->
                if (!mailboxPollErrorReported) {
                    mailboxPollErrorReported = true
                    CrashReporter.record(this, "near_mailbox_poll_failed:${error.javaClass.simpleName}")
                    CrashReporter.recordHandledError(this, "near_mailbox_poll", error)
                }
            }
        }
    }

    private fun decryptMailboxMessage(stored: ChainMessageRecord): ByteArray {
        var lastFailure: Throwable? = null
        mailboxIdentity.privateKeysNewestFirst().forEach { privateKey ->
            runCatching {
                Crypto.decryptChainMessage(
                    privateKey = privateKey,
                    senderDeviceId = stored.senderDeviceId,
                    recipientDeviceId = stored.recipientDeviceId,
                    messageId = stored.messageId,
                    expiresAtNs = stored.expiresAtNs,
                    ephemeralPublicKey = stored.ephemeralPublicKey,
                    nonce = stored.nonce,
                    ciphertext = stored.ciphertext
                )
            }.onSuccess { return it }.onFailure { lastFailure = it }
        }
        throw IllegalStateException("Messaggio non decifrabile con le chiavi mailbox conservate", lastFailure)
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
                chainExecutor.execute {
                    val result = nearChain.resolveContact(number)
                    ui {
                        val record = result.getOrNull()
                        if (record == null) {
                            showMessage(
                                result.exceptionOrNull()?.message
                                    ?: getString(R.string.contact_not_found_on_chain)
                            )
                            return@ui
                        }
                        val publicKey = runCatching {
                            Crypto.decodeCompressedP256PublicKey(record.identityPublicKey)
                                .also {
                                    require(FreedomNumber.fromPublicKey(it) == record.freedomNumber)
                                    Crypto.decodeCompressedP256PublicKey(record.mailboxPublicKey)
                                }
                        }.getOrElse {
                            showMessage(getString(R.string.invalid_chain_contact))
                            return@ui
                        }
                        saveContact(
                            FreedomContact(
                                displayName = requestedName.ifBlank {
                                    getString(R.string.contact_default_name)
                                }.take(48),
                                freedomNumber = record.freedomNumber,
                                fingerprint = Crypto.fingerprint(publicKey),
                                networkId = IdentityNetwork.NEAR_TESTNET.id,
                                deviceId = record.deviceId,
                                identityPublicKey = Base64.getEncoder()
                                    .encodeToString(record.identityPublicKey),
                                mailboxPublicKey = Base64.getEncoder()
                                    .encodeToString(record.mailboxPublicKey),
                                keyEpoch = record.keyEpoch
                            )
                        )
                    }
                }
            }
        }
        dialog.show()
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
            val activeOnChain = contact.deviceId != null && contact.mailboxPublicKey != null
            texts.addView(TextView(context).apply {
                text = getString(if (activeOnChain) R.string.online_nearby else R.string.offline_or_remote)
                textSize = 12f
                setTextColor(color(if (activeOnChain) R.color.freedom_success else R.color.freedom_on_surface_variant))
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
        numeric: Boolean = false,
        inputType: Int? = null
    ): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = hint
        }
        val input = TextInputEditText(layout.context).apply {
            setText(initialValue)
            this.inputType = inputType ?: if (numeric) InputType.TYPE_CLASS_PHONE else
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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

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
        const val ACTION_IDENTITY = 3001
        const val ACTION_SHARE = 3002
        const val TAG = "FreedomContacts"
        const val CRASH_REPORT_DELAY_MILLIS = 700L
        const val MAILBOX_POLL_INTERVAL_MILLIS = 10_000L
        const val MESSAGE_TTL_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
