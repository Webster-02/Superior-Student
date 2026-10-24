package com.superiorstudent.app.feature.market.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 11: submit a proposal for a live task. */
class MarketSendProposalActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var messageInput: EditText
    private lateinit var messageCounter: TextView
    private lateinit var messageError: TextView
    private lateinit var priceInput: EditText
    private lateinit var priceError: TextView
    private lateinit var deliveryInput: EditText
    private lateinit var deliveryError: TextView
    private lateinit var submitButton: LinearLayout
    private val repository by lazy { MarketRepository() }
    private val quickPickViews = mutableMapOf<Int, TextView>()

    private val taskId by lazy { intent.getStringExtra(EXTRA_TASK_ID).orEmpty() }
    private val taskTitle by lazy {
        intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
    }
    private val taskCategory by lazy {
        intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
    }
    private val taskBudget by lazy {
        intent.getStringExtra(EXTRA_BUDGET).orEmpty()
    }
    private val taskDeadline by lazy {
        intent.getStringExtra(EXTRA_DEADLINE).orEmpty()
    }
    private val taskAuthor by lazy {
        intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isSmoothScrollingEnabled = true
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        scroll.addView(form, FrameLayout.LayoutParams(-1, -2))

        form.addView(buildTaskSummary(), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        form.addView(buildMessageField(), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        form.addView(buildPriceAndDeliveryRow(), LinearLayout.LayoutParams(-1, -2))
        form.addView(makeText("Quick pick", 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(0)
                bottomMargin = dp(10)
            })
        form.addView(buildQuickPicks(), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        form.addView(buildInfoPanel(), LinearLayout.LayoutParams(-1, -2))

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        refreshQuickPickStyles()
        updateMessageCounter()
        applyWindowInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            addView(ProposalScreenIconView(this@MarketSendProposalActivity,
                ProposalScreenIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Send proposal", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText(MarketSendProposalUiPolicy.taskSubtitle(taskId, taskTitle),
            12f, false, color(R.color.market_ink2)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildTaskSummary(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
        }
        val taskInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        taskInfo.addView(makeText(taskTitle, 13.5f, true, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val summary = listOfNotNull(
            taskBudget.takeIf { it.isNotBlank() },
            taskDeadline.removePrefix("Due ").takeIf { it.isNotBlank() }?.let { "Due $it" }
        ).joinToString(" · ").ifBlank { "Task details unavailable" }
        taskInfo.addView(makeText(summary,
            12f, false, color(R.color.market_ink2)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.addView(taskInfo, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(makeChip(taskCategory.ifBlank { "Task" }), LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(10)
        })
        return card
    }

    private fun buildMessageField(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(makeText("Your message", 13f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f))
        messageCounter = makeText("", 12f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        labelRow.addView(messageCounter)
        column.addView(labelRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(6)
        })

        messageInput = EditText(this).apply {
            setText("")
            textSize = 13.5f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            hint = "Introduce yourself and explain how you can help."
            gravity = Gravity.TOP or Gravity.START
            includeFontPadding = false
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(104)
            maxHeight = dp(180)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
            filters = arrayOf(InputFilter.LengthFilter(MarketSendProposalUiPolicy.MAX_MESSAGE_LENGTH))
            setLineSpacing(dp(2).toFloat(), 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateMessageCounter()
                    messageError.visibility = View.GONE
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        column.addView(messageInput, LinearLayout.LayoutParams(-1, dp(104)))
        messageError = makeText("", 11f, false, color(R.color.market_bad)).apply {
            visibility = View.GONE
        }
        column.addView(messageError, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        return column
    }

    private fun buildPriceAndDeliveryRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val priceColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        priceColumn.addView(makeText("Your price", 13f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        val priceBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        priceBox.addView(makeText("Rs.", 14f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        priceInput = EditText(this).apply {
            setText("")
            textSize = 14f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            hint = "1,200"
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(0, dp(5), 0, dp(5))
            background = ColorDrawable(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789,")
            imeOptions = EditorInfo.IME_ACTION_NEXT
            filters = arrayOf(InputFilter.LengthFilter(12))
            addTextChangedListener(simpleTextWatcher { priceError.visibility = View.GONE })
        }
        priceBox.addView(priceInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        priceColumn.addView(priceBox, LinearLayout.LayoutParams(-1, dp(50)))
        priceError = makeText("", 10f, false, color(R.color.market_bad)).apply { visibility = View.GONE }
        priceColumn.addView(priceError, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        val deliveryColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deliveryColumn.addView(makeText("Delivery", 13f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        val deliveryBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        deliveryInput = EditText(this).apply {
            setText(MarketSendProposalUiPolicy.DEFAULT_DELIVERY_DAYS)
            textSize = 14f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            hint = "2"
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(0, dp(5), dp(4), dp(5))
            background = ColorDrawable(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(2))
            addTextChangedListener(simpleTextWatcher {
                deliveryError.visibility = View.GONE
                refreshQuickPickStyles()
            })
        }
        deliveryBox.addView(deliveryInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        deliveryBox.addView(makeText("days", 13f, false, color(R.color.market_ink2)))
        deliveryColumn.addView(deliveryBox, LinearLayout.LayoutParams(-1, dp(50)))
        deliveryError = makeText("", 10f, false, color(R.color.market_bad)).apply { visibility = View.GONE }
        deliveryColumn.addView(deliveryError, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        row.addView(priceColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(deliveryColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(10)
        })
        return row
    }

    private fun buildQuickPicks(): View {
        val horizontalScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        MarketSendProposalUiPolicy.QUICK_PICK_DAYS.forEachIndexed { index, days ->
            val chip = TextView(this).apply {
                text = days.toString() + if (days == 1) " day" else " days"
                textSize = 12f
                typeface = font(true)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(36)
                isClickable = true
                isFocusable = true
                contentDescription = "Set delivery to " + text
                setOnClickListener {
                    deliveryInput.setText(days.toString())
                    deliveryInput.setSelection(deliveryInput.text.length)
                    deliveryError.visibility = View.GONE
                    refreshQuickPickStyles()
                }
            }
            quickPickViews[days] = chip
            row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        horizontalScroll.addView(row, FrameLayout.LayoutParams(-2, -2))
        return horizontalScroll
    }

    private fun refreshQuickPickStyles() {
        if (!::deliveryInput.isInitialized) return
        val selected = deliveryInput.text.toString().toIntOrNull()
        quickPickViews.forEach { (days, chip) ->
            val isSelected = selected == days
            chip.setTextColor(color(if (isSelected) R.color.market_segment_ink else R.color.market_ink))
            chip.background = if (isSelected) rounded(color(R.color.market_segment), 99f)
                else rounded(color(R.color.market_card), 99f, color(R.color.market_line))
        }
    }

    private fun buildInfoPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_sky), 18f)
        }
        panel.addView(ProposalScreenIconView(this, ProposalScreenIcon.INFO, color(R.color.market_ink)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(12)
                topMargin = dp(1)
            })
        panel.addView(makeText(
            "Be specific about what you will deliver. Your account’s daily proposal limit is checked when you send.",
            12f, false, color(R.color.market_ink)
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        return panel
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00FFFFFF, color(R.color.market_bg))
            )
        }
        submitButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            contentDescription = if (taskId.isBlank()) "Task is unavailable" else "Send proposal"
            isEnabled = taskId.isNotBlank()
            alpha = if (isEnabled) 1f else 0.5f
            addView(ProposalScreenIconView(this@MarketSendProposalActivity,
                ProposalScreenIcon.SEND, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Send proposal", 15f, true, color(R.color.market_match_ink)))
            setOnClickListener { submitProposal() }
        }
        footer.addView(submitButton, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun submitProposal() {
        if (taskId.isBlank()) {
            toast("This task is unavailable. Open a task from Requests and try again.")
            return
        }
        val validation = MarketSendProposalUiPolicy.validate(
            MarketSendProposalDraft(
                message = messageInput.text.toString(),
                price = priceInput.text.toString(),
                deliveryDays = deliveryInput.text.toString()
            )
        )
        val messageErrorText = validation.messageError ?: when {
            messageInput.text.toString().trim().length < 20 ->
                "Write at least 20 characters so the poster understands your proposal."
            else -> null
        }
        setError(messageError, messageErrorText)
        setError(priceError, validation.priceError)
        setError(deliveryError, validation.deliveryError)
        if (!validation.isValid || messageErrorText != null) {
            toast("Please review the proposal details.")
            return
        }
        val price = priceInput.text.toString().replace(",", "").toDoubleOrNull()
        val deliveryDays = deliveryInput.text.toString().toIntOrNull()
        if (price == null || deliveryDays == null) {
            toast("Please review the proposal details.")
            return
        }
        submitButton.isEnabled = false
        submitButton.alpha = 0.6f
        repository.createProposal(taskId, messageInput.text.toString(), price, deliveryDays)
            .addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                submitButton.isEnabled = true
                submitButton.alpha = 1f
                if (result.isSuccessful) {
                    toast("Proposal sent to the task poster.")
                    finish()
                } else {
                    toast(result.exception?.localizedMessage
                        ?: "Your proposal could not be sent. Please try again.")
                }
            }
    }

    private fun setError(view: TextView, message: String?) {
        view.text = message.orEmpty()
        view.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun updateMessageCounter() {
        if (::messageCounter.isInitialized && ::messageInput.isInitialized) {
            messageCounter.text = MarketSendProposalUiPolicy.messageCounter(messageInput.text.toString())
        }
    }

    private fun simpleTextWatcher(onChange: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChange()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketSendProposalActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketSendProposalActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeChip(label: String): TextView =
        makeText(label, 11f, true, color(R.color.market_ink)).apply {
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = rounded(color(R.color.market_peach), 99f)
            maxLines = 1
        }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = font(medium)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun font(medium: Boolean): Typeface =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius * resources.displayMetrics.density
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_TASK_ID = "market.proposal.task_id"
        const val EXTRA_TASK_TITLE = "market.proposal.task_title"
        const val EXTRA_CATEGORY = "market.proposal.category"
        const val EXTRA_BUDGET = "market.proposal.budget"
        const val EXTRA_DEADLINE = "market.proposal.deadline"
        const val EXTRA_AUTHOR = "market.proposal.author"
    }
}
