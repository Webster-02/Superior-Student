package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

/** Screen 26 native reference implementation. FAQs open Screen 27; other topic actions remain previews. */
class MarketHelpActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var searchInput: EditText
    private lateinit var topicsGrid: LinearLayout
    private lateinit var questionSection: LinearLayout
    private lateinit var questionList: LinearLayout
    private lateinit var emptyResults: TextView
    private lateinit var resultsScroll: ScrollView

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
        resultsScroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        panel.addView(resultsScroll, FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(126))
            clipToPadding = false
        }
        resultsScroll.addView(page, FrameLayout.LayoutParams(-1, -2))

        page.addView(buildSearchField(), LinearLayout.LayoutParams(-1, dp(50)).apply {
            bottomMargin = dp(14)
        })
        topicsGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(topicsGrid, LinearLayout.LayoutParams(-1, -2))
        questionSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        questionSection.addView(makeText(MarketHelpUiPolicy.POPULAR_QUESTIONS, 16f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        questionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        questionSection.addView(questionList, LinearLayout.LayoutParams(-1, -2))
        page.addView(questionSection, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })
        emptyResults = makeText(MarketHelpUiPolicy.EMPTY_RESULTS, 13f, false,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            visibility = View.GONE
        }
        page.addView(emptyResults, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })

        panel.addView(buildFooter(), FrameLayout.LayoutParams(-1, dp(108), Gravity.BOTTOM))
        wireSearch()
        renderSearchResults("")
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
            addView(MarketHelpIconView(this@MarketHelpActivity, MarketHelpGlyph.BACK,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeText(MarketHelpUiPolicy.TITLE, 20f, true, color(R.color.market_ink)))
        titleColumn.addView(makeText(MarketHelpUiPolicy.SUBTITLE, 12f, false,
            color(R.color.market_ink2)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(1)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildSearchField(): View {
        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            setPadding(dp(14), 0, dp(12), 0)
        }
        field.addView(MarketHelpIconView(this, MarketHelpGlyph.SEARCH, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) })
        searchInput = EditText(this).apply {
            hint = MarketHelpUiPolicy.SEARCH_HINT
            textSize = 14f
            typeface = font(false)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(0, 0, 0, 0)
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            includeFontPadding = false
            contentDescription = MarketHelpUiPolicy.SEARCH_HINT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    keyboard?.hideSoftInputFromWindow(searchInput.windowToken, 0)
                    searchInput.clearFocus()
                    true
                } else false
            }
        }
        field.addView(searchInput, LinearLayout.LayoutParams(0, -1, 1f))
        return field
    }

    private fun wireSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSearchResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderSearchResults(query: String) {
        if (!::topicsGrid.isInitialized || !::questionList.isInitialized) return
        val matchingTopics = MarketHelpUiPolicy.topicsFor(query)
        topicsGrid.removeAllViews()
        val topicRows = matchingTopics.chunked(2)
        topicRows.forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            pair.forEachIndexed { columnIndex, topic ->
                val tileParams = LinearLayout.LayoutParams(0, dp(92), 1f).apply {
                    if (columnIndex == 0 && pair.size > 1) marginEnd = dp(10)
                }
                row.addView(buildTopicTile(topic), tileParams)
            }
            topicsGrid.addView(row, LinearLayout.LayoutParams(-1, dp(92)).apply {
                if (rowIndex < topicRows.lastIndex) bottomMargin = dp(10)
            })
        }
        topicsGrid.visibility = if (matchingTopics.isEmpty()) View.GONE else View.VISIBLE

        val matchingQuestions = MarketHelpUiPolicy.questionsFor(query)
        questionList.removeAllViews()
        matchingQuestions.forEachIndexed { index, question ->
            questionList.addView(buildQuestionRow(question), LinearLayout.LayoutParams(-1, -2))
            if (index < matchingQuestions.lastIndex) {
                questionList.addView(View(this).apply {
                    setBackgroundColor(color(R.color.market_line))
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        questionSection.visibility = if (query.isBlank() || matchingQuestions.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        emptyResults.visibility = if (query.isNotBlank() &&
            matchingTopics.isEmpty() && matchingQuestions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildTopicTile(topic: MarketHelpTopic): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.START
            background = rounded(color(topic.backgroundRes), 20f)
            setPadding(dp(14), dp(14), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = topic.label
            setOnClickListener { openTopic(topic) }
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(withAlpha(color(R.color.market_card), 0x88), 12f)
            addView(MarketHelpIconView(this@MarketHelpActivity, topic.icon,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        }
        tile.addView(iconTile, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            bottomMargin = dp(10)
        })
        tile.addView(makeText(topic.label, 13.5f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2))
        return tile
    }

    private fun buildQuestionRow(question: MarketHelpQuestion): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            minimumHeight = dp(60)
            isClickable = true
            isFocusable = true
            contentDescription = question.title
            setOnClickListener { showQuestion(question) }
        }
        row.addView(makeText(question.title, 13.5f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(MarketHelpIconView(this, MarketHelpGlyph.CHEVRON, color(R.color.market_ink3)),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(12) })
        return row
    }

    private fun openTopic(topic: MarketHelpTopic) {
        when (topic.label) {
            MarketHelpUiPolicy.TOPIC_POSTING_RULES -> showPreview(
                topic.label, MarketHelpUiPolicy.RULES_PREVIEW
            )
            MarketHelpUiPolicy.TOPIC_SAFETY_TIPS -> showPreview(
                topic.label, MarketHelpUiPolicy.SAFETY_TIPS
            )
            MarketHelpUiPolicy.TOPIC_FAQS -> openFaq()
            MarketHelpUiPolicy.TOPIC_MY_REPORTS -> showPreview(
                topic.label, MarketHelpUiPolicy.REPORTS_PREVIEW
            )
        }
    }

    private fun showQuestion(question: MarketHelpQuestion) {
        openFaq(question.title)
    }

    private fun openFaq(questionTitle: String? = null) {
        startActivity(Intent(this, MarketFaqActivity::class.java).apply {
            if (!questionTitle.isNullOrBlank()) {
                putExtra(MarketFaqActivity.EXTRA_QUESTION_TITLE, questionTitle)
            }
        })
    }

    private fun buildFooter(): View {
        val footer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, color(R.color.market_bg), color(R.color.market_bg))
            )
            setPadding(dp(16), dp(8), dp(16), dp(10))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
            setPadding(dp(14), dp(12), dp(12), dp(12))
            isFocusable = true
        }
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(makeText(MarketHelpUiPolicy.CONTACT_TITLE, 14f, true,
            color(R.color.market_ink)))
        textColumn.addView(makeText(MarketHelpUiPolicy.CONTACT_DESCRIPTION, 12f, false,
            color(R.color.market_ink2)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(10)
        })

        val contact = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            isFocusable = true
            contentDescription = MarketHelpUiPolicy.CONTACT_BUTTON
            addView(makeText(MarketHelpUiPolicy.CONTACT_BUTTON, 13.5f, true,
                color(R.color.market_match_ink)))
            setOnClickListener { showPreview(MarketHelpUiPolicy.CONTACT_BUTTON,
                MarketHelpUiPolicy.SUPPORT_PREVIEW) }
        }
        card.addView(contact, LinearLayout.LayoutParams(-2, dp(42)))
        footer.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return footer
    }

    private fun showPreview(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message + "\n\n" + MarketHelpUiPolicy.PREVIEW_DISCLOSURE)
            .setPositiveButton("Got it", null)
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
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketHelpActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketHelpActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
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

    private fun withAlpha(value: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(value), Color.green(value), Color.blue(value))

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
