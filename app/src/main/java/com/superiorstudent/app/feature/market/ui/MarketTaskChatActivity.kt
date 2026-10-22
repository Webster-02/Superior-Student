package com.superiorstudent.app.feature.market.ui

import android.content.Intent
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
import com.superiorstudent.app.ui.AppThemePreferences

/**
 * Screen 14 reference chat preview. It uses local fixture messages and never calls Student Connect chat.
 */
class MarketTaskChatActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var messageList: LinearLayout
    private lateinit var messageInput: EditText
    private var messages = MarketTaskChatUiPolicy.referenceMessages

    private val helperName: String
        get() = intent.getStringExtra(EXTRA_HELPER_NAME).orEmpty()
            .ifBlank { MarketTaskChatUiPolicy.HELPER_NAME }
    private val helperInitials: String
        get() = MarketTaskChatUiPolicy.initials(helperName)
    private val taskTitle: String
        get() = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
            .ifBlank { MarketTaskChatUiPolicy.TASK_TITLE }
    private val pricePkr: Int
        get() = intent.getIntExtra(EXTRA_PRICE_PKR, MarketTaskChatUiPolicy.PRICE_PKR)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
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
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
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
        headings.addView(makeText(helperName, 16f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        headings.addView(makeText("Task chat", 12f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(8)
        })
        row.addView(iconButton(MarketTaskChatIcon.FLAG, "Report chat", color(R.color.market_ink)) {
            AlertDialog.Builder(this)
                .setTitle("Report chat")
                .setMessage("Reporting is not connected in this reference preview. No report was submitted.")
                .setPositiveButton("Got it", null)
                .show()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
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
        labels.addView(makeText(taskTitle, 13.5f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        labels.addView(makeText(MarketTaskChatUiPolicy.taskBudgetLine(pricePkr), 12.5f,
            false, color(R.color.market_ink2)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        heading.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(8)
        })

        val status = makeText(MarketTaskChatUiPolicy.STATUS_LABEL, 11.5f, true,
            color(R.color.ok)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = rounded(color(R.color.market_mint), 99f)
        }
        heading.addView(status, LinearLayout.LayoutParams(-2, -2))
        card.addView(heading)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(taskActionButton("View task", false) {
            AlertDialog.Builder(this@MarketTaskChatActivity)
                .setTitle(taskTitle)
                .setMessage("Budget: " + MarketTaskChatUiPolicy.priceLabel(pricePkr) +
                    "\nDeadline: " + MarketTaskChatUiPolicy.DUE_LABEL +
                    "\n\nThis task summary is sample data. No live task was opened.")
                .setPositiveButton("Got it", null)
                .show()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        actions.addView(taskActionButton("Mark complete", true) {
            AlertDialog.Builder(this@MarketTaskChatActivity)
                .setTitle("Preview completion and rating")
                .setMessage("Open the task completion and rating preview? This will not change task status or submit a review.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open rating preview") { _, _ ->
                    startActivity(Intent(this@MarketTaskChatActivity, MarketTaskRatingActivity::class.java).apply {
                        putExtra(MarketTaskRatingActivity.EXTRA_HELPER_NAME, helperName)
                        putExtra(MarketTaskRatingActivity.EXTRA_TASK_TITLE, taskTitle)
                    })
                }
                .show()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
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
        val event = makeText(MarketTaskChatUiPolicy.EVENT_LABEL, 12.5f, true,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
        }
        messageList.addView(event, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10)
        })
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
        row.addView(iconButton(MarketTaskChatIcon.PLUS, "Attachments",
            color(R.color.market_message_ink), color(R.color.market_message_bg)) {
            showAttachmentPreview()
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        messageInput = EditText(this).apply {
            hint = "Message"
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@MarketTaskChatActivity, R.font.poppins)
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(dp(18), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            filters = arrayOf(InputFilter.LengthFilter(MarketTaskChatUiPolicy.MAX_MESSAGE_LENGTH))
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, event ->
                val sendKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event?.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_SEND || sendKey) {
                    sendPreviewMessage()
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
            sendPreviewMessage()
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        return row
    }

    private fun sendPreviewMessage() {
        val raw = messageInput.text?.toString().orEmpty()
        val normalised = MarketTaskChatUiPolicy.normaliseMessage(raw)
        if (normalised == null) {
            Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show()
            return
        }
        messages = MarketTaskChatUiPolicy.appendPreviewMessage(messages, normalised, "Now")
        messageInput.setText("")
        renderMessages()
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        Toast.makeText(this, "Added to this preview only · Not sent.", Toast.LENGTH_SHORT).show()
    }

    private fun showAttachmentPreview() {
        val options = arrayOf("Choose file", "Share via Nearby")
        AlertDialog.Builder(this)
            .setTitle("Attach to chat")
            .setItems(options) { _, which ->
                Toast.makeText(this, options[which] + " is not connected in this preview.",
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        const val EXTRA_TASK_TITLE = "market.chat.task_title"
        const val EXTRA_PRICE_PKR = "market.chat.price_pkr"
    }
}
