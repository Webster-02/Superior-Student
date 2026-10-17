package com.superiorstudent.app.feature.studentconnect

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.net.Uri
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.R
import com.superiorstudent.app.core.session.SessionStore
import com.superiorstudent.app.feature.studentconnect.data.StudentChatConversation
import com.superiorstudent.app.feature.studentconnect.data.StudentChatMessage
import com.superiorstudent.app.feature.studentconnect.data.StudentChatMessageReaction
import com.superiorstudent.app.feature.studentconnect.data.StudentChatReactionEmoji
import com.superiorstudent.app.feature.studentconnect.data.StudentChatReplyMetadata
import com.superiorstudent.app.feature.studentconnect.data.StudentChatRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentProfile
import com.superiorstudent.app.feature.studentconnect.data.StudentProfileRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentPresence
import com.superiorstudent.app.feature.studentconnect.data.StudentPresenceFormatter
import com.superiorstudent.app.feature.studentconnect.data.StudentPresenceRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectPresenceTracker
import com.superiorstudent.app.feature.transfer.P2pChatFileTransferManager
import com.superiorstudent.app.feature.transfer.P2pNearbyTransferActivity
import com.superiorstudent.app.feature.transfer.data.FirebaseP2pTransferSignaling
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadata
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadataReader
import com.superiorstudent.app.feature.transfer.data.P2pTransferOffer
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** One-to-one text chat for two students with an accepted Student Connect relationship. */
class StudentChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var contactNameText: TextView
    private lateinit var contactInitialsText: TextView
    private lateinit var contactPresenceText: TextView
    private lateinit var typingIndicatorText: TextView
    private lateinit var statusText: TextView
    private lateinit var messagesScroll: ScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var attachFileButton: TextView
    private lateinit var selectedFilePanel: LinearLayout
    private lateinit var selectedFileNameText: TextView
    private lateinit var selectedFileDetailsText: TextView
    private var selectedFileMetadata: P2pTransferFileMetadata? = null
    private lateinit var replyPreview: LinearLayout
    private lateinit var replyPreviewTitle: TextView
    private lateinit var replyPreviewBody: TextView
    private lateinit var clearReplyButton: TextView
    private lateinit var transferControlButton: com.google.android.material.button.MaterialButton

    private var activeOutgoingTransferId: String? = null
    private var activeOutgoingFileName: String? = null
    private var activeOutgoingPaused = false
    private var transferControlRequestInFlight = false

    private val chatRepository by lazy { StudentChatRepository() }
    private val profileRepository by lazy { StudentProfileRepository() }
    private val presenceRepository by lazy { StudentPresenceRepository() }
    private val transferSignaling by lazy { FirebaseP2pTransferSignaling() }
    private val fileTransferManager by lazy {
        P2pChatFileTransferManager(
            this,
            object : P2pChatFileTransferManager.Callback {
                override fun onStatus(fileName: String, detail: String) {
                    if (::statusText.isInitialized && !chatUnavailable) {
                        statusText.text = fileName + " · " + detail
                    }
                    updateOutgoingTransferControl(fileName, detail)
                }

                override fun onCompleted(fileName: String, outgoing: Boolean) {
                    if (::statusText.isInitialized && !chatUnavailable) {
                        statusText.text = if (outgoing) {
                            "File sent and verified by receiver: " + fileName
                        } else {
                            "File received and SHA-256 verified: " + fileName
                        }
                    }
                    if (outgoing) clearOutgoingTransferControl(fileName)
                    Toast.makeText(
                        this@StudentChatActivity,
                        if (outgoing) "File transfer completed" else "File received",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(fileName: String, message: String, error: Throwable?) {
                    if (::statusText.isInitialized && !chatUnavailable) {
                        statusText.setTextColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_danger))
                        statusText.text = fileName + " · " + message
                    }
                    clearOutgoingTransferControl(fileName)
                    if (error != null) Log.w(TAG, "P2P file transfer error: " + message, error)
                }
            },
            transferSignaling
        )
    }

    private var incomingOffersListener: ListenerRegistration? = null
    private val promptedIncomingOfferIds = mutableSetOf<String>()
    private val incomingOfferQueue = ArrayDeque<P2pTransferOffer>()
    private var incomingOfferDialogVisible = false
    private var pendingIncomingOfferForSave: P2pTransferOffer? = null

    private var otherUid: String = ""
    private var otherDisplayName: String = "Classmate"
    private var currentProfile: StudentProfile? = null
    private var conversation: StudentChatConversation? = null
    private var messagesListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var readReceiptListener: ListenerRegistration? = null
    private var otherPresenceListener: ListenerRegistration? = null
    private var currentOtherPresence: StudentPresence? = null
    private var activityStarted = false
    private val presenceOwnerKey by lazy { "StudentChatActivity:" + System.identityHashCode(this) }
    private val presenceRefreshRunnable = object : Runnable {
        override fun run() {
            if (!activityStarted) return
            renderOtherPresence()
            typingHandler.postDelayed(this, PRESENCE_UI_REFRESH_MS)
        }
    }
    private val outgoingReceiptViews = mutableMapOf<String, TextView>()
    private val messageReactionSummaryViews = mutableMapOf<String, TextView>()
    private val reactionListeners = mutableListOf<ListenerRegistration>()
    private var reactionMessageIds: List<String> = emptyList()
    private var reactionRecords: List<StudentChatMessageReaction> = emptyList()
    private var reactionListening = false
    private var displayedMessages: List<StudentChatMessage> = emptyList()
    private var replyToMessage: StudentChatMessage? = null
    private var recipientLastReadAt: Timestamp? = null
    private val typingHandler = Handler(Looper.getMainLooper())
    private var isLocalTypingActive = false
    private var remoteTypingUpdatedAt: Timestamp? = null
    private var isSending = false
    private var chatUnavailable = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            statusText.text = "File selection cancelled."
            return@registerForActivityResult
        }
        handlePickedFile(uri)
    }

    private val incomingFileSavePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val offer = pendingIncomingOfferForSave
        pendingIncomingOfferForSave = null
        if (offer == null) return@registerForActivityResult
        if (uri == null) {
            statusText.text = "Save location cancelled. The file was not accepted."
            promptedIncomingOfferIds.remove(offer.transferId)
            incomingOfferQueue.addFirst(offer)
            return@registerForActivityResult
        }
        fileTransferManager.acceptIncoming(offer, uri) { error ->
            if (isFinishing || isDestroyed) return@acceptIncoming
            if (error != null) {
                statusText.text = "Could not accept " + offer.fileName + ". Please check the published P2P rules and try again."
                promptedIncomingOfferIds.remove(offer.transferId)
            } else {
                statusText.text = "Accepted " + offer.fileName + ". Starting secure connection…"
            }
            showNextIncomingOffer()
        }
    }

    private val typingIdleRunnable = Runnable { stopLocalTypingState() }
    private val typingHeartbeatRunnable = object : Runnable {
        override fun run() {
            if (!isLocalTypingActive) return
            publishLocalTypingHeartbeat()
            typingHandler.postDelayed(this, TYPING_HEARTBEAT_MS)
        }
    }
    private val remoteTypingExpiryRunnable = object : Runnable {
        override fun run() { renderRemoteTypingIndicator() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionStore(this)
        if (!session.sessionActive || session.username.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_student_chat)
        auth = FirebaseAuth.getInstance()
        bindViews()

        otherUid = intent.getStringExtra(EXTRA_OTHER_UID).orEmpty()
        otherDisplayName = intent.getStringExtra(EXTRA_OTHER_DISPLAY_NAME)
            ?.trim()
            ?.replace(WHITESPACE, " ")
            ?.takeIf { it.length in 2..60 }
            ?: "Classmate"
        contactNameText.text = otherDisplayName
        contactInitialsText.text = initialsForName(otherDisplayName)

        findViewById<TextView>(R.id.studentChatBackButton).setOnClickListener { finish() }
        clearReplyButton.setOnClickListener {
            clearReplyTarget()
            statusText.text = "Reply cancelled."
        }
        sendButton.setOnClickListener { sendCurrentMessage() }
        transferControlButton.setOnClickListener { toggleOutgoingTransferPause() }
        attachFileButton.setOnClickListener {
            if (conversation == null || chatUnavailable) {
                statusText.text = "Open a valid chat before attaching a file."
            } else {
                PopupMenu(this, attachFileButton).apply {
                    menu.add(0, 1, 0, "Send file in chat")
                    menu.add(0, 2, 1, "Nearby offline transfer")
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> filePicker.launch(arrayOf("*/*"))
                            2 -> startActivity(
                                Intent(this@StudentChatActivity, P2pNearbyTransferActivity::class.java)
                            )
                        }
                        true
                    }
                }.show()
            }
        }
        findViewById<TextView>(R.id.studentChatSelectedFileRemove).setOnClickListener {
            clearSelectedFile()
            statusText.text = "Selected file removed."
        }

        val user = auth.currentUser
        when {
            user == null || !user.isEmailVerified -> showFatalError(
                "Sign in to your verified Student Connect account before opening a chat."
            )
            otherUid.isBlank() || otherUid == user.uid -> showFatalError(
                "This chat link is invalid. Go back and open a chat from My Connections."
            )
            else -> openConversation(user.uid)
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (!::auth.isInitialized) return
        val user = auth.currentUser
        val activeConversation = conversation
        if (user != null && user.isEmailVerified && activeConversation != null && !chatUnavailable) {
            startTypingListener(user.uid, activeConversation)
            startReadReceiptListener(user.uid, activeConversation)
            syncChatPresence(user.uid)
            if (displayedMessages.isNotEmpty()) {
                startMessageReactionsListeners(user.uid, displayedMessages)
            }
            startIncomingOffersListener(activeConversation)
        }
    }

    override fun onStop() {
        activityStarted = false
        StudentConnectPresenceTracker.stop(presenceOwnerKey)
        otherPresenceListener?.remove()
        otherPresenceListener = null
        incomingOffersListener?.remove()
        incomingOffersListener = null
        typingHandler.removeCallbacks(presenceRefreshRunnable)
        stopLocalTypingState()
        typingListener?.remove()
        typingListener = null
        readReceiptListener?.remove()
        readReceiptListener = null
        reactionListeners.forEach { it.remove() }
        reactionListeners.clear()
        reactionListening = false
        reactionMessageIds = emptyList()
        recipientLastReadAt = null
        typingHandler.removeCallbacks(remoteTypingExpiryRunnable)
        if (::typingIndicatorText.isInitialized) typingIndicatorText.visibility = View.GONE
        super.onStop()
    }

    override fun onDestroy() {
        StudentConnectPresenceTracker.stop(presenceOwnerKey)
        otherPresenceListener?.remove()
        otherPresenceListener = null
        activityStarted = false
        stopLocalTypingState()
        typingListener?.remove()
        typingListener = null
        readReceiptListener?.remove()
        readReceiptListener = null
        reactionListeners.forEach { it.remove() }
        reactionListeners.clear()
        reactionListening = false
        reactionMessageIds = emptyList()
        messageReactionSummaryViews.clear()
        reactionRecords = emptyList()
        messagesListener?.remove()
        messagesListener = null
        incomingOffersListener?.remove()
        incomingOffersListener = null
        fileTransferManager.close()
        outgoingReceiptViews.clear()
        displayedMessages = emptyList()
        replyToMessage = null
        clearSelectedFile()
        typingHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun bindViews() {
        contactNameText = findViewById(R.id.studentChatContactName)
        contactInitialsText = findViewById(R.id.studentChatAvatarInitials)
        contactPresenceText = findViewById(R.id.studentChatPresenceIndicator)
        typingIndicatorText = findViewById(R.id.studentChatTypingIndicator)
        statusText = findViewById(R.id.studentChatStatus)
        transferControlButton = findViewById(R.id.studentChatTransferControl)
        transferControlButton.visibility = View.GONE
        messagesScroll = findViewById(R.id.studentChatMessagesScroll)
        messagesContainer = findViewById(R.id.studentChatMessagesContainer)
        emptyStateText = findViewById(R.id.studentChatEmptyState)
        messageInput = findViewById(R.id.studentChatMessageInput)
        sendButton = findViewById(R.id.studentChatSendButton)
        attachFileButton = findViewById(R.id.studentChatAttachFileButton)
        selectedFilePanel = findViewById(R.id.studentChatSelectedFilePanel)
        selectedFileNameText = findViewById(R.id.studentChatSelectedFileName)
        selectedFileDetailsText = findViewById(R.id.studentChatSelectedFileDetails)
        replyPreview = findViewById(R.id.studentChatReplyPreview)
        replyPreviewTitle = findViewById(R.id.studentChatReplyPreviewTitle)
        replyPreviewBody = findViewById(R.id.studentChatReplyPreviewBody)
        clearReplyButton = findViewById(R.id.studentChatReplyPreviewClose)
        replyPreview.visibility = View.GONE
        selectedFilePanel.visibility = View.GONE
        typingIndicatorText.visibility = View.GONE
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrBlank()) stopLocalTypingState() else onLocalComposerChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setComposerEnabled(false)
    }

    private fun openConversation(uid: String) {
        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
        statusText.text = "Checking connection and opening chat…"
        profileRepository.getOwnProfile(uid) { profile, profileError ->
            if (!isCurrentUser(uid)) return@getOwnProfile
            if (profileError != null || profile == null) {
                showFatalError("Could not load your Student Connect profile. Go back and try again.")
                return@getOwnProfile
            }

            currentProfile = profile
            chatRepository.openOrCreateConversation(uid, otherUid) { opened, error ->
                if (!isCurrentUser(uid)) return@openOrCreateConversation
                if (error != null || opened == null) {
                    val denied = error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)
                    showFatalError(
                        if (denied) {
                            "Chat access was denied. Confirm both students are still connected and the latest Firestore rules are published."
                        } else {
                            "Could not open this chat. Check your connection and make sure you are still connected."
                        }
                    )
                    return@openOrCreateConversation
                }

                conversation = opened
                statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
                statusText.text = "Private chat · Only this accepted connection can access these messages."
                setComposerEnabled(true)
                startMessagesListener(uid, opened)
                startIncomingOffersListener(opened)
                startTypingListener(uid, opened)
                startReadReceiptListener(uid, opened)
                if (activityStarted) syncChatPresence(uid)
            }
        }
    }

    private fun startMessagesListener(uid: String, opened: StudentChatConversation) {
        messagesListener?.remove()
        messagesListener = chatRepository.listenMessages(
            conversationId = opened.id,
            currentUid = uid,
            otherUid = otherUid
        ) { messages, error ->
            if (!isCurrentUser(uid)) return@listenMessages
            if (error != null || messages == null) {
                val denied = error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)
                statusText.setTextColor(ContextCompat.getColor(
                    this, if (denied) R.color.sc_danger else R.color.sc_text_muted
                ))
                statusText.text = if (denied) {
                    "Chat access is no longer available. The connection may have been removed."
                } else {
                    "Could not refresh messages. Check your connection."
                }
                if (denied) {
                    chatUnavailable = true
                    stopLocalTypingState()
                    typingListener?.remove()
                    typingListener = null
                    remoteTypingUpdatedAt = null
                    typingIndicatorText.visibility = View.GONE
                    setComposerEnabled(false)
                }
                return@listenMessages
            }
            renderMessages(uid, messages)
            startMessageReactionsListeners(uid, messages)
            chatRepository.markConversationRead(opened.id, uid) { readError ->
                if (!isCurrentUser(uid)) return@markConversationRead
                if (readError != null) {
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
                    statusText.text = "Messages are open, but unread status could not be updated. Check the published chat rules."
                }
            }
        }
    }

    private fun renderMessages(uid: String, messages: List<StudentChatMessage>) {
        displayedMessages = messages
        outgoingReceiptViews.clear()
        messageReactionSummaryViews.clear()
        messagesContainer.removeAllViews()
        emptyStateText.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE

        messages.forEach { message ->
            val isOutgoing = message.senderUid == uid
            val bubble = MaterialCardView(this).apply {
                radius = dp(19).toFloat()
                cardElevation = 0f
                useCompatPadding = false
                strokeWidth = if (isOutgoing) 0 else dp(1)
                setCardBackgroundColor(ContextCompat.getColor(
                    this@StudentChatActivity,
                    if (isOutgoing) R.color.sc_purple else R.color.sc_chat_bubble
                ))
                if (!isOutgoing) {
                    setStrokeColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_border))
                }
            }

            val bubbleContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(8))
            }

            val body = TextView(this).apply {
                text = message.text
                textSize = 14f
                setTextColor(ContextCompat.getColor(
                    this@StudentChatActivity,
                    R.color.sc_text_primary
                ))
                maxWidth = (resources.displayMetrics.widthPixels - dp(92)).coerceAtLeast(dp(170))
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            val replyId = message.replyToMessageId
            val replyName = message.replyToSenderName
            val replyText = message.replyToText
            if (replyId != null && StudentChatReplyMetadata.isValid(replyId, replyName, replyText)) {
                val quotePanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    background = GradientDrawable().apply {
                        setColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_chat_status))
                        cornerRadius = dp(10).toFloat()
                    }
                }
                val quoteTitle = TextView(this).apply {
                    text = "↪ $replyName"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_lavender))
                    maxLines = 1
                }
                val quoteBody = TextView(this).apply {
                    text = replyText
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_text_primary))
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                }
                quotePanel.addView(quoteTitle)
                quotePanel.addView(quoteBody)
                bubbleContent.addView(
                    quotePanel,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                )
            }
            bubbleContent.addView(body)

            val time = TextView(this).apply {
                text = message.createdAt?.let {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.toDate().time))
                } ?: "Sending…"
                textSize = 10f
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(
                    this@StudentChatActivity,
                    if (isOutgoing) R.color.sc_lavender else R.color.sc_text_muted
                ))
                setPadding(0, dp(3), 0, 0)
            }
            bubbleContent.addView(time)

            if (isOutgoing) {
                val receipt = TextView(this).apply {
                    textSize = 9f
                    gravity = Gravity.END
                    setPadding(0, dp(2), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                outgoingReceiptViews[message.id] = receipt
                bubbleContent.addView(receipt)
            }
            val reactionSummary = TextView(this).apply {
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_text_muted))
                setPadding(dp(7), dp(4), dp(7), dp(4))
                visibility = View.GONE
                contentDescription = "Message reactions"
            }
            messageReactionSummaryViews[message.id] = reactionSummary
            bubbleContent.addView(reactionSummary)
            renderMessageReactionSummary(message.id, reactionSummary)
            bubble.addView(bubbleContent)
            bubble.setOnLongClickListener {
                if (chatUnavailable || isSending) {
                    false
                } else {
                    val popup = PopupMenu(this@StudentChatActivity, bubble)
                    popup.menu.add("Reply").setOnMenuItemClickListener {
                        selectReplyTarget(message)
                        true
                    }
                    val reactionMenu = popup.menu.addSubMenu("React")
                    StudentChatReactionEmoji.supported.forEach { emoji ->
                        reactionMenu.add(emoji).setOnMenuItemClickListener {
                            applyMessageReaction(message, emoji)
                            true
                        }
                    }
                    val userUid = auth.currentUser?.uid
                    val ownReaction = reactionRecords.firstOrNull {
                        it.messageId == message.id && it.uid == userUid
                    }
                    if (ownReaction != null) {
                        popup.menu.add("Remove my reaction").setOnMenuItemClickListener {
                            applyMessageReaction(message, null)
                            true
                        }
                    }
                    popup.show()
                    true
                }
            }

            val bubbleParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isOutgoing) Gravity.END else Gravity.START
                topMargin = dp(4)
                bottomMargin = dp(4)
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            messagesContainer.addView(bubble, bubbleParams)
        }

        // Apply the latest read marker even when its listener fired before messages rendered.
        updateMessageReceipts(uid)
        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startMessageReactionsListeners(uid: String, messages: List<StudentChatMessage>) {
        val ids = messages.map { it.id }.distinct()
        if (reactionListening && reactionMessageIds == ids) return

        reactionListeners.forEach { it.remove() }
        reactionListeners.clear()
        reactionListening = false
        reactionMessageIds = ids
        reactionRecords = emptyList()
        updateMessageReactionViews()
        if (!activityStarted || ids.isEmpty() || conversation == null || chatUnavailable) return

        reactionListening = true
        val registrations = chatRepository.listenMessageReactions(
            conversationId = conversation!!.id,
            currentUid = uid,
            otherUid = otherUid,
            messageIds = ids
        ) { reactions, error ->
            if (!activityStarted || !isCurrentUser(uid)) return@listenMessageReactions
            if (error != null || reactions == null) {
                Log.w(TAG, "Could not refresh message reactions.", error)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
                statusText.text = "Message reactions could not be refreshed. Check the published Firestore chat rules."
                return@listenMessageReactions
            }
            reactionRecords = reactions.distinctBy { "${it.messageId}_${it.uid}" }
            updateMessageReactionViews()
        }
        reactionListeners.addAll(registrations)
    }

    private fun updateMessageReactionViews() {
        messageReactionSummaryViews.forEach { (messageId, view) ->
            renderMessageReactionSummary(messageId, view)
        }
    }

    private fun renderMessageReactionSummary(messageId: String, view: TextView) {
        val reactions = reactionRecords.filter { it.messageId == messageId }
        if (reactions.isEmpty()) {
            view.text = ""
            view.visibility = View.GONE
            return
        }
        val counts = reactions.groupingBy { it.emoji }.eachCount()
            .entries.sortedBy { StudentChatReactionEmoji.supported.indexOf(it.key) }
        val summary = counts.joinToString("   ") { "${it.key} ${it.value}" }
        val myReaction = reactions.firstOrNull { it.uid == auth.currentUser?.uid }
        view.text = if (myReaction == null) summary else "$summary  · You: ${myReaction.emoji}"
        view.setTextColor(ContextCompat.getColor(
            this,
            if (myReaction != null) R.color.sc_lavender else R.color.sc_text_muted
        ))
        view.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@StudentChatActivity, R.color.sc_chat_status))
            cornerRadius = dp(10).toFloat()
        }
        view.visibility = View.VISIBLE
    }

    private fun applyMessageReaction(message: StudentChatMessage, emoji: String?) {
        val user = auth.currentUser
        val activeConversation = conversation
        if (user == null || !user.isEmailVerified || activeConversation == null ||
            chatUnavailable || displayedMessages.none { it.id == message.id }
        ) {
            statusText.text = "The chat is not ready for reactions. Reopen it from My Connections and try again."
            return
        }
        if (emoji != null && !StudentChatReactionEmoji.isValid(emoji)) {
            statusText.text = "This reaction is not supported."
            return
        }
        chatRepository.setMessageReaction(
            conversationId = activeConversation.id,
            messageId = message.id,
            currentUid = user.uid,
            otherUid = otherUid,
            emoji = emoji
        ) { error ->
            if (!isCurrentUser(user.uid)) return@setMessageReaction
            statusText.setTextColor(ContextCompat.getColor(
                this, if (error == null) R.color.sc_text_muted else R.color.sc_danger
            ))
            statusText.text = when {
                error == null && emoji == null -> "Your reaction was removed."
                error == null -> "Reaction saved."
                error.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Reaction not saved. Publish the latest message reaction rules."
                else -> "Reaction not saved. Check your connection and try again."
            }
        }
    }

    private fun selectReplyTarget(message: StudentChatMessage) {
        if (message.id.isBlank() ||
            !StudentChatReplyMetadata.isValid(message.id, message.senderName, message.text)
        ) {
            statusText.text = "This message cannot be quoted."
            return
        }
        replyToMessage = message
        replyPreviewTitle.text = "Replying to ${message.senderName}"
        replyPreviewBody.text = message.text
        replyPreview.visibility = View.VISIBLE
        messageInput.requestFocus()
        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
        statusText.text = "Reply to this message, then tap Send."
    }

    private fun clearReplyTarget() {
        replyToMessage = null
        if (::replyPreview.isInitialized) replyPreview.visibility = View.GONE
        if (::replyPreviewBody.isInitialized) replyPreviewBody.text = ""
    }

    private fun onLocalComposerChanged() {
        val user = auth.currentUser
        val activeConversation = conversation
        if (user == null || !user.isEmailVerified || activeConversation == null || chatUnavailable || isSending) {
            stopLocalTypingState()
            return
        }
        if (!isLocalTypingActive) {
            isLocalTypingActive = true
            publishLocalTypingHeartbeat()
            typingHandler.removeCallbacks(typingHeartbeatRunnable)
            typingHandler.postDelayed(typingHeartbeatRunnable, TYPING_HEARTBEAT_MS)
        }
        typingHandler.removeCallbacks(typingIdleRunnable)
        typingHandler.postDelayed(typingIdleRunnable, TYPING_IDLE_MS)
    }

    private fun publishLocalTypingHeartbeat() {
        val user = auth.currentUser ?: return
        val activeConversation = conversation ?: return
        if (!user.isEmailVerified || chatUnavailable || !isLocalTypingActive) return
        chatRepository.publishTypingState(activeConversation.id, user.uid, otherUid) { error ->
            if (error != null && !isDestroyed) Log.w(TAG, "Could not update ephemeral typing state.", error)
        }
    }

    private fun stopLocalTypingState() {
        typingHandler.removeCallbacks(typingIdleRunnable)
        typingHandler.removeCallbacks(typingHeartbeatRunnable)
        if (!isLocalTypingActive) return
        isLocalTypingActive = false
        val user = auth.currentUser
        val activeConversation = conversation
        if (user == null || activeConversation == null) return
        chatRepository.clearTypingState(activeConversation.id, user.uid, otherUid) { error ->
            if (error != null && !isDestroyed) Log.w(TAG, "Could not clear ephemeral typing state.", error)
        }
    }

    private fun startTypingListener(uid: String, activeConversation: StudentChatConversation) {
        typingListener?.remove()
        typingListener = chatRepository.listenTypingState(
            conversationId = activeConversation.id,
            currentUid = uid,
            otherUid = otherUid
        ) { typingState, error ->
            if (!isCurrentUser(uid)) return@listenTypingState
            if (error != null) {
                remoteTypingUpdatedAt = null
                typingHandler.removeCallbacks(remoteTypingExpiryRunnable)
                typingIndicatorText.visibility = View.GONE
                Log.w(TAG, "Could not listen for typing state.", error)
                return@listenTypingState
            }
            remoteTypingUpdatedAt = typingState?.updatedAt
            renderRemoteTypingIndicator()
        }
    }

    private fun syncChatPresence(uid: String) {
        if (!activityStarted || !isCurrentUser(uid) || currentProfile?.uid != uid ||
            conversation == null || chatUnavailable
        ) {
            StudentConnectPresenceTracker.stop(presenceOwnerKey)
            otherPresenceListener?.remove()
            otherPresenceListener = null
            typingHandler.removeCallbacks(presenceRefreshRunnable)
            return
        }
        StudentConnectPresenceTracker.start(presenceOwnerKey, uid)
        startOtherPresenceListener(uid)
        typingHandler.removeCallbacks(presenceRefreshRunnable)
        typingHandler.postDelayed(presenceRefreshRunnable, PRESENCE_UI_REFRESH_MS)
    }

    private fun startOtherPresenceListener(uid: String) {
        otherPresenceListener?.remove()
        otherPresenceListener = presenceRepository.listenPresence(uid, otherUid) { presence, error ->
            if (!activityStarted || !isCurrentUser(uid)) return@listenPresence
            if (error != null) {
                Log.w(TAG, "Could not listen for connection presence.", error)
                currentOtherPresence = null
            } else {
                currentOtherPresence = presence
            }
            renderOtherPresence()
        }
    }

    private fun renderOtherPresence() {
        val label = StudentPresenceFormatter.label(currentOtherPresence, System.currentTimeMillis())
        contactPresenceText.text = label
        contactPresenceText.setTextColor(ContextCompat.getColor(
            this, if (label == "Online") R.color.sc_online else R.color.sc_text_muted
        ))
        contactPresenceText.visibility = View.VISIBLE
    }

    private fun startReadReceiptListener(uid: String, activeConversation: StudentChatConversation) {
        readReceiptListener?.remove()
        readReceiptListener = chatRepository.listenOtherParticipantReadState(
            conversationId = activeConversation.id,
            currentUid = uid,
            otherUid = otherUid
        ) { readAt, error ->
            if (!isCurrentUser(uid)) return@listenOtherParticipantReadState
            if (error != null) {
                Log.w(TAG, "Could not listen for recipient read receipts.", error)
                return@listenOtherParticipantReadState
            }
            recipientLastReadAt = readAt
            updateMessageReceipts(uid)
        }
    }

    private fun updateMessageReceipts(currentUid: String) {
        displayedMessages.forEach { message ->
            if (message.senderUid != currentUid) return@forEach
            val receipt = outgoingReceiptViews[message.id] ?: return@forEach
            val read = com.superiorstudent.app.feature.studentconnect.data.StudentChatReadReceipt
                .isRead(message.createdAt, recipientLastReadAt)
            receipt.text = when {
                message.createdAt == null -> "Sending…"
                read -> "Read ✓✓"
                else -> "Sent ✓"
            }
            receipt.contentDescription = when {
                message.createdAt == null -> "Message is sending"
                read -> "Message read by recipient"
                else -> "Message sent"
            }
            receipt.setTextColor(ContextCompat.getColor(
                this,
                if (read) R.color.sc_lavender else R.color.sc_text_muted
            ))
        }
    }

    private fun renderRemoteTypingIndicator() {
        val updatedAtMillis = remoteTypingUpdatedAt?.toDate()?.time
        val ageMillis = updatedAtMillis?.let { System.currentTimeMillis() - it }
        val isFresh = ageMillis != null && ageMillis in -2_000L..TYPING_STALE_AFTER_MS
        typingIndicatorText.visibility = if (isFresh) View.VISIBLE else View.GONE
        if (isFresh) {
            typingIndicatorText.text = "Typing…"
            typingHandler.removeCallbacks(remoteTypingExpiryRunnable)
            typingHandler.postDelayed(remoteTypingExpiryRunnable, TYPING_EXPIRY_CHECK_MS)
        } else {
            typingHandler.removeCallbacks(remoteTypingExpiryRunnable)
        }
    }

    private fun startIncomingOffersListener(opened: StudentChatConversation) {
        if (chatUnavailable || !activityStarted || incomingOffersListener != null) return
        val uid = auth.currentUser?.takeIf { it.isEmailVerified }?.uid ?: return
        incomingOffersListener = transferSignaling.listenForIncomingOffers(opened.id) { offers, error ->
            if (!isCurrentUser(uid) || chatUnavailable) return@listenForIncomingOffers
            if (error != null) {
                val detail = error.message.orEmpty()
                statusText.text = if (detail.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Incoming file transfers need the latest P2P Firestore rules published."
                } else {
                    "Could not check incoming files. Check your connection."
                }
                return@listenForIncomingOffers
            }
            offers.forEach { offer ->
                if (promptedIncomingOfferIds.add(offer.transferId)) incomingOfferQueue.addLast(offer)
            }
            showNextIncomingOffer()
        }
    }

    private fun showNextIncomingOffer() {
        if (isFinishing || isDestroyed || incomingOfferDialogVisible || incomingOfferQueue.isEmpty()) return
        if (!activityStarted || chatUnavailable) return
        val offer = incomingOfferQueue.removeFirst()
        incomingOfferDialogVisible = true
        AlertDialog.Builder(this)
            .setTitle("Incoming file")
            .setMessage(
                offer.fileName + "\n" +
                    P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes) + " · " + offer.mimeType +
                    "\n\nSave this file to your device? It will be received through an encrypted connection."
            )
            .setPositiveButton("Choose save location") { _, _ ->
                pendingIncomingOfferForSave = offer
                incomingFileSavePicker.launch(offer.fileName.take(180))
            }
            .setNegativeButton("Decline") { _, _ ->
                fileTransferManager.rejectIncoming(offer) { error ->
                    if (error != null) {
                        statusText.text = "Could not decline " + offer.fileName + ". Check the published P2P rules."
                    } else {
                        statusText.text = "File declined: " + offer.fileName
                        promptedIncomingOfferIds.remove(offer.transferId)
                    }
                    incomingOfferDialogVisible = false
                    showNextIncomingOffer()
                }
            }
            .setOnCancelListener {
                incomingOfferDialogVisible = false
                incomingOfferQueue.addFirst(offer)
            }
            .show()
            .also { dialog ->
                dialog.setOnDismissListener { incomingOfferDialogVisible = false }
            }
    }

    private fun handlePickedFile(uri: Uri) {
        val metadata = runCatching { P2pTransferFileMetadataReader.read(this, uri) }.getOrNull()
        if (metadata == null) {
            clearSelectedFile()
            statusText.text = "Could not read this file. Please choose another file."
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedFileMetadata = metadata
        selectedFileNameText.text = metadata.fileName
        selectedFileDetailsText.text = "${metadata.mimeType} · ${P2pTransferFileMetadataReader.formatBytes(metadata.sizeBytes)}\nReady to attach. Transfer has not started."
        selectedFilePanel.visibility = View.VISIBLE
        statusText.text = "File selected. It has not been sent yet."
    }

    private fun clearSelectedFile() {
        selectedFileMetadata = null
        if (::selectedFilePanel.isInitialized) selectedFilePanel.visibility = View.GONE
        if (::selectedFileNameText.isInitialized) selectedFileNameText.text = ""
        if (::selectedFileDetailsText.isInitialized) selectedFileDetailsText.text = ""
    }

    private fun updateOutgoingTransferControl(fileName: String, detail: String) {
        if (!::transferControlButton.isInitialized ||
            activeOutgoingTransferId.isNullOrBlank() ||
            activeOutgoingFileName != fileName
        ) return

        when {
            detail.contains("paused", ignoreCase = true) -> {
                activeOutgoingPaused = true
                transferControlButton.text = "Resume transfer"
                transferControlButton.visibility = View.VISIBLE
            }
            detail.contains("resumed", ignoreCase = true) ||
                detail.contains("Sending encrypted file", ignoreCase = true) -> {
                activeOutgoingPaused = false
                transferControlButton.text = "Pause transfer"
                transferControlButton.visibility = View.VISIBLE
            }
        }
    }

    private fun toggleOutgoingTransferPause() {
        val transferId = activeOutgoingTransferId
        if (transferId.isNullOrBlank() || transferControlRequestInFlight || chatUnavailable) return

        transferControlRequestInFlight = true
        transferControlButton.isEnabled = false
        val requestedPause = !activeOutgoingPaused
        val resultCallback: (Exception?) -> Unit = { error ->
            runOnUiThread {
                transferControlRequestInFlight = false
                if (!::transferControlButton.isInitialized) return@runOnUiThread
                transferControlButton.isEnabled = true
                if (error != null) {
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_danger))
                    statusText.text = (if (requestedPause) "Could not pause the transfer. " else "Could not resume the transfer. ") +
                        (error.message ?: "Please try again.")
                } else {
                    activeOutgoingPaused = requestedPause
                    transferControlButton.text = if (activeOutgoingPaused) "Resume transfer" else "Pause transfer"
                    transferControlButton.visibility = View.VISIBLE
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
                    statusText.text = if (activeOutgoingPaused) {
                        "File transfer paused. The secure connection remains open."
                    } else {
                        "File transfer resumed."
                    }
                }
            }
        }

        if (requestedPause) {
            fileTransferManager.pauseOutgoingTransfer(transferId, resultCallback)
        } else {
            fileTransferManager.resumeOutgoingTransfer(transferId, resultCallback)
        }
    }

    private fun clearOutgoingTransferControl(fileName: String) {
        if (activeOutgoingFileName != fileName) return
        activeOutgoingTransferId = null
        activeOutgoingFileName = null
        activeOutgoingPaused = false
        transferControlRequestInFlight = false
        if (::transferControlButton.isInitialized) {
            transferControlButton.visibility = View.GONE
            transferControlButton.isEnabled = true
            transferControlButton.text = "Pause transfer"
        }
    }

    private fun sendCurrentMessage() {
        val user = auth.currentUser
        val profile = currentProfile
        val activeConversation = conversation
        if (user == null || !user.isEmailVerified || profile == null ||
            activeConversation == null || chatUnavailable || isSending
        ) {
            statusText.text = "The chat is not ready yet. Please wait or reopen it from My Connections."
            return
        }

        val rawText = messageInput.text?.toString().orEmpty()
        val selectedFile = selectedFileMetadata
        if (selectedFile != null) {
            if (rawText.isNotBlank()) {
                statusText.text = "Send the text message first, then send the selected file separately."
                return
            }
            isSending = true
            sendButton.isEnabled = false
            statusText.text = "Preparing encrypted file offer…"
            fileTransferManager.sendFile(selectedFile, activeConversation.id, otherUid) { offer, error ->
                runOnUiThread {
                    isSending = false
                    sendButton.isEnabled = !chatUnavailable
                    if (!isCurrentUser(user.uid)) return@runOnUiThread
                    if (error != null || offer == null) {
                        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_danger))
                        statusText.text = "File not sent. " + (error?.message ?: "Please try again.")
                        Log.w(TAG, "Could not create file offer.", error)
                    } else {
                        activeOutgoingTransferId = offer.transferId
                        activeOutgoingFileName = selectedFile.fileName
                        activeOutgoingPaused = false
                        transferControlRequestInFlight = false
                        transferControlButton.visibility = View.GONE
                        transferControlButton.text = "Pause transfer"
                        clearSelectedFile()
                        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
                        statusText.text = "File offer sent. Waiting for " + otherDisplayName + " to accept…"
                    }
                }
            }
            return
        }
        if (rawText.trim().isEmpty()) {
            statusText.text = "Type a message or attach a file before tapping Send."
            messageInput.requestFocus()
            return
        }
        if (rawText.trim().length > 2000) {
            statusText.text = "Messages must be 2000 characters or fewer."
            return
        }

        stopLocalTypingState()
        isSending = true
        setComposerEnabled(true)
        sendButton.isEnabled = false
        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
        statusText.text = "Sending message…"

        chatRepository.sendMessage(
            conversationId = activeConversation.id,
            currentUid = user.uid,
            otherUid = otherUid,
            senderName = profile.displayName,
            rawText = rawText,
            replyTo = replyToMessage
        ) { error ->
            if (!isCurrentUser(user.uid)) return@sendMessage
            isSending = false
            sendButton.isEnabled = true
            if (error != null) {
                val denied = error.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)
                statusText.setTextColor(ContextCompat.getColor(
                    this, if (denied) R.color.sc_danger else R.color.sc_text_muted
                ))
                statusText.text = if (denied) {
                    "Message not sent. Chat access may have changed because this connection is no longer active."
                } else {
                    "Message not sent. Check your connection and try again."
                }
                if (denied) {
                    chatUnavailable = true
                    setComposerEnabled(false)
                }
                return@sendMessage
            }

            messageInput.text?.clear()
            clearReplyTarget()
            statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
            statusText.text = "Message sent."
        }
    }

    private fun showFatalError(message: String) {
        chatUnavailable = true
        StudentConnectPresenceTracker.stop(presenceOwnerKey, immediate = true)
        otherPresenceListener?.remove()
        otherPresenceListener = null
        typingHandler.removeCallbacks(presenceRefreshRunnable)
        stopLocalTypingState()
        typingListener?.remove()
        typingListener = null
        remoteTypingUpdatedAt = null
        if (::typingIndicatorText.isInitialized) typingIndicatorText.visibility = View.GONE
        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_danger))
        statusText.text = message
        emptyStateText.text = "Chat unavailable.\nGo back to My Connections and try again."
        emptyStateText.visibility = View.VISIBLE
        setComposerEnabled(false)
    }

    private fun setComposerEnabled(enabled: Boolean) {
        if (!::messageInput.isInitialized || !::sendButton.isInitialized) return
        messageInput.isEnabled = enabled
        sendButton.isEnabled = enabled && !isSending && !chatUnavailable
    }

    private fun isCurrentUser(uid: String): Boolean =
        !isFinishing && !isDestroyed && auth.currentUser?.uid == uid

    private fun initialsForName(name: String): String {
        val parts = name.trim().split(WHITESPACE).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.ROOT)
            parts.isNotEmpty() -> parts[0].take(2).uppercase(Locale.ROOT)
            else -> "SC"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_OTHER_UID = "student_chat_other_uid"
        const val EXTRA_OTHER_DISPLAY_NAME = "student_chat_other_display_name"
        private const val TAG = "StudentChatTyping"
        private const val TYPING_IDLE_MS = 1_500L
        private const val TYPING_HEARTBEAT_MS = 4_000L
        private const val TYPING_STALE_AFTER_MS = 9_000L
        private const val TYPING_EXPIRY_CHECK_MS = 1_000L
        private const val PRESENCE_UI_REFRESH_MS = 15_000L
        private val WHITESPACE = Regex("\\s+")
    }
}
