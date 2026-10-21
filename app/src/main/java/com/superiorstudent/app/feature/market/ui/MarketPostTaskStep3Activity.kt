package com.superiorstudent.app.feature.market.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
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

/** Reference screen 09: Post a task, step 3 of 3 — Review. */
class MarketPostTaskStep3Activity : AppCompatActivity() {
    private lateinit var rulesCheckbox: FrameLayout
    private lateinit var rulesError: TextView
    private lateinit var root: FrameLayout
    private var rulesAcknowledged = true

    private val titleText by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_TITLE).orEmpty() }
    private val categoryText by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_CATEGORY).orEmpty() }
    private val courseText by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_COURSE).orEmpty() }
    private val descriptionText by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_DESCRIPTION).orEmpty() }
    private val budgetType by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_BUDGET_TYPE).orEmpty() }
    private val amountText by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_AMOUNT).orEmpty() }
    private val deadlineIso by lazy { intent.getStringExtra(MarketPostTaskStep2Activity.EXTRA_DEADLINE_ISO).orEmpty() }
    private val skills by lazy { intent.getStringArrayListExtra(MarketPostTaskStep2Activity.EXTRA_SKILLS)?.toList().orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        rulesAcknowledged = savedInstanceState?.getBoolean(STATE_RULES_ACKNOWLEDGED, true) ?: true
        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.market_bg)); clipToPadding = false }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })
        content.addView(buildProgress(), LinearLayout.LayoutParams(-1, dp(5)).apply {
            marginStart = dp(16); marginEnd = dp(16); topMargin = dp(10); bottomMargin = dp(14)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(18))
        }
        scroll.addView(form, FrameLayout.LayoutParams(-1, -2))
        form.addView(makeText(
            "This is how helpers will see it. It stays open for 14 days.",
            12.5f, false, color(R.color.market_ink2)
        ).apply { setLineSpacing(dp(2).toFloat(), 1f) }, fieldParams(bottom = 12))
        form.addView(buildPreviewCard(), fieldParams(bottom = 14))
        form.addView(buildRulesAcknowledgement(), fieldParams(bottom = 2))
        rulesError = makeText(
            "Read and accept the posting rules to continue.",
            11f, false, color(R.color.market_bad)
        ).apply { visibility = View.GONE }
        form.addView(rulesError, fieldParams(top = 5))

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        updateAcknowledgement()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Back"
            addView(ReviewStepIconView(this@MarketPostTaskStep3Activity, ReviewStepIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Post a task", 20f, true, color(R.color.market_ink)).apply { letterSpacing = -0.02f })
        headings.addView(makeText("Step 3 of 3 · Review", 12f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildProgress(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) { index ->
            val bar = View(this).apply { background = rounded(color(R.color.market_match), 99f) }
            row.addView(bar, LinearLayout.LayoutParams(0, dp(5), 1f).apply { if (index > 0) marginStart = dp(6) })
        }
        return row
    }

    private fun buildPreviewCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
        }
        val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (categoryText.isNotBlank()) tags.addView(makeChip(categoryText.trim(), R.color.market_peach, R.color.market_ink))
        val courseLabel = courseText.trim().takeIf { it.isNotEmpty() }?.substringBefore("·")?.trim()
        courseLabel?.let {
            tags.addView(makeChip(it, R.color.market_neutral_chip, R.color.market_ink),
                LinearLayout.LayoutParams(-2, dp(30)).apply { marginStart = dp(6) })
        }
        card.addView(tags, fieldParams(bottom = 10))
        card.addView(makeText(titleText, 16f, true, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, fieldParams(bottom = 5))
        card.addView(makeText(descriptionText, 12.5f, false, color(R.color.market_ink2)).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }, fieldParams(bottom = 13))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        metrics.addView(
            makeText(MarketPostTaskStep3UiPolicy.budgetLabel(budgetType, amountText), 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        )
        metrics.addView(ReviewStepIconView(this, ReviewStepIcon.CLOCK, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(3) })
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        metrics.addView(
            makeText(MarketPostTaskStep3UiPolicy.deadlineLabel(todayIso, deadlineIso), 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        metrics.addView(makeText("0 proposals", 11.5f, false, color(R.color.market_ink2)).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(-2, -2))
        card.addView(metrics)
        card.addView(View(this).apply { setBackgroundColor(color(R.color.market_line)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(11) })

        val poster = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatar = FrameLayout(this).apply {
            background = rounded(color(R.color.market_lav), 99f)
            addView(makeText("MA", 9f, true, color(R.color.market_ink)).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }
        poster.addView(avatar, LinearLayout.LayoutParams(dp(25), dp(25)))
        poster.addView(makeText("You", 12.5f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        poster.addView(makeText("· now", 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) })
        card.addView(poster)
        return card
    }

    private fun makeChip(label: String, backgroundColor: Int, textColor: Int): TextView =
        makeText(label, 12f, true, color(textColor)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = rounded(color(backgroundColor), 99f)
            maxLines = 1
        }

    private fun buildRulesAcknowledgement(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        rulesCheckbox = FrameLayout(this).apply {
            isClickable = true; isFocusable = true; contentDescription = "Acknowledge posting rules"
            setOnClickListener {
                rulesAcknowledged = !rulesAcknowledged
                rulesError.visibility = View.GONE
                updateAcknowledgement()
            }
        }
        row.addView(rulesCheckbox, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12); topMargin = dp(1) })
        row.addView(makeText(MarketPostTaskStep3UiPolicy.ACKNOWLEDGEMENT_TEXT, 12.5f, false, color(R.color.market_ink))
            .apply { setLineSpacing(dp(2).toFloat(), 1f) }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun updateAcknowledgement() {
        rulesCheckbox.background = if (rulesAcknowledged) rounded(color(R.color.market_match), 7f)
            else rounded(color(R.color.market_card), 7f, color(R.color.market_line))
        rulesCheckbox.removeAllViews()
        if (rulesAcknowledged) rulesCheckbox.addView(
            ReviewStepIconView(this, ReviewStepIcon.CHECK, color(R.color.market_match_ink)),
            FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
        )
        rulesCheckbox.contentDescription = if (rulesAcknowledged)
            "Posting rules acknowledged. Tap to uncheck."
        else "Posting rules not acknowledged. Tap to check."
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            background = rounded(color(R.color.market_bg), 0f)
        }
        val back = makeText("Back", 15f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        val post = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true; isFocusable = true
            addView(ReviewStepIconView(this@MarketPostTaskStep3Activity, ReviewStepIcon.CHECK, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Post task", 15f, true, color(R.color.market_match_ink)))
            setOnClickListener { validateAndPreviewOnly() }
        }
        footer.addView(back, LinearLayout.LayoutParams(0, dp(52), 1f))
        footer.addView(post, LinearLayout.LayoutParams(0, dp(52), 1.6f).apply { marginStart = dp(10) })
        return footer
    }

    private fun validateAndPreviewOnly() {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val validation = MarketPostTaskStep3UiPolicy.validate(MarketPostTaskStep3Input(
            title = titleText, category = categoryText, course = courseText, description = descriptionText,
            budgetType = budgetType, amount = amountText, deadlineIso = deadlineIso, todayIso = todayIso,
            skills = skills, rulesAcknowledged = rulesAcknowledged
        ))
        rulesError.visibility = if (validation.acknowledgementError == null) View.GONE else View.VISIBLE
        if (!validation.isValid) {
            toast("Please review the form and correct any incomplete fields.")
            return
        }
        toast("Preview validated. Task publishing is not connected.")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_RULES_ACKNOWLEDGED, rulesAcknowledged)
        super.onSaveInstanceState(outState)
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketPostTaskStep3Activity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketPostTaskStep3Activity)
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
        cornerRadius = radius * resources.displayMetrics.density
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size; typeface = font(medium); setTextColor(textColor)
            includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }

    private fun font(medium: Boolean): Typeface =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object { private const val STATE_RULES_ACKNOWLEDGED = "market.review.rules_acknowledged" }
}
