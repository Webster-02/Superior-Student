package com.superiorstudent.app.feature.market.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 27: searchable-by-category FAQ accordion, matching the supplied light/dark references. */
class MarketFaqActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var categoryBar: HorizontalScrollView
    private lateinit var questionCard: LinearLayout
    private val categoryChips = linkedMapOf<MarketFaqCategory, TextView>()

    private var selectedCategory = MarketFaqCategory.ALL
    private var expandedQuestionTitle = MarketFaqUiPolicy.INITIAL_QUESTION_TITLE

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)

        val incomingQuestion = MarketFaqUiPolicy.questionByTitle(
            intent.getStringExtra(EXTRA_QUESTION_TITLE)
        )
        selectedCategory = savedInstanceState?.getString(STATE_CATEGORY)
            ?.let { runCatching { MarketFaqCategory.valueOf(it) }.getOrNull() }
            ?: incomingQuestion?.category
            ?: MarketFaqCategory.ALL
        expandedQuestionTitle = savedInstanceState?.getString(STATE_EXPANDED_QUESTION)
            ?: incomingQuestion?.title
            ?: MarketFaqUiPolicy.INITIAL_QUESTION_TITLE

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipChildren = false
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(main, FrameLayout.LayoutParams(-1, -1))

        main.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })

        categoryBar = buildCategoryBar()
        main.addView(categoryBar, LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
            clipToPadding = false
        }
        scroll.addView(page, android.widget.FrameLayout.LayoutParams(-1, -2))

        questionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        page.addView(questionCard, LinearLayout.LayoutParams(-1, -2))

        renderCategoryChips()
        renderQuestions()
        applyWindowInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, selectedCategory.name)
        outState.putString(STATE_EXPANDED_QUESTION, expandedQuestionTitle)
        super.onSaveInstanceState(outState)
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { finish() }
            addView(
                MarketFaqIconView(this@MarketFaqActivity, MarketFaqGlyph.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
            )
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = dp(12)
        })
        row.addView(
            makeText(MarketFaqUiPolicy.TITLE, 20f, true, color(R.color.market_ink)).apply {
                letterSpacing = -0.02f
            },
            LinearLayout.LayoutParams(0, -1, 1f)
        )
        return row
    }

    private fun buildCategoryBar(): HorizontalScrollView {
        val bar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(16), 0, dp(16), 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        MarketFaqUiPolicy.categories.forEach { category ->
            val chip = makeText(category.label, 12.5f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                minHeight = dp(36)
                isClickable = true
                isFocusable = true
                contentDescription = category.label
                setOnClickListener { selectCategory(category) }
            }
            categoryChips[category] = chip
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(36)).apply {
                marginEnd = dp(8)
            })
        }
        bar.addView(row, android.widget.FrameLayout.LayoutParams(-2, -1))
        return bar
    }

    private fun selectCategory(category: MarketFaqCategory) {
        if (selectedCategory == category) return
        selectedCategory = category
        expandedQuestionTitle = MarketFaqUiPolicy.questionsFor(category).firstOrNull()?.title.orEmpty()
        renderCategoryChips()
        renderQuestions()
    }

    private fun renderCategoryChips() {
        categoryChips.forEach { (category, chip) ->
            val selected = category == selectedCategory
            chip.background = rounded(
                fill = color(if (selected) R.color.market_segment else R.color.market_card),
                radius = 99f,
                stroke = if (selected) null else color(R.color.market_line)
            )
            chip.setTextColor(
                color(if (selected) R.color.market_segment_ink else R.color.market_ink)
            )
        }
    }

    private fun renderQuestions() {
        if (!::questionCard.isInitialized) return
        val visibleQuestions = MarketFaqUiPolicy.questionsFor(selectedCategory)
        if (expandedQuestionTitle.isNotEmpty() &&
            visibleQuestions.none { it.title == expandedQuestionTitle }
        ) {
            expandedQuestionTitle = visibleQuestions.firstOrNull()?.title.orEmpty()
        }

        questionCard.removeAllViews()
        visibleQuestions.forEachIndexed { index, question ->
            val expanded = question.title == expandedQuestionTitle
            questionCard.addView(
                buildQuestionRow(question, expanded),
                LinearLayout.LayoutParams(-1, -2)
            )
            if (index < visibleQuestions.lastIndex) {
                questionCard.addView(View(this).apply {
                    setBackgroundColor(color(R.color.market_line))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
    }

    private fun buildQuestionRow(question: MarketFaqQuestion, expanded: Boolean): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            makeText(question.title, 13.5f, true, color(R.color.market_ink)).apply {
                setLineSpacing(dp(1).toFloat(), 1f)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        titleRow.addView(
            MarketFaqIconView(
                this,
                if (expanded) MarketFaqGlyph.CHEVRON_DOWN else MarketFaqGlyph.CHEVRON_RIGHT,
                color(R.color.market_ink3)
            ),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                marginStart = dp(12)
            }
        )
        titleRow.setOnClickListener { toggleQuestion(question) }
        item.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        if (expanded) {
            item.addView(
                makeText(question.answer, 12.5f, false, color(R.color.market_ink2)).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )
        }
        item.setOnClickListener { toggleQuestion(question) }
        return item
    }

    private fun toggleQuestion(question: MarketFaqQuestion) {
        expandedQuestionTitle = if (expandedQuestionTitle == question.title) {
            ""
        } else {
            question.title
        }
        renderQuestions()
    }

    private fun makeText(
        value: String,
        size: Float,
        medium: Boolean,
        textColor: Int
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        typeface = font(medium)
        setTextColor(textColor)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun font(medium: Boolean): Typeface =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
            )

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketFaqActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketFaqActivity)
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

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_QUESTION_TITLE = "extra_market_faq_question_title"
        private const val STATE_CATEGORY = "market_faq_selected_category"
        private const val STATE_EXPANDED_QUESTION = "market_faq_expanded_question"
    }
}
