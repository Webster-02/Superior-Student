package com.superiorstudent.app.feature.studentconnect

import android.os.Bundle
import android.os.Handler
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.R
import com.superiorstudent.app.core.session.SessionStore
import com.superiorstudent.app.feature.studentconnect.data.StudentChatConversation
import com.superiorstudent.app.feature.studentconnect.data.StudentChatMessage
import com.superiorstudent.app.feature.studentconnect.data.StudentChatRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentProfile
import com.superiorstudent.app.feature.studentconnect.data.StudentProfileRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One-to-one text chat for two students with an accepted Student Connect relationship. */
class StudentChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var contactNameText: TextView
    private lateinit var contactInitialsText: TextView
    private lateinit var typingIndicatorText: TextView
    private lateinit var statusText: TextView
    private lateinit var messagesScroll: ScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private val chatRepository by lazy { StudentChatRepository() }
    private val profileRepository by lazy { StudentProfileRepository() }

    private var otherUid: String = ""
    private var otherDisplayName: String = "Classmate"
    private var currentProfile: StudentProfile? = null
    private var conversation: StudentChatConversation? = null
    private var messagesListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var readReceiptListener: ListenerRegistration? = null
    private val outgoingReceiptViews = mutableMapOf<String, TextView>()
    private var displayedMessages: List<StudentChatMessage> = emptyList()
    private var recipientLastReadAt: Timestamp? = null
    private val typingHandler = Handler(Looper.getMainLooper())
    private var isLocalTypingActive = false
    private var remoteTypingUpdatedAt: Timestamp? = null
    private var isSending = false
    private var chatUnavailable = false

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
        sendButton.setOnClickListener { sendCurrentMessage() }

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
        if (!::auth.isInitialized) return
        val user = auth.currentUser
        val activeConversation = conversation
        if (user != null && user.isEmailVerified && activeConversation != null && !chatUnavailable) {
            startTypingListener(user.uid, activeConversation)
            startReadReceiptListener(user.uid, activeConversation)
        }
    }

    override fun onStop() {
        stopLocalTypingState()
        typingListener?.remove()
        typingListener = null
        readReceiptListener?.remove()
        readReceiptListener = null
        recipientLastReadAt = null
        typingHandler.removeCallbacks(remoteTypingExpiryRunnable)
        if (::typingIndicatorText.isInitialized) typingIndicatorText.visibility = View.GONE
        super.onStop()
    }

    override fun onDestroy() {
        stopLocalTypingState()
        typingListener?.remove()
        typingListener = null
        readReceiptListener?.remove()
        readReceiptListener = null
        messagesListener?.remove()
        messagesListener = null
        outgoingReceiptViews.clear()
        displayedMessages = emptyList()
        typingHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun bindViews() {
        contactNameText = findViewById(R.id.studentChatContactName)
        contactInitialsText = findViewById(R.id.studentChatAvatarInitials)
        typingIndicatorText = findViewById(R.id.studentChatTypingIndicator)
        statusText = findViewById(R.id.studentChatStatus)
        messagesScroll = findViewById(R.id.studentChatMessagesScroll)
        messagesContainer = findViewById(R.id.studentChatMessagesContainer)
        emptyStateText = findViewById(R.id.studentChatEmptyState)
        messageInput = findViewById(R.id.studentChatMessageInput)
        sendButton = findViewById(R.id.studentChatSendButton)
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
                startTypingListener(uid, opened)
                startReadReceiptListener(uid, opened)
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
            bubble.addView(bubbleContent)

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

        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
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
        if (rawText.trim().isEmpty()) {
            statusText.text = "Type a message before tapping Send."
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
            rawText = rawText
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
            statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
            statusText.text = "Message sent."
        }
    }

    private fun showFatalError(message: String) {
        chatUnavailable = true
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
        private val WHITESPACE = Regex("\\s+")
    }
}
