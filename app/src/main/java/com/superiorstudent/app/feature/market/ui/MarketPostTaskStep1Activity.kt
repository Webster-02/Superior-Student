package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences
import kotlin.math.min

/** Reference screen 07: Post a task, step 1 of 3 — Details. */
class MarketPostTaskStep1Activity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var titleInput: EditText
    private lateinit var courseInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var descriptionCount: TextView
    private lateinit var categoryError: TextView
    private lateinit var categoryChips: Map<String, TextView>
    private var selectedCategory = "Design"

    private val referenceTitle = "Poster for Optometry Society health camp"
    private val referenceCourse = "BSOPM · Ocular Anatomy"
    private val referenceDescription =
        "Need an A3 poster for our health camp on 28 Oct. Two colour options please. Text and logo will be shared on chat after selection."

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })
        content.addView(buildProgress(), LinearLayout.LayoutParams(-1, dp(5)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            topMargin = dp(10)
            bottomMargin = dp(14)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        scroll.addView(form, FrameLayout.LayoutParams(-1, -2))

        form.addView(makeLabel("Title"))
        titleInput = makeInput(referenceTitle, singleLine = true, maxLength = 100).apply {
            textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        form.addView(titleInput, fieldParams(top = 5, bottom = 12))

        form.addView(makeLabel("Category"), fieldParams(bottom = 6))
        val categoriesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chipMap = linkedMapOf<String, TextView>()
        addCategoryRow(categoriesContainer, listOf("Notes", "Tutoring", "Design", "Coding"), chipMap)
        addCategoryRow(categoriesContainer, listOf("Project", "Typing"), chipMap, topMargin = 8)
        categoryChips = chipMap
        form.addView(categoriesContainer, fieldParams(bottom = 2))

        categoryError = makeText("Choose a category to continue", 11f, false, color(R.color.market_bad))
            .apply { visibility = View.GONE }
        form.addView(categoryError, fieldParams(bottom = 10))

        form.addView(makeLabel("Course (optional)"), fieldParams(bottom = 5))
        courseInput = makeInput(referenceCourse, singleLine = true, maxLength = 80).apply {
            textSize = 14f
        }
        val courseField = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            addView(PostTaskIconView(this@MarketPostTaskStep1Activity, PostTaskIcon.SEARCH, color(R.color.market_ink2)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
            addView(courseInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        }
        form.addView(courseField, fieldParams(bottom = 12))

        val descriptionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        descriptionHeader.addView(makeLabel("Description"), LinearLayout.LayoutParams(0, -2, 1f))
        descriptionCount = makeText("${referenceDescription.length}/500", 12f, false, color(R.color.market_ink2))
        descriptionHeader.addView(descriptionCount)
        form.addView(descriptionHeader, fieldParams(bottom = 5))

        descriptionInput = makeInput(referenceDescription, singleLine = false, maxLength = 500).apply {
            textSize = 13.5f
            gravity = Gravity.TOP
            minHeight = dp(104)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(3).toFloat(), 1f)
            imeOptions = EditorInfo.IME_ACTION_NONE
        }
        form.addView(descriptionInput, LinearLayout.LayoutParams(-1, dp(124)))
        descriptionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                descriptionCount.text = "${s?.length ?: 0}/500"
                descriptionCount.setTextColor(color(
                    if ((s?.length ?: 0) > 500) R.color.market_bad else R.color.market_ink2
                ))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        updateCategoryChips()
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
            addView(PostTaskIconView(this@MarketPostTaskStep1Activity, PostTaskIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Post a task", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText("Step 1 of 3 · Details", 12f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildProgress(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        repeat(3) { index ->
            val bar = View(this).apply {
                background = rounded(
                    color(if (index == 0) R.color.market_match else R.color.market_line),
                    99f
                )
            }
            row.addView(bar, LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        return row
    }

    private fun addCategoryRow(
        parent: LinearLayout,
        labels: List<String>,
        chips: MutableMap<String, TextView>,
        topMargin: Int = 0
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.forEachIndexed { index, label ->
            val chip = makeText(label, 12.5f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCategory = label
                    updateCategoryChips()
                }
            }
            val params = LinearLayout.LayoutParams(0, dp(38), 1f)
            if (index > 0) params.marginStart = dp(7)
            row.addView(chip, params)
            chips[label] = chip
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(38)).apply {
            if (topMargin > 0) this.topMargin = dp(topMargin)
        })
        if (labels.size == 2) {
            row.removeAllViews()
            labels.forEachIndexed { index, label ->
                val chip = chips.getValue(label)
                val params = LinearLayout.LayoutParams(-2, dp(38))
                if (index > 0) params.marginStart = dp(8)
                row.addView(chip, params)
            }
        }
    }

    private fun updateCategoryChips() {
        if (!::categoryChips.isInitialized) return
        categoryChips.forEach { (label, chip) ->
            val active = selectedCategory == label
            chip.background = rounded(
                color(if (active) R.color.market_segment else R.color.market_card),
                99f,
                if (active) null else color(R.color.market_line)
            )
            chip.setTextColor(color(if (active) R.color.market_segment_ink else R.color.market_ink))
        }
    }

    private fun makeLabel(value: String): TextView =
        makeText(value, 13f, true, color(R.color.market_ink))

    private fun makeInput(value: String, singleLine: Boolean, maxLength: Int): EditText =
        EditText(this).apply {
            setText(value)
            setSingleLine(singleLine)
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink2))
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            setPadding(dp(14), if (singleLine) 0 else dp(12), dp(14), if (singleLine) 0 else dp(12))
            if (singleLine) {
                layoutParams = LinearLayout.LayoutParams(-1, dp(50))
            }
        }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            addView(PostTaskIconView(this@MarketPostTaskStep1Activity, PostTaskIcon.NEXT, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Next", 15f, true, color(R.color.market_match_ink)))
        }
        button.setOnClickListener { validateAndContinue() }
        footer.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun validateAndContinue() {
        val result = MarketPostTaskStep1UiPolicy.validate(
            MarketPostTaskStep1Input(
                title = titleInput.text?.toString().orEmpty(),
                category = selectedCategory,
                course = courseInput.text?.toString().orEmpty(),
                description = descriptionInput.text?.toString().orEmpty()
            )
        )
        titleInput.error = result.titleError
        descriptionInput.error = result.descriptionError
        categoryError.visibility = if (result.categoryError == null) View.GONE else View.VISIBLE
        if (!result.isValid) {
            toast("Please check the highlighted fields.")
            return
        }
        toast("Step 1 details look valid. Step 2 will be added in the next screen step.")
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketPostTaskStep1Activity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketPostTaskStep1Activity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun fieldParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
        }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
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

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

