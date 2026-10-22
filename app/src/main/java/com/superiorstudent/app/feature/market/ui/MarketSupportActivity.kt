package com.superiorstudent.app.feature.market.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
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

/** Screen 29: reference-matched support form. It deliberately does not fake a live submission. */
class MarketSupportActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var subjectInput: EditText
    private lateinit var detailsInput: EditText
    private lateinit var detailsCounter: TextView
    private val categoryChips = linkedMapOf<MarketSupportCategory, TextView>()
    private var selectedCategory = MarketSupportCategory.LISTING_PROBLEM

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipChildren = false
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        main.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })

        val panel = FrameLayout(this)
        main.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        panel.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(120))
            clipToPadding = false
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))

        page.addView(makeText(
            MarketSupportUiPolicy.CATEGORY_PROMPT, 14f, true, color(R.color.market_ink)
        ), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        page.addView(buildCategoryPicker(), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        page.addView(makeText(
            MarketSupportUiPolicy.SUBJECT_LABEL, 14f, true, color(R.color.market_ink)
        ), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(5)
        })
        subjectInput = EditText(this).apply {
            setText(MarketSupportUiPolicy.INITIAL_SUBJECT)
            textSize = 14f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            minHeight = dp(50)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            includeFontPadding = false
            contentDescription = MarketSupportUiPolicy.SUBJECT_LABEL
        }
        page.addView(subjectInput, LinearLayout.LayoutParams(-1, dp(50)).apply {
            bottomMargin = dp(12)
        })

        val detailsHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        detailsHeading.addView(makeText(
            MarketSupportUiPolicy.DETAILS_LABEL, 14f, true, color(R.color.market_ink)
        ), LinearLayout.LayoutParams(0, -2, 1f))
        detailsCounter = makeText(
            MarketSupportUiPolicy.counterFor(MarketSupportUiPolicy.INITIAL_DETAILS.length),
            12f, false, color(R.color.market_ink2)
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            contentDescription = "Details character count"
        }
        detailsHeading.addView(detailsCounter, LinearLayout.LayoutParams(-2, -2))
        page.addView(detailsHeading, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(5)
        })

        detailsInput = EditText(this).apply {
            setText(MarketSupportUiPolicy.INITIAL_DETAILS)
            textSize = 13.5f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            gravity = Gravity.TOP or Gravity.START
            minHeight = dp(104)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
            filters = arrayOf(InputFilter.LengthFilter(MarketSupportUiPolicy.MAX_DETAILS_CHARS))
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1f)
            contentDescription = MarketSupportUiPolicy.DETAILS_LABEL
        }
        page.addView(detailsInput, LinearLayout.LayoutParams(-1, dp(104)).apply {
            bottomMargin = dp(12)
        })
        detailsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                detailsCounter.text = MarketSupportUiPolicy.counterFor(s?.length ?: 0)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        page.addView(buildAttachmentCard(), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        page.addView(buildSafetyReminder(), LinearLayout.LayoutParams(-1, -2))

        panel.addView(buildFooter(), FrameLayout.LayoutParams(-1, dp(84), Gravity.BOTTOM))

        refreshCategoryChips()
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
            addView(
                MarketSupportIconView(this@MarketSupportActivity, MarketSupportGlyph.BACK,
                    color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            )
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeText(
            MarketSupportUiPolicy.TITLE, 20f, true, color(R.color.market_ink)
        ))
        titleColumn.addView(makeText(
            MarketSupportUiPolicy.SUBTITLE, 12f, false, color(R.color.market_ink2)
        ), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(1)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildCategoryPicker(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val first = MarketSupportUiPolicy.categories.take(3)
        val second = MarketSupportUiPolicy.categories.drop(3)
        first.forEachIndexed { index, category ->
            firstRow.addView(buildCategoryChip(category), LinearLayout.LayoutParams(-2, dp(36)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        second.forEachIndexed { index, category ->
            secondRow.addView(buildCategoryChip(category), LinearLayout.LayoutParams(-2, dp(36)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        column.addView(firstRow, LinearLayout.LayoutParams(-1, dp(36)))
        column.addView(secondRow, LinearLayout.LayoutParams(-1, dp(36)).apply {
            topMargin = dp(8)
        })
        return column
    }

    private fun buildCategoryChip(category: MarketSupportCategory): TextView =
        makeText(category.label, 12.5f, true, color(R.color.market_ink)).apply {
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(36)
            isClickable = true
            isFocusable = true
            contentDescription = category.label
            categoryChips[category] = this
            setOnClickListener {
                selectedCategory = category
                refreshCategoryChips()
            }
        }

    private fun refreshCategoryChips() {
        if (categoryChips.isEmpty()) return
        val dark = AppThemePreferences.isNight(this)
        categoryChips.forEach { (category, chip) ->
            val selected = category == selectedCategory
            val fill = if (selected) {
                if (dark) color(R.color.market_match) else color(R.color.market_ink)
            } else {
                color(R.color.market_card)
            }
            val textColor = if (selected) {
                if (dark) color(R.color.market_match_ink) else Color.WHITE
            } else {
                color(R.color.market_ink)
            }
            chip.background = rounded(fill, 99f, if (selected) null else color(R.color.market_line))
            chip.setTextColor(textColor)
            chip.isSelected = selected
        }
    }

    private fun buildAttachmentCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            setPadding(dp(14), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            contentDescription = MarketSupportUiPolicy.ATTACH_TITLE
            setOnClickListener {
                AlertDialog.Builder(this@MarketSupportActivity)
                    .setTitle(MarketSupportUiPolicy.ATTACHMENT_NOT_CONNECTED_TITLE)
                    .setMessage(MarketSupportUiPolicy.ATTACHMENT_NOT_CONNECTED_MESSAGE)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(R.color.market_lav), 13f)
            addView(
                MarketSupportIconView(this@MarketSupportActivity, MarketSupportGlyph.BRIEFCASE,
                    color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            )
        }
        card.addView(iconTile, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            marginEnd = dp(12)
        })
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(makeText(
            MarketSupportUiPolicy.ATTACH_TITLE, 13f, true, color(R.color.market_ink)
        ))
        textColumn.addView(makeText(
            MarketSupportUiPolicy.SAMPLE_LISTING_TITLE, 12f, false, color(R.color.market_ink2)
        ), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
        })
        card.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(8)
        })
        card.addView(
            MarketSupportIconView(this, MarketSupportGlyph.CHEVRON_RIGHT, color(R.color.market_ink3)),
            LinearLayout.LayoutParams(dp(18), dp(18))
        )
        return card
    }

    private fun buildSafetyReminder(): View {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(color(R.color.market_peach), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        note.addView(
            MarketSupportIconView(this, MarketSupportGlyph.SHIELD, color(R.color.market_ink)),
            LinearLayout.LayoutParams(dp(20), dp(24)).apply {
                marginEnd = dp(12)
            }
        )
        note.addView(makeText(
            MarketSupportUiPolicy.SAFETY_MESSAGE, 12f, false, color(R.color.market_ink)
        ).apply {
            gravity = Gravity.TOP
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return note
    }

    private fun buildFooter(): View {
        val footer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, color(R.color.market_bg), color(R.color.market_bg))
            )
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            contentDescription = MarketSupportUiPolicy.SEND_BUTTON
            setOnClickListener { trySubmit() }
        }
        button.addView(
            MarketSupportIconView(this, MarketSupportGlyph.SEND, color(R.color.market_match_ink)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
        )
        button.addView(makeText(
            MarketSupportUiPolicy.SEND_BUTTON, 15f, true, color(R.color.market_match_ink)
        ))
        footer.addView(button, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
        return footer
    }

    private fun trySubmit() {
        if (subjectInput.text.toString().isBlank()) {
            subjectInput.error = MarketSupportUiPolicy.SUBJECT_REQUIRED
            subjectInput.requestFocus()
            return
        }
        if (detailsInput.text.toString().isBlank()) {
            detailsInput.error = MarketSupportUiPolicy.DETAILS_REQUIRED
            detailsInput.requestFocus()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(MarketSupportUiPolicy.SUPPORT_NOT_CONNECTED_TITLE)
            .setMessage(MarketSupportUiPolicy.SUPPORT_NOT_CONNECTED_MESSAGE)
            .setPositiveButton("OK", null)
            .show()
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

    private fun font(medium: Boolean) =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketSupportActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketSupportActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
