package com.superiorstudent.app.feature.market.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/** Reference screen 08: Post a task, step 2 of 3 — Budget and time. */
class MarketPostTaskStep2Activity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var budgetOptions: Map<String, TextView>
    private lateinit var amountSection: LinearLayout
    private lateinit var amountInput: EditText
    private lateinit var amountError: TextView
    private lateinit var deadlineButton: LinearLayout
    private lateinit var deadlineLabel: TextView
    private lateinit var deadlineError: TextView
    private lateinit var skillsError: TextView
    private lateinit var skillChips: Map<String, TextView>

    private var selectedBudget = "Fixed"
    private val selectedSkills = linkedSetOf("Canva", "Photoshop", "Layout")
    private val availableSkills = listOf("Canva", "Photoshop", "Layout", "Illustrator", "Figma")
    private var selectedDeadline: Calendar = referenceDeadline()

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
            setPadding(dp(16), 0, dp(16), dp(20))
        }
        scroll.addView(form, FrameLayout.LayoutParams(-1, -2))

        form.addView(makeLabel("Budget"), fieldParams(bottom = 6))
        form.addView(buildBudgetSelector(), fieldParams(bottom = 12))

        amountSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        amountSection.addView(makeLabel("Amount"), fieldParams(bottom = 5))
        val amountField = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        amountField.addView(makeText("Rs.", 14f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(8) })
        amountInput = EditText(this).apply {
            setText("1,500")
            textSize = 14f
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789,")
            filters = arrayOf(InputFilter.LengthFilter(12))
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink2))
            hint = "Enter amount"
            setPadding(0, 0, 0, 0)
            background = null
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        }
        amountField.addView(amountInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        amountSection.addView(amountField, LinearLayout.LayoutParams(-1, dp(50)))
        amountError = makeText("Enter a whole amount greater than zero.", 11f, false, color(R.color.market_bad))
            .apply { visibility = View.GONE }
        amountSection.addView(amountError, fieldParams(top = 4))
        form.addView(amountSection, fieldParams(bottom = 12))

        form.addView(makeLabel("Deadline"), fieldParams(bottom = 5))
        deadlineButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Choose task deadline"
            addView(BudgetStepIconView(this@MarketPostTaskStep2Activity, BudgetStepIcon.CALENDAR, color(R.color.market_ink2)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
            deadlineLabel = makeText("", 14f, false, color(R.color.market_ink))
            addView(deadlineLabel, LinearLayout.LayoutParams(0, -1, 1f))
            setOnClickListener { showDeadlinePicker() }
        }
        form.addView(deadlineButton, LinearLayout.LayoutParams(-1, dp(50)))
        updateDeadlineLabel()
        deadlineError = makeText("Choose today or a future date.", 11f, false, color(R.color.market_bad))
            .apply { visibility = View.GONE }
        form.addView(deadlineError, fieldParams(top = 4, bottom = 12))

        form.addView(makeLabel("Skills needed (up to 5)"), fieldParams(bottom = 6))
        val firstSkillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val secondSkillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        skillChips = linkedMapOf()
        addSkill(firstSkillRow, "Canva")
        addSkill(firstSkillRow, "Photoshop", marginStart = 8)
        addSkill(firstSkillRow, "Layout", marginStart = 8)
        addSkill(secondSkillRow, "Illustrator")
        addSkill(secondSkillRow, "Figma", marginStart = 8)
        form.addView(firstSkillRow, LinearLayout.LayoutParams(-1, dp(38)))
        form.addView(secondSkillRow, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(8)
        })
        skillsError = makeText("Choose no more than five skills.", 11f, false, color(R.color.market_bad))
            .apply { visibility = View.GONE }
        form.addView(skillsError, fieldParams(top = 3))

        val safety = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_peach), 18f)
            addView(BudgetStepIconView(this@MarketPostTaskStep2Activity, BudgetStepIcon.INFO, color(R.color.market_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginEnd = dp(10)
                    topMargin = dp(1)
                })
            addView(makeText(
                "The app does not handle money. Agree on a safe split with the helper in chat, for example half after seeing a draft.",
                12f, false, color(R.color.market_ink)
            ).apply { gravity = Gravity.TOP; setLineSpacing(dp(2).toFloat(), 1f) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        form.addView(safety, fieldParams(top = 16, bottom = 12))

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        updateBudgetOptions()
        updateSkillChips()
        updateAmountVisibility()
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
            addView(BudgetStepIconView(this@MarketPostTaskStep2Activity, BudgetStepIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Post a task", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText("Step 2 of 3 · Budget and time", 12f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildProgress(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) { index ->
            val active = index < 2
            val bar = View(this).apply {
                background = rounded(
                    color(if (active) R.color.market_match else R.color.market_line),
                    99f
                )
            }
            row.addView(bar, LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        return row
    }

    private fun buildBudgetSelector(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }
        val labels = MarketPostTaskStep2UiPolicy.budgetTypes
        val options = linkedMapOf<String, TextView>()
        labels.forEachIndexed { index, label ->
            val option = makeText(label, 13f, true, color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(5), 0, dp(5), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedBudget = label
                    amountError.visibility = View.GONE
                    updateBudgetOptions()
                    updateAmountVisibility()
                }
            }
            val params = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (index > 0) params.marginStart = dp(3)
            outer.addView(option, params)
            options[label] = option
        }
        budgetOptions = options
        return outer
    }

    private fun addSkill(row: LinearLayout, label: String, marginStart: Int = 0) {
        val chip = makeText(label, 12.5f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                skillsError.visibility = View.GONE
                if (label in selectedSkills) {
                    selectedSkills.remove(label)
                } else if (selectedSkills.size >= MarketPostTaskStep2UiPolicy.MAX_SKILLS) {
                    skillsError.visibility = View.VISIBLE
                    toast("Choose up to five skills.")
                } else {
                    selectedSkills.add(label)
                }
                updateSkillChips()
            }
        }
        val params = LinearLayout.LayoutParams(-2, dp(38))
        if (marginStart > 0) params.marginStart = dp(marginStart)
        row.addView(chip, params)
        skillChips = skillChips + (label to chip)
    }

    private fun updateBudgetOptions() {
        if (!::budgetOptions.isInitialized) return
        budgetOptions.forEach { (label, view) ->
            val active = selectedBudget == label
            view.background = rounded(
                color(if (active) R.color.market_segment else R.color.market_card),
                15f
            )
            view.setTextColor(color(if (active) R.color.market_segment_ink else R.color.market_ink2))
        }
    }

    private fun updateSkillChips() {
        if (!::skillChips.isInitialized) return
        skillChips.forEach { (label, view) ->
            val active = label in selectedSkills
            view.background = rounded(
                color(if (active) R.color.market_segment else R.color.market_card),
                99f,
                if (active) null else color(R.color.market_line)
            )
            view.setTextColor(color(if (active) R.color.market_segment_ink else R.color.market_ink))
        }
    }

    private fun updateAmountVisibility() {
        amountSection.visibility = if (selectedBudget == "Free help") View.GONE else View.VISIBLE
    }

    private fun showDeadlinePicker() {
        val today = startOfToday()
        val dateDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDeadline.set(Calendar.YEAR, year)
                selectedDeadline.set(Calendar.MONTH, month)
                selectedDeadline.set(Calendar.DAY_OF_MONTH, day)
                selectedDeadline.set(Calendar.HOUR_OF_DAY, 12)
                selectedDeadline.set(Calendar.MINUTE, 0)
                selectedDeadline.set(Calendar.SECOND, 0)
                selectedDeadline.set(Calendar.MILLISECOND, 0)
                deadlineError.visibility = View.GONE
                updateDeadlineLabel()
            },
            selectedDeadline.get(Calendar.YEAR),
            selectedDeadline.get(Calendar.MONTH),
            selectedDeadline.get(Calendar.DAY_OF_MONTH)
        )
        dateDialog.datePicker.minDate = today.timeInMillis
        dateDialog.show()
    }

    private fun updateDeadlineLabel() {
        deadlineLabel.text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(selectedDeadline.time)
    }

    private fun validateAndContinue() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(amountInput.windowToken, 0)
        val result = MarketPostTaskStep2UiPolicy.validate(
            MarketPostTaskStep2Input(
                budgetType = selectedBudget,
                amount = amountInput.text?.toString().orEmpty(),
                deadlineIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDeadline.time),
                todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(startOfToday().time),
                skills = selectedSkills.toList()
            )
        )
        amountError.visibility = if (result.amountError == null || selectedBudget == "Free help") View.GONE else View.VISIBLE
        deadlineError.visibility = if (result.deadlineError == null) View.GONE else View.VISIBLE
        skillsError.visibility = if (result.skillsError == null) View.GONE else View.VISIBLE
        if (!result.isValid) {
            toast("Please check the highlighted fields.")
            return
        }
        toast("Budget and deadline look valid. Step 3 will be added in the next screen step.")
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            background = rounded(color(R.color.market_bg), 0f)
        }
        val back = makeText("Back", 15f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        val next = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            addView(BudgetStepIconView(this@MarketPostTaskStep2Activity, BudgetStepIcon.NEXT, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Next", 15f, true, color(R.color.market_match_ink)))
            setOnClickListener { validateAndContinue() }
        }
        footer.addView(back, LinearLayout.LayoutParams(0, dp(52), 1f))
        footer.addView(next, LinearLayout.LayoutParams(0, dp(52), 1.6f).apply {
            marginStart = dp(10)
        })
        return footer
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketPostTaskStep2Activity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketPostTaskStep2Activity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun referenceDeadline(): Calendar {
        val reference = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (reference.before(startOfToday())) {
            return startOfToday().apply {
                add(Calendar.DAY_OF_MONTH, 3)
                set(Calendar.HOUR_OF_DAY, 12)
            }
        }
        return reference
    }

    private fun startOfToday(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
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

    private fun makeLabel(value: String): TextView =
        makeText(value, 13f, true, color(R.color.market_ink))

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

    companion object {
        const val EXTRA_TITLE = "market.post_task.title"
        const val EXTRA_CATEGORY = "market.post_task.category"
        const val EXTRA_COURSE = "market.post_task.course"
        const val EXTRA_DESCRIPTION = "market.post_task.description"
    }
}
