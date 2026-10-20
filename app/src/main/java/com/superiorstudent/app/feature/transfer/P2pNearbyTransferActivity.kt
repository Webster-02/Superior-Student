package com.superiorstudent.app.feature.transfer

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadata
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadataReader
import com.superiorstudent.app.feature.transfer.data.P2pTransferValidation
import com.superiorstudent.app.feature.transfer.security.P2pEncryptedChunkStream
import com.superiorstudent.app.feature.transfer.security.P2pTransferCrypto
import com.superiorstudent.app.feature.transfer.transport.P2pLanDiscovery
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nearby-only file transfer. Offer, consent, ephemeral key exchange, encrypted frames and receipt
 * travel over the local socket; Firebase and Internet signaling are not used by this screen.
 */
class P2pNearbyTransferActivity : AppCompatActivity() {

    private enum class NearbyUiStage { HUB, DISCOVERY, VERIFY, TRANSFER, COMPLETE, HISTORY }
    private enum class NearbyPermissionAction { SEND, RECEIVE }

    private lateinit var statusText: TextView
    private lateinit var sendButton: Button
    private lateinit var receiveButton: Button
    private lateinit var pauseResumeButton: Button
    private lateinit var stopButton: Button
    private lateinit var rootLayout: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private lateinit var hubPanel: LinearLayout
    private lateinit var discoveryPanel: LinearLayout
    private lateinit var discoveryHeading: TextView
    private lateinit var peerListContainer: LinearLayout
    private lateinit var verifyPanel: LinearLayout
    private lateinit var progressPanel: LinearLayout
    private lateinit var progressTitle: TextView
    private lateinit var progressFileName: TextView
    private lateinit var progressPercent: TextView
    private lateinit var progressAmounts: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusCard: MaterialCardView
    private lateinit var retryButton: MaterialButton
    private lateinit var historyPanel: LinearLayout
    private lateinit var historyItemsContainer: LinearLayout
    private lateinit var queueSummaryText: TextView
    private lateinit var transferActions: LinearLayout

    @Volatile private var uiStage = NearbyUiStage.HUB
    private val transferInProgress = AtomicBoolean(false)
    @Volatile private var transferIsOutgoing = false
    @Volatile private var transferFileName = ""
    @Volatile private var transferTotalBytes = 0L
    @Volatile private var transferStartOffset = 0L
    @Volatile private var transferStartedAtMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sending = AtomicBoolean(false)
    private val receiving = AtomicBoolean(false)
    private val activeIncomingSocket = AtomicBoolean(false)
    private val resumableDownloads = ConcurrentHashMap<String, ResumableDownloadState>()
    private val checkpointStore by lazy { P2pNearbyCheckpointStore(applicationContext) }
    private val transferHistoryStore by lazy { P2pNearbyTransferHistoryStore(applicationContext) }
    private val outgoingPaused = AtomicBoolean(false)
    private val outgoingStreamActive = AtomicBoolean(false)
    private val senderDataFinished = AtomicBoolean(false)
    private val pauseRequestInFlight = AtomicBoolean(false)
    private val outgoingFrameLock = Any()
    @Volatile private var activeOutgoingOutput: DataOutputStream? = null

    @Volatile private var outgoingDiscovery: P2pLanDiscovery? = null
    @Volatile private var advertisingDiscovery: P2pLanDiscovery? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var pendingIncomingSession: PendingIncomingSession? = null
    @Volatile private var preparedOutgoing: PreparedOutgoing? = null
    @Volatile private var peerPromptShown = false
    @Volatile private var temporaryDownload: File? = null
    private var pendingNearbyPermissionAction: NearbyPermissionAction? = null
    private var lastNearbyAction: NearbyPermissionAction? = null
    private var lastStatusAllowsRetry = false
    private var outgoingQueueUris: List<Uri> = emptyList()
    private var outgoingQueueFileNames: List<String> = emptyList()
    private var outgoingQueueIndex = 0
    private var outgoingQueueActive = false
    @Volatile private var lastSendSucceeded = false

    private data class PreparedOutgoing(
        val metadata: P2pTransferFileMetadata,
        val sha256: String
    )

    private data class ResumableDownloadState(
        val tempFile: File,
        val originalOffer: P2pNearbyOffer,
        @Volatile var destinationUri: Uri?
    )

    private class PendingIncomingSession(
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val offer: P2pNearbyOffer,
        val resumeState: ResumableDownloadState? = null
    ) {
        val selectionReady = CountDownLatch(1)
        @Volatile var accepted: Boolean = false
        @Volatile var destinationUri: Uri? = resumeState?.destinationUri
    }

    private data class Packet(val kind: Int, val payload: ByteArray)

    private val outgoingFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selectedUris: List<Uri> ->
            if (selectedUris.isNotEmpty()) prepareOutgoingQueue(selectedUris)
            else showStatus("File selection cancelled.")
        }

    private val incomingSavePicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            val pending = pendingIncomingSession
            if (pending == null) return@registerForActivityResult
            pending.destinationUri = uri
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            pending.resumeState?.destinationUri = uri
            pending.accepted = uri != null
            pending.selectionReady.countDown()
            pendingIncomingSession = null
            if (uri == null) showStatus("Save location cancelled. The sender will not transfer the file.")
            else showStatus("Save location selected. Comparing the security code…")
        }

    private val senderDiscoveryTimeout = Runnable {
        if (sending.get() && !peerPromptShown && uiStage == NearbyUiStage.DISCOVERY) {
            cancelCurrentAction()
            showStatus(
                "No nearby phone was found within 30 seconds. The phones may be on different Wi-Fi networks, or guest Wi-Fi/client isolation may block local discovery. Use the same non-guest Wi-Fi; if discovery still fails, turn on one phone's hotspot and connect the other phone to it, then retry."
            )
        }
    }

    private val nearbyWifiPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingNearbyPermissionAction
            pendingNearbyPermissionAction = null
            if (granted) {
                action?.let(::performNearbyPermissionAction)
            } else {
                setUiStage(NearbyUiStage.HUB)
                showStatus("Nearby Wi-Fi permission was denied. Allow it to discover or connect to nearby phones.")
                showNearbyWifiPermissionHelp(action)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        renderScreen()
        restoreResumableDownloads()
        when (intent.getStringExtra(EXTRA_INITIAL_MODE)) {
            MODE_SEND -> window.decorView.post {
                if (!isFinishing && !isDestroyed && !sending.get() && !receiving.get()) {
                    requestNearbyWifiPermission(NearbyPermissionAction.SEND)
                }
            }
            MODE_RECEIVE -> requestNearbyWifiPermission(NearbyPermissionAction.RECEIVE)
        }
    }

    private fun restoreResumableDownloads() {
        val checkpoints = try {
            checkpointStore.restore()
        } catch (error: Exception) {
            LogNearbyTransfer.error("Could not restore nearby transfer checkpoints", error)
            emptyList()
        }
        checkpoints.forEach { checkpoint ->
            resumableDownloads[checkpoint.offer.transferId] = ResumableDownloadState(
                tempFile = checkpoint.tempFile,
                originalOffer = checkpoint.offer,
                destinationUri = null
            )
        }
        if (checkpoints.isNotEmpty()) {
            showStatus(
                "Found " + checkpoints.size +
                    " saved nearby transfer checkpoint(s). Start Receive nearby file on the same Wi-Fi to resume."
            )
        }
    }

    private fun renderScreen() {
        val page = color(R.color.sc_navy)
        window.statusBarColor = page
        window.navigationBarColor = page
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@P2pNearbyTransferActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@P2pNearbyTransferActivity)
        }

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            setBackgroundColor(page)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val back = makeText("‹", 28f, R.color.sc_lavender, medium = true).apply {
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_student_connect_dashboard_tile)
            setPadding(0, 0, 0, dp(3))
            contentDescription = "Back"
            setOnClickListener {
                if (uiStage == NearbyUiStage.HISTORY) setUiStage(NearbyUiStage.HUB) else finish()
            }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        headerTitle = makeText("Nearby share", 18f, R.color.sc_text_primary, medium = true)
        headerSubtitle = makeText("Same Wi-Fi · no mobile data", 11f, R.color.sc_text_muted)
        titleBlock.addView(headerTitle)
        titleBlock.addView(headerSubtitle)
        header.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        rootLayout.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(10))
        }

        // Hub — matching the reference's concentric-phone illustration, primary Send and secondary Receive.
        hubPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        val orbitCard = makeCard(R.color.sc_hero, 26).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(176)
            ).apply { bottomMargin = dp(18) }
        }
        val orbitFrame = FrameLayout(this)
        val rings = ImageView(this).apply {
            setImageResource(R.drawable.bg_student_connect_dashboard_orb)
            imageTintList = ColorStateList.valueOf(color(R.color.sc_purple))
            alpha = 0.95f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        orbitFrame.addView(rings, FrameLayout.LayoutParams(dp(148), dp(148), Gravity.CENTER))
        val phone = ImageView(this).apply {
            setImageResource(R.drawable.ic_pt_device_mobile)
            imageTintList = ColorStateList.valueOf(color(R.color.sc_purple))
            contentDescription = null
        }
        orbitFrame.addView(phone, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        orbitCard.addView(orbitFrame)
        hubPanel.addView(orbitCard)

        hubPanel.addView(makeText(
            "Share files with classmates", 20f, R.color.sc_text_primary, medium = true
        ).apply { setPadding(0, 0, 0, dp(5)) })
        hubPanel.addView(makeText(
            "Both phones must be on the same Wi-Fi network and close to each other.",
            12f, R.color.sc_text_muted
        ).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, 0, 0, dp(16))
        })

        sendButton = makeButton("Send a file", primary = true) {
            if (!sending.get() && !receiving.get()) {
                requestNearbyWifiPermission(NearbyPermissionAction.SEND)
            }
        }
        receiveButton = makeButton("Receive a nearby file", primary = false) {
            requestNearbyWifiPermission(NearbyPermissionAction.RECEIVE)
        }
        hubPanel.addView(sendButton, fullWidthParams())
        hubPanel.addView(receiveButton, fullWidthParams(top = 8))
        hubPanel.addView(
            makeButton("Connection check", primary = false) { runConnectionCheck() },
            fullWidthParams(top = 8)
        )
        hubPanel.addView(
            makeButton("Transfer history", primary = false) { openTransferHistory() },
            fullWidthParams(top = 8)
        )

        val hubNote = makeInfoCard(
            "Files go directly phone to phone. Nothing is uploaded to the internet or Firebase. You will compare a six-digit code before anything is sent.",
            R.color.sc_sky
        )
        hubPanel.addView(hubNote, fullWidthParams(top = 16))
        content.addView(hubPanel, fullWidthParams())

        historyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        historyPanel.addView(makeText("Completed transfers", 18f, R.color.sc_text_primary, medium = true).apply {
            setPadding(0, 0, 0, dp(5))
        })
        historyPanel.addView(makeText(
            "Stored only on this phone. Open or share a file, or remove a history entry. Received files can also be deleted after confirmation.",
            12f, R.color.sc_text_muted
        ).apply { setLineSpacing(dp(2).toFloat(), 1f); setPadding(0, 0, 0, dp(12)) })
        historyItemsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyPanel.addView(historyItemsContainer, fullWidthParams())
        historyPanel.addView(
            makeButton("Back to Nearby Share", primary = false) { setUiStage(NearbyUiStage.HUB) },
            fullWidthParams(top = 12)
        )
        content.addView(historyPanel, fullWidthParams())

        // Discovery — sender can connect to a discovered receiver. Receiver advertises locally and waits.
        discoveryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val discoveryHero = makeCard(R.color.sc_card, 24).apply {
            layoutParams = fullWidthParams(bottom = 16)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        discoveryHero.setPadding(0, 0, 0, 0)
        val discoveryContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        discoveryHeading = makeText("Looking for nearby phones", 18f, R.color.sc_text_primary, medium = true)
        val discoveryCopy = makeText(
            "Keep both phones on the same Wi-Fi network and leave U2U open while connecting.",
            12f, R.color.sc_text_muted
        ).apply {
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        discoveryContent.addView(discoveryHeading)
        discoveryContent.addView(discoveryCopy)
        discoveryHero.addView(discoveryContent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        discoveryPanel.addView(discoveryHero)
        peerListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        discoveryPanel.addView(peerListContainer, fullWidthParams())
        queueSummaryText = makeText("", 11f, R.color.sc_text_muted).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
            background = getDrawable(R.drawable.bg_student_connect_inline_surface)
        }
        discoveryPanel.addView(queueSummaryText, fullWidthParams(top = 10))
        discoveryPanel.addView(
            makeInfoCard("No internet signaling is used for nearby transfer. Keep this screen open until the connection is ready.", R.color.sc_sky),
            fullWidthParams(top = 14)
        )
        discoveryPanel.addView(
            makeButton("Connection check", primary = false) { runConnectionCheck() },
            fullWidthParams(top = 8)
        )
        content.addView(discoveryPanel, fullWidthParams())

        // Verification content under the modal is intentionally neutral; the code itself is shown in the
        // existing confirmation dialog, whose accept/cancel callbacks remain unchanged.
        verifyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val verifyHero = makeCard(R.color.sc_hero, 24).apply { setPadding(0, 0, 0, 0) }
        val verifyContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        verifyContent.addView(makeText("SECURITY CODE", 10f, R.color.sc_button_secondary_text, medium = true))
        verifyContent.addView(makeText("Compare both phones", 20f, R.color.sc_purple, medium = true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        verifyContent.addView(makeText(
            "Only continue when every digit matches on the sender and receiver.",
            12f, R.color.sc_button_secondary_text
        ))
        verifyHero.addView(verifyContent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        verifyPanel.addView(verifyHero, fullWidthParams())
        content.addView(verifyPanel, fullWidthParams())

        // Transfer — bytes/progress are presentation only. Encrypted framing and SHA-256 logic below are untouched.
        progressPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val transferHero = makeCard(R.color.sc_hero, 26).apply { setPadding(0, 0, 0, 0) }
        val transferContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        progressTitle = makeText("SENDING", 10f, R.color.sc_button_secondary_text, medium = true)
        progressTitle.letterSpacing = 0.1f
        progressFileName = makeText("File", 13f, R.color.sc_button_secondary_text)
        progressFileName.setPadding(0, dp(6), 0, dp(10))
        progressPercent = makeText("0%", 42f, R.color.sc_purple, medium = true)
        progressPercent.letterSpacing = 0.06f
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(color(R.color.sc_purple))
            progressBackgroundTintList = ColorStateList.valueOf(color(R.color.sc_nav_icon))
            minimumHeight = dp(7)
        }
        progressAmounts = makeText("0 B of 0 B · Preparing…", 10f, R.color.sc_button_secondary_text)
        progressAmounts.setPadding(0, dp(8), 0, 0)
        transferContent.addView(progressTitle)
        transferContent.addView(progressFileName)
        transferContent.addView(progressPercent)
        transferContent.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(7)
        ).apply { topMargin = dp(10) })
        transferContent.addView(progressAmounts)
        transferHero.addView(transferContent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        progressPanel.addView(transferHero, fullWidthParams())
        progressPanel.addView(
            makeInfoCard("Keep both phones close and this screen open until the transfer finishes.", R.color.sc_sky),
            fullWidthParams(top = 14)
        )
        content.addView(progressPanel, fullWidthParams())

        statusCard = makeCard(R.color.sc_chat_status, 18).apply {
            visibility = View.GONE
            layoutParams = fullWidthParams(top = 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        statusText = makeText("", 12f, R.color.sc_text_muted)
        statusText.setLineSpacing(dp(2).toFloat(), 1f)
        statusText.maxLines = 5
        val statusContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        statusContent.addView(statusText)
        retryButton = makeButton("Try again", primary = true) {
            val action = lastNearbyAction
            if (action != null) requestNearbyWifiPermission(action) else runConnectionCheck()
        }.apply {
            minimumHeight = dp(40)
            cornerRadius = dp(12)
            textSize = 12f
            visibility = View.GONE
        }
        statusContent.addView(retryButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
        ).apply { topMargin = dp(8) })
        statusCard.addView(statusContent)
        // Keep state/reason text fixed below the scrollable panels so it remains visible in every stage.
        statusCard.visibility = View.GONE

        scroll.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        rootLayout.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        rootLayout.addView(statusCard, fullWidthParams(top = 8))

        transferActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        pauseResumeButton = makeButton("Pause", primary = false) { togglePauseResume() }
        stopButton = makeButton("Cancel", primary = false) { cancelCurrentAction() }.apply {
            backgroundTintList = ColorStateList.valueOf(color(R.color.sc_danger_bg))
            setTextColor(color(R.color.sc_danger))
        }
        transferActions.addView(pauseResumeButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(5)
        })
        transferActions.addView(stopButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(5)
        })
        rootLayout.addView(transferActions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setContentView(rootLayout)
        applyNearbyWindowInsets()
        applyUiStage(NearbyUiStage.HUB)
        refreshTransferActions()
    }

    private fun applyNearbyWindowInsets() {
        val baseLeft = rootLayout.paddingLeft
        val baseTop = rootLayout.paddingTop
        val baseRight = rootLayout.paddingRight
        val baseBottom = rootLayout.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + maxOf(bars.bottom, ime.bottom)
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(rootLayout)
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

    private fun makeText(
        value: String,
        sizeSp: Float,
        colorResource: Int,
        medium: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color(colorResource))
        includeFontPadding = false
        typeface = ResourcesCompat.getFont(
            this@P2pNearbyTransferActivity,
            if (medium) R.font.poppins_medium else R.font.poppins
        )
    }

    private fun makeCard(backgroundResource: Int, radiusDp: Int): MaterialCardView =
        MaterialCardView(this).apply {
            radius = dp(radiusDp).toFloat()
            cardElevation = 0f
            useCompatPadding = false
            strokeWidth = dp(1)
            setCardBackgroundColor(color(backgroundResource))
            setStrokeColor(color(R.color.sc_border))
        }

    private fun makeButton(label: String, primary: Boolean, click: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            includeFontPadding = false
            cornerRadius = dp(16)
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(50)
            setTextColor(color(if (primary) R.color.sc_button_primary_text else R.color.sc_button_secondary_text))
            backgroundTintList = ColorStateList.valueOf(
                color(if (primary) R.color.sc_purple else R.color.sc_button_secondary)
            )
            setStrokeColor(ColorStateList.valueOf(color(R.color.sc_border)))
            strokeWidth = dp(1)
            typeface = ResourcesCompat.getFont(
                this@P2pNearbyTransferActivity, R.font.poppins_medium
            )
            setOnClickListener { click() }
        }

    private fun fullWidthParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
        }

    private fun makeInfoCard(message: String, backgroundResource: Int): MaterialCardView {
        val card = makeCard(backgroundResource, 18).apply {
            setCardBackgroundColor(color(backgroundResource))
            setStrokeColor(color(backgroundResource))
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_pt_shield)
            imageTintList = ColorStateList.valueOf(color(R.color.sc_text_primary))
            contentDescription = null
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(19), dp(19)).apply {
            marginEnd = dp(8)
        })
        row.addView(makeText(message, 11f, R.color.sc_text_primary), LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        card.addView(row)
        return card
    }

    private fun requestNearbyWifiPermission(action: NearbyPermissionAction) {
        lastNearbyAction = action
        // Android 16's opt-in local-network protections temporarily use NEARBY_WIFI_DEVICES.
        // Avoid extra prompts on Android 13–15; this app still targets SDK 35, so Android 17+
        // retains legacy local-network access without ACCESS_LOCAL_NETWORK.
        if (Build.VERSION.SDK_INT != 36 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            performNearbyPermissionAction(action)
            return
        }
        pendingNearbyPermissionAction = action
        nearbyWifiPermissionRequest.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    private fun showNearbyWifiPermissionHelp(action: NearbyPermissionAction?) {
        val showSettings = !ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.NEARBY_WIFI_DEVICES
        )
        val message = "Nearby sharing needs local Wi-Fi permission so Android can discover and connect to the other phone on the same Wi-Fi or hotspot. It does not need internet access. Allow the permission to continue."
        val dialog = AlertDialog.Builder(this)
            .setTitle("Nearby Wi-Fi permission needed")
            .setMessage(message)
            .setNegativeButton("Not now", null)
        if (showSettings) {
            dialog.setPositiveButton("Open app settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        } else {
            dialog.setPositiveButton("Try again") { _, _ ->
                requestNearbyWifiPermission(action ?: lastNearbyAction ?: NearbyPermissionAction.RECEIVE)
            }
        }
        dialog.show()
    }

    private fun runConnectionCheck() {
        val wifiManager = getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiNetworkConnected = runCatching {
            connectivity?.allNetworks.orEmpty().any { network ->
                connectivity?.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }.getOrDefault(false)
        val localWifiAddress = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { network ->
                    network.isUp && !network.isLoopback &&
                        network.name.lowercase().let { name ->
                            name.startsWith("wlan") || name.startsWith("wifi") ||
                                name.startsWith("ap") || name.startsWith("swlan") ||
                                name.startsWith("softap")
                        }
                }
                .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { address ->
                    address.isSiteLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress
                }
                ?.hostAddress
        }.getOrNull()
        val nearbyPermissionGranted = Build.VERSION.SDK_INT != 36 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        val advice = P2pNearbyConnectionDiagnosis.advice(
            wifiEnabled = wifiEnabled,
            wifiNetworkConnected = wifiNetworkConnected,
            hasPrivateWifiAddress = !localWifiAddress.isNullOrBlank(),
            nearbyPermissionGranted = nearbyPermissionGranted
        )
        val diagnostic = buildString {
            appendLine("Wi-Fi radio: ${if (wifiEnabled) "enabled" else "disabled or managed by hotspot"}")
            appendLine("Connected Wi-Fi network: ${if (wifiNetworkConnected) "detected" else "not detected by Android"}")
            appendLine("Local Wi-Fi IPv4: ${localWifiAddress ?: "not detected"}")
            if (Build.VERSION.SDK_INT == 36) {
                appendLine("Nearby Wi-Fi permission: ${if (nearbyPermissionGranted) "granted" else "not granted"}")
            }
            appendLine()
            append(advice)
            appendLine()
            appendLine()
            append("This check cannot directly test whether a router isolates its connected devices. If the other phone is not found after 30 seconds, avoid guest Wi-Fi and disable client/AP isolation. As an alternative, enable a hotspot on one phone, connect the second phone to it, open Receive on one phone first, then Send on the other.")
        }
        AlertDialog.Builder(this)
            .setTitle("Nearby connection check")
            .setMessage(diagnostic)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun performNearbyPermissionAction(action: NearbyPermissionAction) {
        when (action) {
            NearbyPermissionAction.SEND -> outgoingFilePicker.launch(arrayOf("*/*"))
            NearbyPermissionAction.RECEIVE -> startReceiving()
        }
    }

    private fun setUiStage(stage: NearbyUiStage) {
        mainHandler.post {
            if (!isFinishing && !isDestroyed && ::hubPanel.isInitialized) {
                applyUiStage(stage)
            }
        }
    }

    private fun applyUiStage(stage: NearbyUiStage) {
        uiStage = stage
        hubPanel.visibility = if (stage == NearbyUiStage.HUB) View.VISIBLE else View.GONE
        historyPanel.visibility = if (stage == NearbyUiStage.HISTORY) View.VISIBLE else View.GONE
        discoveryPanel.visibility = if (stage == NearbyUiStage.DISCOVERY) View.VISIBLE else View.GONE
        verifyPanel.visibility = if (stage == NearbyUiStage.VERIFY) View.VISIBLE else View.GONE
        progressPanel.visibility = if (stage == NearbyUiStage.TRANSFER || stage == NearbyUiStage.COMPLETE) {
            View.VISIBLE
        } else View.GONE
        when (stage) {
            NearbyUiStage.HUB -> {
                headerTitle.text = "Nearby share"
                headerSubtitle.text = "Same Wi-Fi · no mobile data"
            }
            NearbyUiStage.DISCOVERY -> {
                headerTitle.text = if (transferIsOutgoing) "Find a phone" else "Nearby share"
                headerSubtitle.text = if (transferIsOutgoing) "Step 1 of 3 · Find a phone" else "Same Wi-Fi · waiting for a sender"
                discoveryHeading.text = if (transferIsOutgoing) {
                    "Looking for nearby phones"
                } else {
                    "Waiting for a nearby sender"
                }
            }
            NearbyUiStage.VERIFY -> {
                headerTitle.text = "Check the code"
                headerSubtitle.text = "Step 2 of 3 · Verify"
            }
            NearbyUiStage.TRANSFER, NearbyUiStage.COMPLETE -> {
                headerTitle.text = if (transferIsOutgoing) "Sending" else "Receiving"
                headerSubtitle.text = "Step 3 of 3 · Transfer"
            }
            NearbyUiStage.HISTORY -> {
                headerTitle.text = "Transfer history"
                headerSubtitle.text = "Completed files · stored locally"
            }
        }
        refreshTransferActions()
    }

    private fun refreshTransferActions() {
        val active = transferInProgress.get()
        mainHandler.post {
            if (isFinishing || isDestroyed || !::transferActions.isInitialized) return@post
            transferActions.visibility = if (active) View.VISIBLE else View.GONE
            pauseResumeButton.visibility =
                if (active && sending.get() && outgoingStreamActive.get() && !senderDataFinished.get()) {
                    View.VISIBLE
                } else View.GONE
            stopButton.visibility = if (active) View.VISIBLE else View.GONE
            pauseResumeButton.isEnabled = active && sending.get() && outgoingStreamActive.get() &&
                !senderDataFinished.get() && !pauseRequestInFlight.get()
            stopButton.isEnabled = active
        }
    }

    private fun beginTransferProgress(
        outgoing: Boolean,
        fileName: String,
        totalBytes: Long,
        resumeOffset: Long
    ) {
        transferIsOutgoing = outgoing
        transferFileName = fileName
        transferTotalBytes = totalBytes.coerceAtLeast(0L)
        transferStartOffset = resumeOffset.coerceAtLeast(0L)
        transferStartedAtMs = SystemClock.elapsedRealtime()
        transferInProgress.set(true)
        updateTransferProgress(transferStartOffset, transferTotalBytes, fileName, outgoing)
        setUiStage(NearbyUiStage.TRANSFER)
        refreshTransferActions()
    }

    private fun updateTransferProgress(bytes: Long, totalBytes: Long, fileName: String, outgoing: Boolean) {
        val total = totalBytes.coerceAtLeast(0L)
        val bounded = bytes.coerceAtLeast(0L).coerceAtMost(if (total > 0L) total else bytes.coerceAtLeast(0L))
        val percent = if (total > 0L) ((bounded * 100L) / total).toInt().coerceIn(0, 100) else 0
        val elapsedMs = (SystemClock.elapsedRealtime() - transferStartedAtMs).coerceAtLeast(1L)
        val rateBytes = ((bounded - transferStartOffset).coerceAtLeast(0L) * 1000L) / elapsedMs
        val amountText = P2pTransferFileMetadataReader.formatBytes(bounded) + " of " +
            P2pTransferFileMetadataReader.formatBytes(total)
        val speedText = if (transferStartedAtMs > 0L) {
            P2pTransferFileMetadataReader.formatBytes(rateBytes) + "/s"
        } else "Preparing…"
        mainHandler.post {
            if (isFinishing || isDestroyed || !::progressBar.isInitialized) return@post
            progressTitle.text = if (outgoing) "SENDING" else "RECEIVING"
            progressFileName.text = fileName + if (outgoing) "\nto nearby phone" else "\nfrom nearby phone"
            progressPercent.text = "$percent%"
            progressBar.progress = percent
            progressAmounts.text = "$amountText · $speedText"
        }
    }

    private fun showDiscoveredPeer(peer: P2pLanDiscovery.Peer) {
        if (!::peerListContainer.isInitialized) return
        peerListContainer.removeAllViews()
        val rowCard = makeCard(R.color.sc_card, 22).apply {
            setCardBackgroundColor(color(R.color.sc_card))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = makeText("P", 15f, R.color.sc_button_primary_text, medium = true).apply {
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_student_connect_profile_avatar)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(makeText("Nearby phone", 13f, R.color.sc_text_primary, medium = true))
        details.addView(makeText("Ready to receive · same Wi-Fi", 10f, R.color.sc_online).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val connect = makeButton("Connect", primary = true) { }
        connect.apply {
            minimumHeight = dp(40)
            cornerRadius = dp(12)
            textSize = 12f
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                val prepared = preparedOutgoing
                if (prepared != null && sending.get()) {
                    connect.isEnabled = false
                    showStatus("Connecting locally. Confirm the same six-digit code on both phones when prompted…")
                    ioExecutor.execute { sendToPeer(peer, prepared) }
                }
            }
        }
        row.addView(connect, LinearLayout.LayoutParams(dp(100), dp(42)))
        rowCard.addView(row)
        peerListContainer.addView(rowCard, fullWidthParams(top = 8))
        discoveryHeading.text = "Nearby phone found"
        showStatus("Nearby phone found. Tap Connect to establish a local connection.")
    }

    private fun prepareOutgoingQueue(selectedUris: List<Uri>) {
        val uniqueUris = selectedUris.distinctBy { it.toString() }
        if (uniqueUris.isEmpty()) {
            showStatus("No files were selected.")
            return
        }
        if (uniqueUris.size > MAX_QUEUE_FILES) {
            showStatus("Select up to $MAX_QUEUE_FILES files per nearby transfer queue.")
            return
        }
        val metadata = uniqueUris.map { uri ->
            runCatching { P2pTransferFileMetadataReader.read(this, uri) }.getOrNull()
        }
        val invalidIndex = metadata.indexOfFirst { it == null || it.sizeBytes <= 0L }
        if (invalidIndex >= 0) {
            clearOutgoingQueue()
            showStatus("Could not read file ${invalidIndex + 1}. Choose non-empty, readable files and retry.")
            return
        }
        uniqueUris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        outgoingQueueUris = uniqueUris
        outgoingQueueFileNames = metadata.map { it!!.fileName }
        outgoingQueueIndex = 0
        outgoingQueueActive = true
        refreshOutgoingQueueSummary()
        prepareOutgoingFile(uniqueUris.first())
    }

    private fun prepareOutgoingFile(uri: Uri) {
        val metadata = P2pTransferFileMetadataReader.read(this, uri)
        if (metadata == null || metadata.sizeBytes <= 0L) {
            clearOutgoingQueue()
            showStatus("Could not read this file or its size is unknown. Choose a non-empty file.")
            return
        }
        if (!sending.compareAndSet(false, true)) {
            showStatus("Another nearby transfer operation is already active.")
            return
        }
        transferIsOutgoing = true
        setUiStage(NearbyUiStage.DISCOVERY)
        setOperationUi(true)
        val queuePrefix = if (outgoingQueueActive) {
            "Queue file ${outgoingQueueIndex + 1} of ${outgoingQueueUris.size}: ${metadata.fileName}. "
        } else ""
        showStatus(queuePrefix + "Checking file integrity and preparing encrypted transfer…")
        refreshOutgoingQueueSummary()

        ioExecutor.execute {
            try {
                val digest = hashUri(metadata)
                if (!sending.get()) return@execute
                preparedOutgoing = PreparedOutgoing(metadata, digest)
                peerPromptShown = false
                val discovery = P2pLanDiscovery(
                    this,
                    object : P2pLanDiscovery.Callback {
                        override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) {
                            if (!sending.get() || peerPromptShown) return
                            if (!peer.sessionId.startsWith(NEARBY_SESSION_PREFIX)) return
                            val address = peer.host.hostAddress.orEmpty()
                            if (peer.host !is Inet4Address ||
                                !P2pTransferValidation.isPrivateLanAddress(address) ||
                                peer.port !in 1..65535
                            ) return
                            peerPromptShown = true
                            mainHandler.removeCallbacks(senderDiscoveryTimeout)
                            runOnUiThread {
                                if (isFinishing || isDestroyed || !sending.get()) return@runOnUiThread
                                showDiscoveredPeer(peer)
                            }
                        }

                        override fun onPeerLost(serviceName: String) = Unit
                        override fun onAdvertised(serviceName: String) = Unit
                        override fun onError(operation: String, errorCode: Int) {
                            if (!sending.get()) return
                            if (operation == "discover") {
                                cancelCurrentAction()
                                showStatus(
                                    "Nearby transfer cancelled. Discovery failed (code $errorCode). " +
                                        "Use the same non-guest Wi-Fi on both phones and ensure device/client isolation is off, then try again."
                                )
                            } else {
                                showStatus(
                                    "A nearby service could not be resolved (code $errorCode). " +
                                        "Still searching for a receiver on the same Wi-Fi…"
                                )
                            }
                        }
                    }
                )
                outgoingDiscovery = discovery
                runOnUiThread {
                    if (sending.get()) {
                        showStatus("Searching for a nearby receiver on the current Wi-Fi…")
                        if (!discovery.startDiscovery()) {
                            cancelCurrentAction()
                            showStatus("Could not start nearby discovery. Check Wi-Fi/hotspot and retry.")
                        } else {
                            mainHandler.removeCallbacks(senderDiscoveryTimeout)
                            mainHandler.postDelayed(senderDiscoveryTimeout, DISCOVERY_TIMEOUT_MS)
                        }
                    } else {
                        discovery.close()
                    }
                }
            } catch (error: Exception) {
                if (sending.get()) showStatus("Could not prepare this file: " + safeMessage(error))
                clearOutgoingQueue()
                finishSending()
            }
        }
    }

    private class NearbyUserCancelledException(message: String) : IOException(message)

    private fun sendToPeer(peer: P2pLanDiscovery.Peer, prepared: PreparedOutgoing) {
        val transferId = UUID.randomUUID().toString()
        lastSendSucceeded = false
        var lastError: Exception? = null
        try {
            for (attempt in 0..MAX_RECONNECT_ATTEMPTS) {
                if (!sending.get()) break
                try {
                    val refreshedPeer = outgoingDiscovery?.currentPeers()
                        ?.firstOrNull { candidate ->
                            candidate.sessionId == peer.sessionId &&
                                candidate.host is Inet4Address &&
                                P2pTransferValidation.isPrivateLanAddress(candidate.host.hostAddress.orEmpty()) &&
                                candidate.port in 1..65535
                        } ?: peer
                    sendOneNearbyAttempt(refreshedPeer, prepared, transferId)
                    lastSendSucceeded = true
                    lastError = null
                    break
                } catch (cancelled: NearbyUserCancelledException) {
                    lastError = null
                    showStatus(cancelled.message ?: "Nearby transfer cancelled.")
                    break
                } catch (error: Exception) {
                    lastError = error
                    if (!sending.get() || attempt >= MAX_RECONNECT_ATTEMPTS) break
                    showStatus(
                        "Local connection interrupted. Checking for a saved checkpoint and retrying " +
                            "(${attempt + 1}/${MAX_RECONNECT_ATTEMPTS})…"
                    )
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            if (lastError != null && sending.get()) {
                showStatus("Nearby transfer stopped after reconnect attempts: " + safeMessage(lastError!!))
            }
        } finally {
            transferInProgress.set(false)
            refreshTransferActions()
            outgoingStreamActive.set(false)
            outgoingPaused.set(false)
            senderDataFinished.set(true)
            activeOutgoingOutput = null
            pauseRequestInFlight.set(false)
            updatePauseResumeButton(enabled = false, paused = false)
            runCatching { activeSocket?.close() }
            activeSocket = null
            val queueCount = outgoingQueueUris.size
            val completedIndex = outgoingQueueIndex
            val wasQueueActive = outgoingQueueActive
            val queueItemSucceeded = lastSendSucceeded
            val nextUri = if (
                wasQueueActive && queueItemSucceeded && sending.get() && completedIndex + 1 < queueCount
            ) outgoingQueueUris[completedIndex + 1] else null
            val nextIndex = completedIndex + 1
            if (wasQueueActive) {
                if (nextUri != null) {
                    outgoingQueueIndex = nextIndex
                    refreshOutgoingQueueSummary()
                } else {
                    clearOutgoingQueue()
                }
            }
            finishSending()
            if (nextUri != null) {
                mainHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        showStatus("File ${completedIndex + 1} of $queueCount verified. Preparing file ${nextIndex + 1} of $queueCount…")
                        prepareOutgoingFile(nextUri)
                    }
                }, 500L)
            } else if (wasQueueActive && queueItemSucceeded) {
                mainHandler.post {
                    showStatus("All $queueCount queued file(s) sent and SHA-256 verified.")
                }
            }
        }
    }

    private fun sendOneNearbyAttempt(
        peer: P2pLanDiscovery.Peer,
        prepared: PreparedOutgoing,
        transferId: String
    ) {
        var socket: Socket? = null
        try {
            if (!sending.get()) throw NearbyUserCancelledException("Nearby transfer cancelled.")
            val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
            val offer = P2pNearbyOffer(
                transferId = transferId,
                fileName = prepared.metadata.fileName,
                sizeBytes = prepared.metadata.sizeBytes,
                mimeType = prepared.metadata.mimeType,
                sha256 = prepared.sha256,
                chunkSize = P2pEncryptedChunkStream.MAX_CHUNK_BYTES,
                senderPublicKeyBase64 = keyPair.publicKeyBase64()
            )
            if (!P2pNearbyTransferProtocol.isValidOffer(offer)) {
                throw NearbyUserCancelledException("The selected file metadata could not be validated.")
            }

            socket = Socket()
            activeSocket = socket
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            writeControl(output, "offer", mapOf(
                "transferId" to offer.transferId,
                "fileName" to offer.fileName,
                "sizeBytes" to offer.sizeBytes,
                "mimeType" to offer.mimeType,
                "sha256" to offer.sha256,
                "chunkSize" to offer.chunkSize,
                "senderPublicKey" to offer.senderPublicKeyBase64
            ))
            showStatus("Connected locally. Waiting for the receiver to approve or resume the file…")

            val acceptance = readControl(input)
            when (acceptance.optString("type")) {
                "reject" -> throw NearbyUserCancelledException("The receiver declined the nearby transfer.")
                "accept" -> Unit
                else -> throw IOException("Unexpected response from the receiver.")
            }
            val receiverPublicKey = acceptance.optString("receiverPublicKey")
            if (receiverPublicKey.length !in 40..132) {
                throw NearbyUserCancelledException("The receiver's public key is invalid.")
            }
            val resumeOffset = acceptance.optLong("resumeOffset", 0L)
            if (!P2pEncryptedChunkStream.isValidCheckpoint(
                    resumeOffset, offer.sizeBytes, offer.chunkSize
                )
            ) {
                throw NearbyUserCancelledException("Receiver supplied an invalid resume checkpoint.")
            }
            val key = P2pTransferCrypto.deriveTransferKey(keyPair, receiverPublicKey, transferId)
            val code = P2pNearbyTransferProtocol.confirmationCode(offer, receiverPublicKey)
            val resumeText = if (resumeOffset > 0L) {
                "\n\nSaved progress: " + P2pTransferFileMetadataReader.formatBytes(resumeOffset) +
                    ". The file will resume from this verified chunk checkpoint."
            } else {
                ""
            }
            if (!askForCodeConfirmation(
                    "Confirm nearby security code",
                    "File: " + offer.fileName + "\nSize: " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes) +
                        resumeText + "\n\nSecurity code: " + code +
                        "\n\nCompare all six digits with the receiver's phone. Continue only if they match exactly.",
                    "Codes match"
                )
            ) {
                runCatching { writeControl(output, "cancel") }
                throw NearbyUserCancelledException("Security code was not confirmed.")
            }
            writeControl(output, "sender_confirm")
            val receiverConfirmation = readControl(input)
            when (receiverConfirmation.optString("type")) {
                "cancel", "reject" -> throw NearbyUserCancelledException("The receiver cancelled security confirmation.")
                "receiver_confirm" -> Unit
                else -> throw IOException("Receiver security confirmation was not received.")
            }
            writeControl(output, "start")
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            activeOutgoingOutput = output
            outgoingPaused.set(false)
            senderDataFinished.set(false)
            outgoingStreamActive.set(true)
            pauseRequestInFlight.set(false)
            beginTransferProgress(true, offer.fileName, offer.sizeBytes, resumeOffset)
            updatePauseResumeButton(enabled = true, paused = false)
            if (resumeOffset > 0L) {
                showStatus(
                    "Resuming directly over local Wi-Fi from " +
                        P2pTransferFileMetadataReader.formatBytes(resumeOffset) + "…"
                )
            } else {
                showStatus("Sending directly over local Wi-Fi…")
            }

            val source = contentResolver.openInputStream(prepared.metadata.uri)
                ?: throw IOException("Android could not reopen the selected file.")
            val descriptor = P2pEncryptedChunkStream.TransferDescriptor(
                transferId = transferId,
                fileName = prepared.metadata.fileName,
                mimeType = prepared.metadata.mimeType,
                totalBytes = prepared.metadata.sizeBytes,
                sha256Hex = prepared.sha256,
                chunkSize = offer.chunkSize
            )
            val resumedSender = try {
                P2pEncryptedChunkStream.Sender(source, key, descriptor, resumeOffset)
            } catch (error: Exception) {
                runCatching { source.close() }
                throw error
            }
            resumedSender.use { sender ->
                var lastStatusBytes = resumeOffset
                while (true) {
                    var waitForResume = false
                    var frame: ByteArray? = null
                    synchronized(outgoingFrameLock) {
                        if (!sending.get()) throw NearbyUserCancelledException("Transfer cancelled.")
                        if (outgoingPaused.get()) {
                            waitForResume = true
                        } else {
                            val nextFrame = sender.nextFrame()
                            frame = nextFrame
                            if (nextFrame == null) {
                                senderDataFinished.set(true)
                                outgoingStreamActive.set(false)
                                updatePauseResumeButton(enabled = false, paused = false)
                            } else {
                                try {
                                    writePacket(output, P2pNearbyTransferProtocol.DATA_PACKET, nextFrame)
                                } finally {
                                    nextFrame.fill(0)
                                }
                            }
                        }
                    }
                    if (waitForResume) {
                        Thread.sleep(PAUSE_POLL_DELAY_MS)
                        continue
                    }
                    if (frame == null) break
                    val processed = sender.bytesProcessed
                    if (processed - lastStatusBytes >= STATUS_UPDATE_BYTES) {
                        lastStatusBytes = processed
                        updateTransferProgress(processed, offer.sizeBytes, offer.fileName, outgoing = true)
                        showStatus("Sending directly over local Wi-Fi… " +
                            P2pTransferFileMetadataReader.formatBytes(processed) + " / " +
                            P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes))
                    }
                }
                val endFrame = sender.finishFrame()
                try {
                    synchronized(outgoingFrameLock) {
                        senderDataFinished.set(true)
                        outgoingStreamActive.set(false)
                        updatePauseResumeButton(enabled = false, paused = false)
                        writePacket(output, P2pNearbyTransferProtocol.DATA_PACKET, endFrame)
                        sender.markEndFrameSent()
                    }
                } finally {
                    endFrame.fill(0)
                }
            }

            val receipt = readControl(input)
            if (receipt.optString("type") != "complete" ||
                !receipt.optString("sha256").equals(prepared.sha256, ignoreCase = true)
            ) {
                throw IOException("The receiver did not confirm the expected SHA-256 digest.")
            }
            val historySaved = runCatching {
                transferHistoryStore.add(
                    P2pNearbyTransferHistoryRecord(
                        transferId = transferId,
                        fileName = prepared.metadata.fileName,
                        mimeType = prepared.metadata.mimeType,
                        sizeBytes = prepared.metadata.sizeBytes,
                        completedAtEpochMillis = System.currentTimeMillis(),
                        direction = P2pNearbyTransferHistoryRecord.SENT,
                        contentUri = prepared.metadata.uri.toString()
                    )
                )
            }.isSuccess
            showStatus(
                "File sent and SHA-256 verified by the nearby receiver: " + offer.fileName +
                    if (historySaved) "" else " (local transfer history could not be saved)."
            )
            runOnUiThread {
                Toast.makeText(this, "Nearby transfer completed", Toast.LENGTH_LONG).show()
            }
        } finally {
            outgoingStreamActive.set(false)
            outgoingPaused.set(false)
            senderDataFinished.set(true)
            activeOutgoingOutput = null
            pauseRequestInFlight.set(false)
            updatePauseResumeButton(enabled = false, paused = false)
            runCatching { socket?.close() }
            if (activeSocket === socket) activeSocket = null
        }
    }

    private fun startReceiving() {
        if (sending.get()) {
            showStatus("Stop the current send attempt before receiving.")
            return
        }
        if (!receiving.compareAndSet(false, true)) {
            showStatus("Nearby reception is already active.")
            return
        }
        transferIsOutgoing = false
        setUiStage(NearbyUiStage.DISCOVERY)
        setReceiveUi(true)
        showStatus("Opening a temporary local receiver…")
        ioExecutor.execute {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", 0), 4)
                serverSocket = server
                if (!receiving.get()) {
                    runCatching { server.close() }
                    if (serverSocket === server) serverSocket = null
                    return@execute
                }
                val sessionId = NEARBY_SESSION_PREFIX + UUID.randomUUID().toString()
                val discovery = P2pLanDiscovery(
                    this,
                    object : P2pLanDiscovery.Callback {
                        override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) = Unit
                        override fun onPeerLost(serviceName: String) = Unit
                        override fun onAdvertised(serviceName: String) {
                            showStatus("Ready. Keep this screen open while the sender chooses your phone.")
                        }
                        override fun onError(operation: String, errorCode: Int) {
                            if (!receiving.get()) return
                            if (operation == "advertise") {
                                failReceiverAdvertising(
                                    "Could not advertise this phone on local Wi-Fi (code $errorCode). " +
                                        "Use the same non-guest Wi-Fi on both phones and ensure device/client isolation is off."
                                )
                            } else {
                                showStatus("Local receiver discovery reported an issue (code $errorCode). Keep this screen open and try again if no sender appears.")
                            }
                        }
                    }
                )
                advertisingDiscovery = discovery
                runOnUiThread {
                    if (receiving.get()) {
                        if (!discovery.advertise(sessionId, server.localPort, "StudentReceiver")) {
                            failReceiverAdvertising(
                                "Could not advertise this phone on local Wi-Fi. " +
                                    "Use the same non-guest Wi-Fi on both phones and ensure device/client isolation is off."
                            )
                        } else {
                            showStatus("Waiting for a nearby sender. Internet is not required for this transfer.")
                        }
                    } else {
                        discovery.close()
                    }
                }
                acceptLoop(server)
            } catch (error: Exception) {
                if (receiving.get()) showStatus("Could not start nearby reception: " + safeMessage(error))
                stopReceiving()
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (receiving.get() && !server.isClosed) {
            val socket = try {
                server.accept()
            } catch (error: Exception) {
                if (receiving.get()) showStatus("Nearby receiver stopped: " + safeMessage(error))
                return
            }
            if (!receiving.get() || !activeIncomingSocket.compareAndSet(false, true)) {
                runCatching { socket.close() }
                continue
            }
            ioExecutor.execute {
                try {
                    handleIncomingSocket(socket)
                } finally {
                    activeIncomingSocket.set(false)
                    if (activeSocket === socket) activeSocket = null
                    runCatching { socket.close() }
                }
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        var temp: File? = null
        var receiver: P2pEncryptedChunkStream.Receiver? = null
        var receivedOffer: P2pNearbyOffer? = null
        var preserveCheckpoint = false
        var completed = false
        try {
            activeSocket = socket
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val first = readPacket(input)
            if (first.kind != P2pNearbyTransferProtocol.CONTROL_PACKET ||
                first.payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES
            ) {
                first.payload.fill(0)
                writeControl(output, "reject", mapOf("reason" to "invalid_offer"))
                throw IOException("The nearby device sent an invalid offer.")
            }
            val offerJson = try {
                JSONObject(String(first.payload, Charsets.UTF_8))
            } finally {
                first.payload.fill(0)
            }
            if (offerJson.optString("type") != "offer") {
                writeControl(output, "reject", mapOf("reason" to "expected_offer"))
                throw IOException("No valid file offer was received.")
            }
            val offer = parseOffer(offerJson)
            receivedOffer = offer
            if (!P2pNearbyTransferProtocol.isValidOffer(offer)) {
                writeControl(output, "reject", mapOf("reason" to "invalid_metadata"))
                throw IOException("The incoming file metadata failed validation.")
            }

            val existingState = resumableDownloads[offer.transferId]
            if (existingState != null && !sameTransferMetadata(existingState.originalOffer, offer)) {
                writeControl(output, "reject", mapOf("reason" to "resume_metadata_mismatch"))
                throw IOException("The reconnect offer does not match the saved transfer.")
            }
            if (existingState != null &&
                (!existingState.tempFile.isFile ||
                    !P2pEncryptedChunkStream.isValidCheckpoint(
                        existingState.tempFile.length(), offer.sizeBytes, offer.chunkSize
                    ))
            ) {
                resumableDownloads.remove(offer.transferId, existingState)
                existingState.tempFile.delete()
                writeControl(output, "reject", mapOf("reason" to "invalid_saved_checkpoint"))
                throw IOException("The saved partial file is not at a valid chunk boundary.")
            }
            if (existingState != null) preserveCheckpoint = true

            val pending = PendingIncomingSession(socket, input, output, offer, existingState)
            pendingIncomingSession = pending
            runOnUiThread { showIncomingOffer(pending) }
            val decisionReady = pending.selectionReady.await(USER_DECISION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!decisionReady || !pending.accepted || pending.destinationUri == null) {
                runCatching { writeControl(output, "reject", mapOf("reason" to "declined")) }
                showStatus("Incoming nearby transfer was declined or timed out.")
                return
            }
            val destinationUri = pending.destinationUri
                ?: throw IOException("The save destination was not selected.")
            val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
            val key = P2pTransferCrypto.deriveTransferKey(
                keyPair, offer.senderPublicKeyBase64, offer.transferId
            )
            val downloadFile = existingState?.tempFile ?: checkpointStore.createTempFile()
            temp = downloadFile
            val resumeOffset = downloadFile.length()
            if (!P2pEncryptedChunkStream.isValidCheckpoint(
                    resumeOffset, offer.sizeBytes, offer.chunkSize
                )
            ) {
                throw IOException("Partial file size is not aligned to an authenticated chunk checkpoint.")
            }

            val resumeState = existingState ?: ResumableDownloadState(downloadFile, offer, destinationUri)
            resumeState.destinationUri = destinationUri
            checkpointStore.save(downloadFile, offer)
            resumableDownloads[offer.transferId] = resumeState
            temporaryDownload = downloadFile
            writeControl(output, "accept", mapOf(
                "receiverPublicKey" to keyPair.publicKeyBase64(),
                "resumeOffset" to resumeOffset
            ))
            val code = P2pNearbyTransferProtocol.confirmationCode(offer, keyPair.publicKeyBase64())
            val resumeText = if (resumeOffset > 0L) {
                "\n\nSaved progress: " + P2pTransferFileMetadataReader.formatBytes(resumeOffset) +
                    ". Existing verified chunks will be kept."
            } else {
                ""
            }
            if (!askForCodeConfirmation(
                    "Confirm nearby security code",
                    "Incoming file: " + offer.fileName + "\nSize: " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes) +
                        resumeText + "\n\nSecurity code: " + code +
                        "\n\nCompare all six digits with the sender's phone. Continue only if they match exactly.",
                    "Codes match"
                )
            ) {
                runCatching { writeControl(output, "cancel") }
                throw NearbyUserCancelledException("Security code was not confirmed.")
            }
            writeControl(output, "receiver_confirm")
            val senderConfirmation = readControl(input)
            when (senderConfirmation.optString("type")) {
                "cancel", "reject" -> throw NearbyUserCancelledException("The sender cancelled security confirmation.")
                "sender_confirm" -> Unit
                else -> throw IOException("Sender security confirmation was not received.")
            }
            val start = readControl(input)
            if (start.optString("type") != "start") {
                throw IOException("The sender did not start the approved transfer.")
            }
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            beginTransferProgress(false, offer.fileName, offer.sizeBytes, resumeOffset)
            showStatus(
                if (resumeOffset > 0L) "Resuming encrypted file over local Wi-Fi…"
                else "Receiving encrypted file directly over local Wi-Fi…"
            )

            val descriptor = P2pEncryptedChunkStream.TransferDescriptor(
                transferId = offer.transferId,
                fileName = offer.fileName,
                mimeType = offer.mimeType,
                totalBytes = offer.sizeBytes,
                sha256Hex = offer.sha256,
                chunkSize = offer.chunkSize
            )
            val outputFile = FileOutputStream(downloadFile, resumeOffset > 0L)
            receiver = try {
                P2pEncryptedChunkStream.Receiver(
                    outputFile,
                    key,
                    descriptor,
                    initialBytes = resumeOffset,
                    prefixInput = if (resumeOffset > 0L) FileInputStream(downloadFile) else null
                )
            } catch (error: Exception) {
                runCatching { outputFile.close() }
                throw error
            }
            var lastStatusBytes = resumeOffset
            var remotePaused = false
            while (!receiver.isComplete) {
                val packet = readPacket(input)
                if (packet.kind == P2pNearbyTransferProtocol.CONTROL_PACKET) {
                    val control = try {
                        JSONObject(String(packet.payload, Charsets.UTF_8))
                    } finally {
                        packet.payload.fill(0)
                    }
                    val controlType = control.optString("type")
                    when {
                        controlType == "cancel" -> throw NearbyUserCancelledException("The sender cancelled the transfer.")
                        controlType == "error" -> throw IOException("The sender reported a transfer error.")
                        controlType == P2pNearbyTransferProtocol.CONTROL_PAUSE && !remotePaused -> {
                            remotePaused = true
                            socket.soTimeout = 0
                            showStatus("Transfer paused by sender. Waiting for Resume…")
                        }
                        controlType == P2pNearbyTransferProtocol.CONTROL_RESUME && remotePaused -> {
                            remotePaused = false
                            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                            showStatus("Transfer resumed. Receiving encrypted file over local Wi-Fi…")
                        }
                        else -> throw IOException("Unexpected control packet during file transfer.")
                    }
                    continue
                }
                if (packet.kind != P2pNearbyTransferProtocol.DATA_PACKET) {
                    packet.payload.fill(0)
                    throw IOException("Unexpected frame type during transfer.")
                }
                try {
                    receiver.acceptFrame(packet.payload)
                } finally {
                    packet.payload.fill(0)
                }
                val receivedBytes = receiver.bytesReceived
                if (receivedBytes - lastStatusBytes >= STATUS_UPDATE_BYTES) {
                    lastStatusBytes = receivedBytes
                    updateTransferProgress(receivedBytes, offer.sizeBytes, offer.fileName, outgoing = false)
                    showStatus("Receiving encrypted file… " +
                        P2pTransferFileMetadataReader.formatBytes(receivedBytes) + " / " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes))
                }
            }
            receiver.finish()
            receiver.close()
            receiver = null
            promoteVerifiedFile(downloadFile, destinationUri, offer.sizeBytes)
            writeControl(output, "complete", mapOf("sha256" to offer.sha256))
            completed = true
            resumableDownloads.remove(offer.transferId, resumeState)
            checkpointStore.remove(offer.transferId)
            val historySaved = runCatching {
                transferHistoryStore.add(
                    P2pNearbyTransferHistoryRecord(
                        transferId = offer.transferId,
                        fileName = offer.fileName,
                        mimeType = offer.mimeType,
                        sizeBytes = offer.sizeBytes,
                        completedAtEpochMillis = System.currentTimeMillis(),
                        direction = P2pNearbyTransferHistoryRecord.RECEIVED,
                        contentUri = destinationUri.toString()
                    )
                )
            }.isSuccess
            showStatus(
                "File received and SHA-256 verified over local Wi-Fi: " + offer.fileName +
                    if (historySaved) "" else " (local transfer history could not be saved)."
            )
            runOnUiThread {
                Toast.makeText(this, "Nearby file received and verified", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            runCatching { receiver?.close() }
            receiver = null
            val offer = receivedOffer
            val checkpoint = temp
            val savedState = offer?.let { resumableDownloads[it.transferId] }
            if (!completed && offer != null && checkpoint != null &&
                savedState != null && checkpoint.isFile &&
                P2pEncryptedChunkStream.isValidCheckpoint(
                    checkpoint.length(), offer.sizeBytes, offer.chunkSize
                )
            ) {
                preserveCheckpoint = true
                if (receiving.get()) {
                    showStatus(
                        "Connection interrupted. Saved checkpoint: " +
                            P2pTransferFileMetadataReader.formatBytes(checkpoint.length()) +
                            ". Keep Receive nearby file active while the sender reconnects."
                    )
                }
            } else if (checkpoint != null && offer != null && savedState != null) {
                resumableDownloads.remove(offer.transferId, savedState)
                checkpointStore.remove(offer.transferId)
            }
            if (receiving.get()) {
                LogNearbyTransfer.error("Nearby receiver connection ended", error)
            }
            runCatching { writeControl(
                DataOutputStream(BufferedOutputStream(socket.getOutputStream())),
                "error",
                mapOf("reason" to safeMessage(error).take(180))
            ) }
        } finally {
            transferInProgress.set(false)
            refreshTransferActions()
            if (completed) {
                receivedOffer?.let {
                    resumableDownloads.remove(it.transferId)
                    checkpointStore.remove(it.transferId)
                }
                temp?.delete()
            } else if (!preserveCheckpoint && temp != null) {
                receivedOffer?.let {
                    resumableDownloads.remove(it.transferId)
                    checkpointStore.remove(it.transferId)
                }
                temp.delete()
            }
            if (temporaryDownload === temp) temporaryDownload = null
            if (pendingIncomingSession?.socket === socket) pendingIncomingSession = null
        }
    }

    private fun showIncomingOffer(pending: PendingIncomingSession) {
        if (isFinishing || isDestroyed || !receiving.get()) {
            pending.accepted = false
            pending.selectionReady.countDown()
            return
        }
        val resumeState = pending.resumeState
        val resumeOffset = resumeState?.tempFile?.length() ?: 0L
        val isResume = resumeState != null
        val canReuseDestination = isResume && resumeState?.destinationUri != null
        val offerCard = makeCard(R.color.sc_card, 22).apply { setPadding(0, 0, 0, 0) }
        val offerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        offerContent.addView(makeText(pending.offer.fileName, 15f, R.color.sc_text_primary, medium = true))
        offerContent.addView(makeText(
            P2pTransferFileMetadataReader.formatBytes(pending.offer.sizeBytes) + " · " + pending.offer.mimeType,
            11f, R.color.sc_text_muted
        ).apply { setPadding(0, dp(5), 0, dp(10)) })
        offerContent.addView(makeInfoCard(
            if (isResume) {
                "Saved progress: " + P2pTransferFileMetadataReader.formatBytes(resumeOffset) +
                    ". Resume from the verified encrypted checkpoint."
            } else {
                "Only accept if you expected this file from the person next to you."
            },
            R.color.sc_sky
        ))
        offerCard.addView(offerContent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        AlertDialog.Builder(this)
            .setTitle(if (isResume) "Resume nearby file" else "Incoming nearby file")
            .setView(offerCard)
            .setPositiveButton(
                if (!isResume) "Accept & choose save location"
                else if (canReuseDestination) "Resume transfer"
                else "Resume & choose save location"
            ) { _, _ ->
                if (canReuseDestination) {
                    pending.destinationUri = resumeState?.destinationUri
                    pending.accepted = pending.destinationUri != null
                    pending.selectionReady.countDown()
                } else {
                    pendingIncomingSession = pending
                    incomingSavePicker.launch(pending.offer.fileName)
                }
            }
            .setNegativeButton("Decline") { _, _ ->
                pending.accepted = false
                pending.selectionReady.countDown()
            }
            .setOnCancelListener {
                pending.accepted = false
                pending.selectionReady.countDown()
            }
            .show()
    }

    private fun askForCodeConfirmation(
        title: String,
        message: String,
        positiveLabel: String
    ): Boolean {
        val latch = CountDownLatch(1)
        val confirmed = AtomicBoolean(false)
        val code = Regex("""Security code:\s*([0-9]{3}\s*[0-9]{3})""")
            .find(message)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ") ?: "••• •••"
        val details = message
            .replace(Regex("""(?m)^Security code:\s*[0-9]{3}\s*[0-9]{3}\s*$"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        mainHandler.post {
            if (isFinishing || isDestroyed) {
                latch.countDown()
                return@post
            }
            applyUiStage(NearbyUiStage.VERIFY)
            val dialogContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(6))
            }
            val codeCard = makeCard(R.color.sc_hero, 24).apply {
                setCardBackgroundColor(color(R.color.sc_hero))
                setStrokeColor(color(R.color.sc_hero))
                setPadding(0, 0, 0, 0)
            }
            val codeContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            codeContent.addView(makeText("SECURITY CODE", 10f, R.color.sc_button_secondary_text, medium = true))
            codeContent.addView(makeText(code, 38f, R.color.sc_purple, medium = true).apply {
                letterSpacing = 0.13f
                setPadding(0, dp(8), 0, dp(8))
                gravity = Gravity.CENTER
            })
            codeContent.addView(makeText(
                "Compare all six digits on both phones.", 11f, R.color.sc_button_secondary_text
            ))
            codeCard.addView(codeContent, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            dialogContent.addView(codeCard, fullWidthParams(bottom = 12))
            val detailsCard = makeCard(R.color.sc_card, 18).apply {
                setCardBackgroundColor(color(R.color.sc_card))
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            detailsCard.addView(makeText(details, 12f, R.color.sc_text_primary).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            })
            dialogContent.addView(detailsCard, fullWidthParams())

            AlertDialog.Builder(this)
                .setTitle("Check the code")
                .setView(dialogContent)
                .setPositiveButton(positiveLabel) { _, _ ->
                    confirmed.set(true)
                    latch.countDown()
                }
                .setNegativeButton("Cancel transfer") { _, _ ->
                    confirmed.set(false)
                    latch.countDown()
                }
                .setOnCancelListener {
                    confirmed.set(false)
                    latch.countDown()
                }
                .show()
        }
        return latch.await(CONFIRMATION_TIMEOUT_MINUTES, TimeUnit.MINUTES) && confirmed.get()
    }

    private fun sameTransferMetadata(first: P2pNearbyOffer, second: P2pNearbyOffer): Boolean =
        first.transferId == second.transferId &&
            first.fileName == second.fileName &&
            first.sizeBytes == second.sizeBytes &&
            first.mimeType == second.mimeType &&
            first.sha256.equals(second.sha256, ignoreCase = true) &&
            first.chunkSize == second.chunkSize

    private fun parseOffer(json: JSONObject): P2pNearbyOffer = P2pNearbyOffer(
        transferId = json.optString("transferId"),
        fileName = json.optString("fileName"),
        sizeBytes = json.optLong("sizeBytes", -1L),
        mimeType = json.optString("mimeType"),
        sha256 = json.optString("sha256"),
        chunkSize = json.optInt("chunkSize", -1),
        senderPublicKeyBase64 = json.optString("senderPublicKey")
    )

    private fun hashUri(metadata: P2pTransferFileMetadata): String {
        val input = contentResolver.openInputStream(metadata.uri)
            ?: throw IOException("Android could not read the selected file.")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { stream ->
            val buffer = ByteArray(32 * 1024)
            try {
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total = Math.addExact(total, count.toLong())
                    if (total > metadata.sizeBytes) throw IOException("The selected file changed while being prepared.")
                    digest.update(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        if (total != metadata.sizeBytes) throw IOException("The selected file size changed while being prepared.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun promoteVerifiedFile(temp: File, destinationUri: Uri, expectedSize: Long) {
        if (!temp.isFile || temp.length() != expectedSize) {
            throw IOException("The verified temporary file has an unexpected length.")
        }
        val source = FileInputStream(temp)
        val destination = contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("Android could not open the selected save destination.")
        source.use { input ->
            destination.use { output ->
                input.copyTo(output, 64 * 1024)
                output.flush()
            }
        }
    }

    private fun writeControl(
        output: DataOutputStream,
        type: String,
        values: Map<String, Any?> = emptyMap()
    ) {
        val json = JSONObject()
        json.put("type", type)
        values.forEach { (key, value) -> if (value != null) json.put(key, value) }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        try {
            if (payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES) {
                throw IOException("Nearby control message is too large.")
            }
            writePacket(output, P2pNearbyTransferProtocol.CONTROL_PACKET, payload)
        } finally {
            payload.fill(0)
        }
    }

    private fun readControl(input: DataInputStream): JSONObject {
        val packet = readPacket(input)
        if (packet.kind != P2pNearbyTransferProtocol.CONTROL_PACKET ||
            packet.payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES
        ) {
            packet.payload.fill(0)
            throw IOException("Expected a bounded nearby control message.")
        }
        return try {
            JSONObject(String(packet.payload, Charsets.UTF_8))
        } finally {
            packet.payload.fill(0)
        }
    }

    private fun writePacket(output: DataOutputStream, kind: Int, payload: ByteArray) {
        if (payload.isEmpty() || payload.size > P2pNearbyTransferProtocol.MAX_PACKET_BYTES) {
            throw IOException("Nearby packet size is outside protocol limits.")
        }
        output.writeByte(kind)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readPacket(input: DataInputStream): Packet {
        val kind = input.readUnsignedByte()
        val length = input.readInt()
        if (length !in 1..P2pNearbyTransferProtocol.MAX_PACKET_BYTES) {
            throw IOException("Nearby packet length is invalid.")
        }
        val payload = ByteArray(length)
        input.readFully(payload)
        return Packet(kind, payload)
    }

    private fun togglePauseResume() {
        if (!sending.get() || !outgoingStreamActive.get() || senderDataFinished.get()) return
        if (!pauseRequestInFlight.compareAndSet(false, true)) return
        pauseResumeButton.isEnabled = false
        ioExecutor.execute {
            var nextPausedState: Boolean? = null
            try {
                synchronized(outgoingFrameLock) {
                    val output = activeOutgoingOutput
                    if (!sending.get() || !outgoingStreamActive.get() || senderDataFinished.get() || output == null) {
                        return@synchronized
                    }
                    val shouldPause = !outgoingPaused.get()
                    writeControl(
                        output,
                        if (shouldPause) P2pNearbyTransferProtocol.CONTROL_PAUSE
                        else P2pNearbyTransferProtocol.CONTROL_RESUME
                    )
                    outgoingPaused.set(shouldPause)
                    nextPausedState = shouldPause
                }
                nextPausedState?.let { paused ->
                    updatePauseResumeButton(enabled = true, paused = paused)
                    showStatus(
                        if (paused) "Transfer paused at a safe encrypted-frame boundary."
                        else "Transfer resumed over local Wi-Fi…"
                    )
                }
            } catch (error: Exception) {
                if (sending.get()) {
                    showStatus("Pause/Resume failed: " + safeMessage(error))
                    sending.set(false)
                    runCatching { activeSocket?.close() }
                }
            } finally {
                pauseRequestInFlight.set(false)
                if (outgoingStreamActive.get() && !senderDataFinished.get() && sending.get()) {
                    updatePauseResumeButton(enabled = true, paused = outgoingPaused.get())
                }
            }
        }
    }

    private fun updatePauseResumeButton(enabled: Boolean, paused: Boolean) {
        mainHandler.post {
            if (!isFinishing && !isDestroyed && ::pauseResumeButton.isInitialized) {
                pauseResumeButton.text = if (paused) "Resume" else "Pause"
                pauseResumeButton.isEnabled = enabled && !pauseRequestInFlight.get()
                refreshTransferActions()
            }
        }
    }

    private fun cancelCurrentAction() {
        mainHandler.removeCallbacks(senderDiscoveryTimeout)
        clearOutgoingQueue()
        transferInProgress.set(false)
        refreshTransferActions()
        if (receiving.get()) {
            stopReceiving()
            showStatus("Nearby reception stopped.")
            return
        }
        sending.set(false)
        outgoingStreamActive.set(false)
        outgoingPaused.set(false)
        senderDataFinished.set(true)
        activeOutgoingOutput = null
        outgoingDiscovery?.close()
        outgoingDiscovery = null
        updatePauseResumeButton(enabled = false, paused = false)
        runCatching { activeSocket?.close() }
        activeSocket = null
        finishSending()
        showStatus("Nearby transfer cancelled.")
    }

    private fun failReceiverAdvertising(message: String) {
        receiving.set(false)
        runCatching { advertisingDiscovery?.close() }
        advertisingDiscovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        setReceiveUi(false)
        setUiStage(NearbyUiStage.HUB)
        showStatus(message)
        // Keep resumable checkpoints intact; an advertising failure is not a user-requested reset.
    }

    private fun stopReceiving() {
        transferInProgress.set(false)
        refreshTransferActions()
        receiving.set(false)
        advertisingDiscovery?.close()
        advertisingDiscovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        val pending = pendingIncomingSession
        if (pending != null) {
            pending.accepted = false
            pending.selectionReady.countDown()
            pendingIncomingSession = null
        }
        clearResumableDownloads()
        if (!isFinishing && !isDestroyed) setReceiveUi(false)
    }

    private fun clearResumableDownloads() {
        resumableDownloads.entries.toList().forEach { entry ->
            runCatching { entry.value.tempFile.delete() }
            checkpointStore.remove(entry.key)
        }
        resumableDownloads.clear()
    }

    private fun finishSending() {
        mainHandler.removeCallbacks(senderDiscoveryTimeout)
        sending.set(false)
        outgoingDiscovery?.close()
        outgoingDiscovery = null
        preparedOutgoing = null
        runOnUiThread {
            if (!isFinishing && !isDestroyed && !receiving.get()) setOperationUi(false)
        }
    }

    private fun setOperationUi(active: Boolean) {
        sendButton.isEnabled = !active && !receiving.get()
        refreshRetryActionVisibility()
        receiveButton.isEnabled = !active && !receiving.get()
        refreshTransferActions()
        updatePauseResumeButton(
            enabled = active && transferInProgress.get() && outgoingStreamActive.get() && !senderDataFinished.get(),
            paused = outgoingPaused.get()
        )
    }

    private fun setReceiveUi(active: Boolean) {
        sendButton.isEnabled = !active && !sending.get()
        refreshRetryActionVisibility()
        receiveButton.isEnabled = !active && !sending.get()
        refreshTransferActions()
        updatePauseResumeButton(enabled = false, paused = false)
    }

    private fun showStatus(message: String) {
        mainHandler.post {
            if (isFinishing || isDestroyed || !::statusText.isInitialized) return@post
            statusText.text = message
            statusCard.visibility = if (message.isNotBlank()) View.VISIBLE else View.GONE
            val lowerMessage = message.lowercase()
            val userActionNeeded = listOf(
                "could not", "failed", "cancelled", "declined", "not confirmed",
                "unavailable", "invalid", "interrupted", "error", "not sent",
                "not accepted", "stopped", "did not", "timed out", "no nearby",
                "not found", "not readable", "denied"
            ).any { lowerMessage.contains(it) }
            lastStatusAllowsRetry = userActionNeeded
            if (userActionNeeded && message.isNotBlank()) {
                val compactMessage = message.lineSequence().firstOrNull()?.trim().orEmpty().take(120)
                if (compactMessage.isNotBlank()) {
                    Toast.makeText(this, compactMessage, Toast.LENGTH_SHORT).show()
                }
            }
            statusText.setTextColor(color(
                if (userActionNeeded) R.color.sc_danger else R.color.sc_text_muted
            ))
            refreshRetryActionVisibility()
            when {
                message.startsWith("File sent and SHA-256 verified") ||
                    message.startsWith("File received and SHA-256 verified") -> {
                    if (transferTotalBytes > 0L) {
                        updateTransferProgress(transferTotalBytes, transferTotalBytes, transferFileName, transferIsOutgoing)
                    }
                    transferInProgress.set(false)
                    applyUiStage(NearbyUiStage.COMPLETE)
                }
                message.startsWith("Searching for a nearby receiver") ||
                    message.startsWith("Checking file integrity") ||
                    message.startsWith("Opening a temporary local receiver") ||
                    message.startsWith("Ready. Keep this screen open") ||
                    message.startsWith("Waiting for a nearby sender") ||
                    message.startsWith("Connected locally") -> applyUiStage(NearbyUiStage.DISCOVERY)
                message.startsWith("Sending directly over local Wi-Fi") ||
                    message.startsWith("Receiving encrypted file") ||
                    message.startsWith("Resuming encrypted file") ||
                    message.startsWith("Transfer paused") ||
                    message.startsWith("Transfer resumed") -> {
                    if (transferInProgress.get()) applyUiStage(NearbyUiStage.TRANSFER)
                }
                message.startsWith("File selection cancelled") ||
                    message.startsWith("Nearby reception stopped") ||
                    message.startsWith("Nearby transfer cancelled") -> {
                    if (!transferInProgress.get()) applyUiStage(NearbyUiStage.HUB)
                }
            }
            refreshTransferActions()
        }
    }

    private fun clearOutgoingQueue() {
        outgoingQueueUris = emptyList()
        outgoingQueueFileNames = emptyList()
        outgoingQueueIndex = 0
        outgoingQueueActive = false
        refreshOutgoingQueueSummary()
    }

    private fun refreshOutgoingQueueSummary() {
        if (!::queueSummaryText.isInitialized) return
        mainHandler.post {
            if (isFinishing || isDestroyed || !::queueSummaryText.isInitialized) return@post
            if (!outgoingQueueActive || outgoingQueueFileNames.isEmpty()) {
                queueSummaryText.visibility = View.GONE
                queueSummaryText.text = ""
                return@post
            }
            queueSummaryText.visibility = View.VISIBLE
            queueSummaryText.text = buildString {
                append("TRANSFER QUEUE · ")
                append(outgoingQueueFileNames.size)
                append(if (outgoingQueueFileNames.size == 1) " file" else " files")
                outgoingQueueFileNames.forEachIndexed { index, fileName ->
                    append("\n")
                    append(when {
                        index < outgoingQueueIndex -> "✓ "
                        index == outgoingQueueIndex -> "› "
                        else -> "○ "
                    })
                    append(fileName)
                    append(" · ")
                    append(when {
                        index < outgoingQueueIndex -> "verified"
                        index == outgoingQueueIndex -> "current"
                        else -> "waiting"
                    })
                }
                if (outgoingQueueUris.size > 1) {
                    append("\nEach file is sent and verified separately; tap Connect for each queued file and compare its security code.")
                }
            }
        }
    }

    private fun openTransferHistory() {
        renderTransferHistory()
        setUiStage(NearbyUiStage.HISTORY)
    }

    private fun renderTransferHistory() {
        if (!::historyItemsContainer.isInitialized) return
        historyItemsContainer.removeAllViews()
        val records = runCatching { transferHistoryStore.list() }.getOrDefault(emptyList())
        if (records.isEmpty()) {
            historyItemsContainer.addView(makeInfoCard(
                "No completed nearby transfers yet. Successfully sent and received files will appear here.",
                R.color.sc_sky
            ), fullWidthParams())
            return
        }
        records.forEach { record ->
            val card = makeCard(R.color.sc_card, 18).apply {
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(makeText(record.fileName, 13f, R.color.sc_text_primary, medium = true))
            val date = runCatching {
                SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                    .format(Date(record.completedAtEpochMillis))
            }.getOrDefault("Completed transfer")
            val direction = if (record.direction == P2pNearbyTransferHistoryRecord.SENT) "Sent" else "Received"
            column.addView(makeText(
                "$direction · ${P2pTransferFileMetadataReader.formatBytes(record.sizeBytes)} · $date",
                10f, R.color.sc_text_muted
            ).apply { setPadding(0, dp(4), 0, dp(9)) })
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val open = makeButton("Open", primary = true) { openHistoryRecord(record) }
            val share = makeButton("Share", primary = false) { shareHistoryRecord(record) }
            val deleteLabel = if (record.direction == P2pNearbyTransferHistoryRecord.RECEIVED) "Delete" else "Remove"
            val delete = makeButton(deleteLabel, primary = false) { confirmDeleteHistoryRecord(record) }
            listOf(open, share, delete).forEachIndexed { index, button ->
                actions.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }
            column.addView(actions)
            card.addView(column)
            historyItemsContainer.addView(card, fullWidthParams(top = 8))
        }
    }

    private fun openHistoryRecord(record: P2pNearbyTransferHistoryRecord) {
        try {
            val uri = Uri.parse(record.contentUri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, record.fileName, uri)
            }
            startActivity(intent)
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not open file")
                .setMessage("The file may have been moved, deleted, or its document permission may have expired. ${safeMessage(error)}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun shareHistoryRecord(record: P2pNearbyTransferHistoryRecord) {
        try {
            val uri = Uri.parse(record.contentUri)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = record.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, record.fileName, uri)
            }
            startActivity(Intent.createChooser(sendIntent, "Share ${record.fileName}"))
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not share file")
                .setMessage("The file may have been moved, deleted, or its document permission may have expired. ${safeMessage(error)}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun confirmDeleteHistoryRecord(record: P2pNearbyTransferHistoryRecord) {
        if (record.direction == P2pNearbyTransferHistoryRecord.RECEIVED) {
            AlertDialog.Builder(this)
                .setTitle("Delete received file?")
                .setMessage("Choose whether to delete the saved document from its storage provider or only remove this history entry.")
                .setPositiveButton("Delete file and history") { _, _ -> removeHistoryRecord(record, deleteSavedFile = true) }
                .setNeutralButton("History only") { _, _ -> removeHistoryRecord(record, deleteSavedFile = false) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Remove transfer history?")
                .setMessage("The original sent file belongs to its existing storage location. This only removes its local history entry.")
                .setPositiveButton("Remove entry") { _, _ -> removeHistoryRecord(record, deleteSavedFile = false) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun removeHistoryRecord(record: P2pNearbyTransferHistoryRecord, deleteSavedFile: Boolean) {
        if (deleteSavedFile) {
            val uri = Uri.parse(record.contentUri)
            val deleted = try {
                if (DocumentsContract.isDocumentUri(this, uri)) {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                } else {
                    contentResolver.delete(uri, null, null) > 0
                }
            } catch (_: Exception) {
                false
            }
            if (!deleted) {
                AlertDialog.Builder(this)
                    .setTitle("File was not deleted")
                    .setMessage("Android's storage provider could not delete this document. The file is still kept; you can remove only the history entry instead.")
                    .setPositiveButton("Remove history only") { _, _ -> removeHistoryRecord(record, deleteSavedFile = false) }
                    .setNegativeButton("Keep entry", null)
                    .show()
                return
            }
        }
        val removed = runCatching { transferHistoryStore.remove(record.transferId) }.isSuccess
        renderTransferHistory()
        showStatus(
            when {
                !removed -> "The file action finished, but the local history entry could not be removed."
                deleteSavedFile -> "Saved file deleted and history entry removed."
                else -> "Transfer history entry removed."
            }
        )
    }

    private fun refreshRetryActionVisibility() {
        if (!::retryButton.isInitialized) return
        retryButton.visibility = if (
            lastStatusAllowsRetry && lastNearbyAction != null && !sending.get() && !receiving.get()
        ) View.VISIBLE else View.GONE
    }

    private fun safeMessage(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() }?.take(180) ?: "Please retry on the same Wi-Fi."

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        val pending = pendingIncomingSession
        if (pending != null) {
            pending.accepted = false
            pending.selectionReady.countDown()
        }
        receiving.set(false)
        sending.set(false)
        outgoingStreamActive.set(false)
        outgoingPaused.set(false)
        senderDataFinished.set(true)
        activeOutgoingOutput = null
        updatePauseResumeButton(enabled = false, paused = false)
        runCatching { advertisingDiscovery?.close() }
        runCatching { outgoingDiscovery?.close() }
        advertisingDiscovery = null
        outgoingDiscovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        // Saved partial files intentionally survive Activity destruction. Explicitly stopping
        // reception still clears checkpoints through stopReceiving()/clearResumableDownloads().
        temporaryDownload = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private object LogNearbyTransfer {
        fun error(message: String, error: Exception) {
            android.util.Log.w("P2pNearbyTransfer", message, error)
        }
    }

    private companion object {
        const val EXTRA_INITIAL_MODE = "pachattar.u2u.nearby.initial_mode"
        const val MODE_SEND = "send"
        const val MODE_RECEIVE = "receive"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val HANDSHAKE_TIMEOUT_MS = 3 * 60 * 1000
        const val SOCKET_READ_TIMEOUT_MS = 2 * 60 * 1000
        const val DISCOVERY_TIMEOUT_MS = 30_000L
        const val MAX_QUEUE_FILES = 20
        const val USER_DECISION_TIMEOUT_MINUTES = 3L
        const val CONFIRMATION_TIMEOUT_MINUTES = 3L
        const val STATUS_UPDATE_BYTES = 1024L * 1024L
        const val PAUSE_POLL_DELAY_MS = 50L
        const val MAX_RECONNECT_ATTEMPTS = 12
        const val RECONNECT_DELAY_MS = 1_500L
        const val NEARBY_SESSION_PREFIX = "nearby_"
    }
}
