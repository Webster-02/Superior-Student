package com.superiorstudent.app.feature.market.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketConversation
import com.superiorstudent.app.feature.market.data.MarketConversationMessage
import com.superiorstudent.app.feature.market.data.MarketListingKind
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.feature.market.data.MarketTaskStatus
import com.superiorstudent.app.ui.AppThemePreferences

/**
 * Screen 14: live Market text chat, stored separately from Student Connect conversations.
 */
class MarketTaskChatActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var messageList: LinearLayout
    private lateinit var messageInput: EditText
    private val repository by lazy { MarketRepository() }
    private var messages: List<MarketTaskChatMessage> = emptyList()
    private var conversation: MarketConversation? = null
    private var currentUid = ""
    private var recipientUid = ""
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var liveLoadError: String? = null
    private var partnerDisplayName = intent.getStringExtra(EXTRA_HELPER_NAME).orEmpty()
        .ifBlank { "Market participant" }
    private var partnerNameView: TextView? = null
    private var taskTitleView: TextView? = null
    private var summaryStatusView: TextView? = null
    private var completionButton: TextView? = null

    private val helperName: String
        get() = partnerDisplayName
    private val helperInitials: String
        get() = MarketTaskChatUiPolicy.initials(helperName)
    private val taskTitle: String
        get() = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank {
            val current = conversation
            val id = current?.taskId ?: current?.offerId ?: ""
            val type = if (current?.kind == MarketListingKind.OFFER) "Offer" else "Task"
            if (id.isBlank()) "Market conversation" else "$type · ${id.takeLast(6)}"
        }
    private val pricePkr: Int
        get() = intent.getIntExtra(EXTRA_PRICE_PKR, 0)
    private val conversationId: String
        get() = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2))
        content.addView(View(this).apply {
            setBackgroundColor(color(R.color.market_line))
        }, LinearLayout.LayoutParams(-1, dp(1)))
        content.addView(buildTaskSummary(), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(10), dp(16), dp(4))
        })

        chatScroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(5)
        })
        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        chatScroll.addView(messageList, FrameLayout.LayoutParams(-1, -2))
        content.addView(buildComposer(), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(6), dp(14), dp(8))
        })

        renderMessages()
        loadConversation()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onStart() {
        super.onStart()
        conversation?.let(::startMessageListener)
    }

    override fun onStop() {
        listenerRegistration?.remove()
        listenerRegistration = null
        super.onStop()
    }

    private fun loadConversation() {
        if (conversationId.isBlank()) {
            liveLoadError = "This Market chat is missing its conversation ID. Open it from a task or offer."
            renderMessages()
            return
        }
        val load = try {
            repository.loadMarketConversation(conversationId)
        } catch (error: Exception) {
            liveLoadError = error.localizedMessage ?: "The Market conversation could not be loaded."
            renderMessages()
            return
        }
        load.addOnCompleteListener { result ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            if (!result.isSuccessful || result.result == null) {
                liveLoadError = result.exception?.localizedMessage
                    ?: "This Market conversation is unavailable."
                renderMessages()
                return@addOnCompleteListener
            }
            val loadedConversation = result.result ?: return@addOnCompleteListener
            conversation = loadedConversation
            recipientUid = loadedConversation.participants.firstOrNull { it != currentUid }.orEmpty()
            taskTitleView?.text = taskTitle
            summaryStatusView?.text = if (loadedConversation.kind == MarketListingKind.OFFER)
                "Offer chat" else "Task chat"
            completionButton?.visibility = View.GONE
            messageInput.isEnabled = recipientUid.isNotBlank()
            liveLoadError = null
            try {
                repository.markConversationRead(loadedConversation.id)
            } catch (_: Exception) {
                // Message access remains valid even if the optional read marker update fails.
            }
            renderMessages()
            startMessageListener(loadedConversation)
            if (loadedConversation.kind == MarketListingKind.TASK &&
                loadedConversation.listingOwnerUid == currentUid && !loadedConversation.taskId.isNullOrBlank()) {
                try {
                    repository.loadOwnedTask(loadedConversation.taskId).addOnCompleteListener { taskResult ->
                        if (isFinishing || isDestroyed) return@addOnCompleteListener
                        val liveTask = if (taskResult.isSuccessful) taskResult.result else null
                        completionButton?.visibility = if (liveTask?.status == MarketTaskStatus.ASSIGNED)
                            View.VISIBLE else View.GONE
                        summaryStatusView?.text = when (liveTask?.status) {
                            MarketTaskStatus.COMPLETED -> "Completed"
                            MarketTaskStatus.CANCELLED -> "Cancelled"
                            MarketTaskStatus.EXPIRED -> "Expired"
                            MarketTaskStatus.ASSIGNED -> "Assigned"
                            else -> "Task chat"
                        }
                    }
                } catch (_: Exception) {
                    completionButton?.visibility = View.GONE
                }
            }
        }
    }

    private fun startMessageListener(chat: MarketConversation) {
        if (listenerRegistration != null || isFinishing || isDestroyed) return
        try {
            listenerRegistration = repository.listenMarketMessages(chat) { rows, error ->
                if (isFinishing || isDestroyed) return@listenMarketMessages
                if (error != null) {
                    liveLoadError = error.localizedMessage ?: "Market messages could not be loaded."
                    renderMessages()
                    return@listenMarketMessages
                }
                liveLoadError = null
                val firstOtherSender = rows.firstOrNull { it.senderUid != currentUid }?.senderName
                if (intent.getStringExtra(EXTRA_HELPER_NAME).isNullOrBlank() &&
                    !firstOtherSender.isNullOrBlank()) {
                    partnerDisplayName = firstOtherSender
                    partnerNameView?.text = firstOtherSender
                }
                messages = rows.map(::toChatMessage)
                renderMessages()
                chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
            }
        } catch (error: Exception) {
            liveLoadError = error.localizedMessage ?: "Market messages could not be loaded."
            renderMessages()
        }
    }

    private fun toChatMessage(message: MarketConversationMessage) = MarketTaskChatMessage(
        id = message.id,
        body = message.text,
        timeLabel = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(message.createdAt.toDate()),
        fromMe = message.senderUid == currentUid
    )

    private fun confirmTaskCompletion() {
        val chat = conversation ?: return
        val taskId = chat.taskId ?: return
        AlertDialog.Builder(this)
            .setTitle("Mark task complete?")
            .setMessage("Confirm that the task has been completed.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Complete task") { _, _ ->
                try {
                    repository.completeTask(taskId).addOnCompleteListener { result ->
                        if (isFinishing || isDestroyed) return@addOnCompleteListener
                        if (result.isSuccessful) {
                            completionButton?.visibility = View.GONE
                summaryStatusView?.text = "Completed"
                            Toast.makeText(this, "Task marked complete.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, result.exception?.localizedMessage
                                ?: "Task could not be completed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (error: Exception) {
                    Toast.makeText(this, error.localizedMessage ?: "Task could not be completed.",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(10))
        }
        row.addView(iconButton(MarketTaskChatIcon.BACK, "Back", color(R.color.market_ink)) {
            finish()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        val avatar = FrameLayout(this).apply {
            background = oval(color(R.color.market_sky))
            addView(makeText(helperInitials, 14f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginStart = dp(12)
        })

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val partnerName = makeText(helperName, 16f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        partnerNameView = partnerName
        headings.addView(partnerName)
        headings.addView(makeText("Market chat", 12f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(8)
        })
        return row
    }

    private fun buildTaskSummary(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val briefcase = FrameLayout(this).apply {
            background = rounded(color(R.color.market_peach), 14f)
            addView(MarketTaskChatIconView(this@MarketTaskChatActivity,
                MarketTaskChatIcon.BRIEFCASE, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        heading.addView(briefcase, LinearLayout.LayoutParams(dp(42), dp(42)))

        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val taskTitleLabel = makeText(taskTitle, 13.5f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        taskTitleView = taskTitleLabel
        labels.addView(taskTitleLabel)
        val priceLine = if (pricePkr > 0) "Proposal price ${MarketTaskChatUiPolicy.priceLabel(pricePkr)}"
            else "Market conversation"
        labels.addView(makeText(priceLine, 12.5f,
            false, color(R.color.market_ink2)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        heading.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(8)
        })

        val summaryStatus = makeText("Market chat", 11.5f, true,
            color(R.color.ok)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = rounded(color(R.color.market_mint), 99f)
        }
        summaryStatusView = summaryStatus
        heading.addView(summaryStatus, LinearLayout.LayoutParams(-2, -2))
        card.addView(heading)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(taskActionButton("View task", false) {
            AlertDialog.Builder(this@MarketTaskChatActivity)
                .setTitle(taskTitle)
                .setMessage("Conversation ID: ${conversation?.id ?: conversationId}\n" +
                    "Listing ID: ${conversation?.listingId.orEmpty()}")
                .setPositiveButton("Got it", null)
                .show()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        val completeButton = taskActionButton("Mark complete", true) {
            confirmTaskCompletion()
        }.apply { visibility = View.GONE }
        completionButton = completeButton
        actions.addView(completeButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(8)
        })
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        return card
    }

    private fun taskActionButton(label: String, highlighted: Boolean, action: () -> Unit): TextView =
        makeText(label, 12.5f, true,
            color(if (highlighted) R.color.market_match_ink else R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            background = if (highlighted) {
                rounded(color(R.color.market_match), 14f)
            } else {
                rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun renderMessages() {
        if (!::messageList.isInitialized) return
        messageList.removeAllViews()
        val kindLabel = if (conversation?.kind == MarketListingKind.OFFER) "Offer conversation"
            else "Task conversation"
        val event = makeText(kindLabel, 12.5f, true,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
        }
        messageList.addView(event, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10)
        })
        if (liveLoadError != null) {
            messageList.addView(makeText(liveLoadError.orEmpty(), 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(20), dp(12), dp(20))
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        if (messages.isEmpty()) {
            val emptyCopy = if (conversation == null) "Connecting to Market chat…"
                else "No messages yet. Send a message to start."
            messageList.addView(makeText(emptyCopy, 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(20), dp(12), dp(20))
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        messages.forEachIndexed { index, message ->
            messageList.addView(buildMessageRow(message), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(8)
            })
        }
    }

    private fun buildMessageRow(message: MarketTaskChatMessage): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (message.fromMe) Gravity.END else Gravity.START
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = if (message.fromMe) {
                rounded(color(R.color.market_match), 18f)
            } else {
                rounded(color(R.color.market_card), 18f, color(R.color.market_line))
            }
        }
        val textColor = color(if (message.fromMe) R.color.market_match_ink else R.color.market_ink)
        bubble.addView(makeText(message.body, 14f, false, textColor).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2))
        val metadata = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        metadata.addView(makeText(message.timeLabel, 10.5f, false,
            color(if (message.fromMe) R.color.market_ink2 else R.color.market_ink3)))
        if (message.fromMe) {
            metadata.addView(MarketTaskChatIconView(this, MarketTaskChatIcon.DOUBLE_CHECK,
                color(R.color.market_ink2)), LinearLayout.LayoutParams(dp(15), dp(15)).apply {
                marginStart = dp(3)
            })
        }
        bubble.addView(metadata, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
        })
        val widthDp = (resources.displayMetrics.widthPixels /
            resources.displayMetrics.density * 0.78f).toInt().coerceAtLeast(180)
        row.addView(bubble, LinearLayout.LayoutParams(dp(widthDp), -2))
        return row
    }

    private fun buildComposer(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        messageInput = EditText(this).apply {
            hint = "Message"
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@MarketTaskChatActivity, R.font.poppins)
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(dp(18), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            filters = arrayOf(InputFilter.LengthFilter(2000))
            isSingleLine = true
            isEnabled = false
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, event ->
                val sendKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event?.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_SEND || sendKey) {
                    sendMessage()
                    true
                } else false
            }
        }
        row.addView(messageInput, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
        })
        row.addView(iconButton(MarketTaskChatIcon.SEND, "Send message",
            color(R.color.market_match_ink), color(R.color.market_match)) {
            sendMessage()
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        return row
    }

    private fun sendMessage() {
        val raw = messageInput.text?.toString().orEmpty()
        if (raw.trim().isEmpty()) {
            Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show()
            return
        }
        val chat = conversation
        if (chat == null || recipientUid.isBlank()) {
            Toast.makeText(this, "This Market conversation is not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        messageInput.isEnabled = false
        val send = try {
            repository.sendMarketMessage(chat.id, recipientUid, raw)
        } catch (error: Exception) {
            messageInput.isEnabled = true
            Toast.makeText(this, error.localizedMessage ?: "Message could not be sent.",
                Toast.LENGTH_SHORT).show()
            return
        }
        send.addOnCompleteListener { result ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            messageInput.isEnabled = conversation != null
            if (result.isSuccessful) {
                messageInput.setText("")
                liveLoadError = null
            } else {
                Toast.makeText(this, result.exception?.localizedMessage
                    ?: "Message could not be sent.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun iconButton(
        icon: MarketTaskChatIcon,
        description: String,
        tint: Int,
        fill: Int = color(R.color.market_card),
        action: () -> Unit
    ): FrameLayout = FrameLayout(this).apply {
        background = rounded(fill, 16f,
            if (fill == color(R.color.market_card)) color(R.color.market_line) else null)
        isClickable = true
        isFocusable = true
        contentDescription = description
        addView(MarketTaskChatIconView(this@MarketTaskChatActivity, icon, tint),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnClickListener { action() }
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketTaskChatActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketTaskChatActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketTaskChatActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HELPER_NAME = "market.chat.helper_name"
        const val EXTRA_CONVERSATION_ID = "market.chat.conversation_id"
        const val EXTRA_TASK_TITLE = "market.chat.task_title"
        const val EXTRA_PRICE_PKR = "market.chat.price_pkr"
        const val EXTRA_CHAT_CONTEXT = "market.chat.context"
    }
}
