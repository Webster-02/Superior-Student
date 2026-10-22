package com.superiorstudent.app.feature.market.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
 * Screen 24: appeal form. Editing is local; Send appeal intentionally remains preview-only
 * until a separately approved appeal submission flow exists.
 */
class MarketAppealActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var reasonInput: EditText
    private lateinit var countLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.market_bg)) }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(root)

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        main.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildOfferSummary(), LinearLayout.LayoutParams(-1, -2))

        val promptRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        promptRow.addView(makeText(MarketAppealUiPolicy.REASON_PROMPT, 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f))
        countLabel = makeText(
            MarketAppealUiPolicy.characterCount(MarketAppealUiPolicy.DEFAULT_REASON).toString() +
                "/" + MarketAppealUiPolicy.MAX_REASON_LENGTH,
            12f, false, color(R.color.market_ink2)
        )
        promptRow.addView(countLabel, LinearLayout.LayoutParams(-2, -2))
        page.addView(promptRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })

        reasonInput = EditText(this).apply {
            setText(MarketAppealUiPolicy.DEFAULT_REASON)
            textSize = 14f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink2))
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(104)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            filters = arrayOf(InputFilter.LengthFilter(MarketAppealUiPolicy.MAX_REASON_LENGTH))
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            includeFontPadding = false
            isFocusableInTouchMode = true
            setSelection(text?.length ?: 0)
        }
        page.addView(reasonInput, LinearLayout.LayoutParams(-1, -2))
        reasonInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty()
                countLabel.text = MarketAppealUiPolicy.characterCount(value).toString() +
                    "/" + MarketAppealUiPolicy.MAX_REASON_LENGTH
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        page.addView(buildInfoBanner(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        main.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
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
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Back"
            addView(MarketAppealIconView(this@MarketAppealActivity, MarketAppealGlyph.BACK,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(makeText(MarketAppealUiPolicy.TITLE, 20f, true, color(R.color.market_ink)))
        heading.addView(makeText(MarketAppealUiPolicy.SUBTITLE, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })
        row.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildOfferSummary(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(makeText(MarketAppealUiPolicy.OFFER_TITLE, 14f, true, color(R.color.market_ink)))
        texts.addView(makeText(MarketAppealUiPolicy.OFFER_SUBTITLE, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val badge = makeText(MarketAppealUiPolicy.REMOVED_LABEL, 12f, true, color(R.color.market_ink))
        badge.gravity = Gravity.CENTER
        badge.setPadding(dp(12), dp(7), dp(12), dp(7))
        badge.background = rounded(color(R.color.market_peach), 99f)
        card.addView(badge, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        return card
    }

    private fun buildInfoBanner(): View {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            background = rounded(color(R.color.market_sky), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        banner.addView(MarketAppealIconView(this, MarketAppealGlyph.INFO, color(R.color.market_ink)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12); topMargin = dp(1) })
        banner.addView(makeText(MarketAppealUiPolicy.INFO_TEXT, 12f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return banner
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val send = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true; isFocusable = true
            contentDescription = MarketAppealUiPolicy.SEND_BUTTON
            setPadding(dp(14), 0, dp(14), 0)
            addView(MarketAppealIconView(this@MarketAppealActivity, MarketAppealGlyph.SEND,
                color(R.color.market_match_ink)), LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginEnd = dp(10)
            })
            addView(makeText(MarketAppealUiPolicy.SEND_BUTTON, 16f, true, color(R.color.market_match_ink)))
            setOnClickListener { sendAppealPreview() }
        }
        footer.addView(send, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun sendAppealPreview() {
        val reason = reasonInput.text?.toString().orEmpty()
        if (!MarketAppealUiPolicy.isReasonValid(reason)) {
            AlertDialog.Builder(this).setTitle(MarketAppealUiPolicy.EMPTY_REASON_TITLE)
                .setMessage(MarketAppealUiPolicy.EMPTY_REASON_MESSAGE)
                .setPositiveButton("Got it", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(MarketAppealUiPolicy.PREVIEW_TITLE)
            .setMessage(MarketAppealUiPolicy.PREVIEW_MESSAGE)
            .setPositiveButton("Got it", null).show()
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size; typeface = font(medium); setTextColor(textColor)
            includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }
    private fun font(medium: Boolean) = ResourcesCompat.getFont(this,
        if (medium) R.font.poppins_medium else R.font.poppins)
        ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketAppealActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketAppealActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
