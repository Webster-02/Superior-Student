package com.superiorstudent.app.feature.market.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
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

/** Screen 16: task completion rating. UI state stays local until the real task/review flow is approved. */
class MarketTaskRatingActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var starsRow: LinearLayout
    private lateinit var tagsContainer: LinearLayout
    private lateinit var commentInput: EditText
    private lateinit var commentCounter: TextView

    private var selectedRating = MarketTaskRatingUiPolicy.DEFAULT_RATING
    private val selectedTags = linkedSetOf<String>().apply {
        addAll(MarketTaskRatingUiPolicy.defaultSelectedTags)
    }
    private var initialComment = MarketTaskRatingUiPolicy.DEFAULT_COMMENT

    private val helperName: String
        get() = intent.getStringExtra(EXTRA_HELPER_NAME).orEmpty()
            .ifBlank { MarketTaskRatingUiPolicy.DEFAULT_HELPER_NAME }

    private val taskTitle: String
        get() = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
            .ifBlank { MarketTaskRatingUiPolicy.DEFAULT_TASK_TITLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        selectedRating = MarketTaskRatingUiPolicy.clampRating(
            savedInstanceState?.getInt(STATE_RATING, MarketTaskRatingUiPolicy.DEFAULT_RATING)
                ?: MarketTaskRatingUiPolicy.DEFAULT_RATING
        )
        selectedTags.clear()
        selectedTags.addAll(savedInstanceState?.getStringArrayList(STATE_TAGS)
            ?: MarketTaskRatingUiPolicy.defaultSelectedTags.toList())
        initialComment = savedInstanceState?.getString(STATE_COMMENT)
            ?: MarketTaskRatingUiPolicy.DEFAULT_COMMENT

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

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))

        body.addView(buildCompletedHero(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(18)
        })
        starsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        body.addView(starsRow, LinearLayout.LayoutParams(-1, dp(44)).apply {
            bottomMargin = dp(18)
        })

        tagsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(tagsContainer, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(16)
        })

        body.addView(buildCommentField(), LinearLayout.LayoutParams(-1, -2))
        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))

        renderStars()
        renderTags()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RATING, selectedRating)
        outState.putStringArrayList(STATE_TAGS, ArrayList(selectedTags))
        if (::commentInput.isInitialized) {
            outState.putString(STATE_COMMENT, commentInput.text?.toString().orEmpty())
        }
        super.onSaveInstanceState(outState)
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
            addView(MarketProposalsIconView(this@MarketTaskRatingActivity,
                MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(makeText("Complete task", 20f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildCompletedHero(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val check = FrameLayout(this).apply {
            background = oval(color(R.color.market_match))
            addView(TextView(this@MarketTaskRatingActivity).apply {
                text = "✓"
                textSize = 42f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(color(R.color.market_match_ink))
            }, FrameLayout.LayoutParams(-1, -1))
        }
        hero.addView(check, LinearLayout.LayoutParams(dp(78), dp(78)))
        hero.addView(makeText("Task completed", 22f, true,
            color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        hero.addView(makeText("How was your experience with $helperName?", 12.5f, false,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return hero
    }

    private fun renderStars() {
        if (!::starsRow.isInitialized) return
        starsRow.removeAllViews()
        for (value in 1..MarketTaskRatingUiPolicy.MAX_RATING) {
            val selected = value <= selectedRating
            val star = makeText(if (selected) "★" else "☆", 37f, true,
                if (selected) Color.rgb(232, 161, 0) else color(R.color.market_ink3)).apply {
                gravity = Gravity.CENTER
                contentDescription = "$value star" + if (value == 1) "" else "s"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedRating = MarketTaskRatingUiPolicy.clampRating(value)
                    renderStars()
                }
            }
            starsRow.addView(star, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
    }

    private fun renderTags() {
        if (!::tagsContainer.isInitialized) return
        tagsContainer.removeAllViews()
        val groups = listOf(
            MarketTaskRatingUiPolicy.tags.take(3),
            MarketTaskRatingUiPolicy.tags.drop(3)
        )
        groups.forEachIndexed { index, labels ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            labels.forEach { label ->
                row.addView(buildTag(label), LinearLayout.LayoutParams(-2, dp(40)).apply {
                    if (row.childCount > 0) marginStart = dp(8)
                })
            }
            tagsContainer.addView(row, LinearLayout.LayoutParams(-1, dp(40)).apply {
                if (index > 0) topMargin = dp(8)
            })
        }
    }

    private fun buildTag(label: String): TextView {
        val selected = label in selectedTags
        return makeText(label, 12.5f, true,
            color(if (selected) R.color.market_segment_ink else R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(14), 0, dp(14), 0)
            background = if (selected) {
                rounded(color(R.color.market_segment), 99f)
            } else {
                rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            }
            isClickable = true
            isFocusable = true
            contentDescription = label + if (selected) " selected" else " not selected"
            setOnClickListener {
                val updated = MarketTaskRatingUiPolicy.toggleTag(selectedTags.toSet(), label)
                selectedTags.clear()
                selectedTags.addAll(updated)
                renderTags()
            }
        }
    }

    private fun buildCommentField(): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(makeText("Comment (optional)", 13.5f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(0, -2, 1f))
        commentCounter = makeText(
            MarketTaskRatingUiPolicy.commentCounter(initialComment), 12.5f, false,
            color(R.color.market_ink2)
        )
        labelRow.addView(commentCounter, LinearLayout.LayoutParams(-2, -2))
        section.addView(labelRow)
        commentInput = EditText(this).apply {
            setText(initialComment)
            hint = "Share a few details about your experience"
            textSize = 13.5f
            typeface = ResourcesCompat.getFont(this@MarketTaskRatingActivity, R.font.poppins)
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(color(R.color.market_ink))
            setHintTextColor(color(R.color.market_ink3))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            filters = arrayOf(InputFilter.LengthFilter(MarketTaskRatingUiPolicy.MAX_COMMENT_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 5
            setLineSpacing(dp(2).toFloat(), 1f)
            isVerticalScrollBarEnabled = false
        }
        section.addView(commentInput, LinearLayout.LayoutParams(-1, dp(104)).apply {
            topMargin = dp(8)
        })
        commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                commentCounter.text = MarketTaskRatingUiPolicy.commentCounter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        return section
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
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(color(R.color.market_match), 16f)
            minimumHeight = dp(52)
            isClickable = true
            isFocusable = true
            contentDescription = "Submit rating"
            addView(TextView(this@MarketTaskRatingActivity).apply {
                text = "✓"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(color(R.color.market_match_ink))
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(makeText("Submit rating", 15f, true,
                color(R.color.market_match_ink)), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(8)
            })
            setOnClickListener { showPreviewConfirmation() }
        }
        footer.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun showPreviewConfirmation() {
        val comment = MarketTaskRatingUiPolicy.normaliseComment(
            commentInput.text?.toString().orEmpty()
        )
        if (comment == null) {
            AlertDialog.Builder(this)
                .setTitle("Check comment")
                .setMessage("Keep the optional comment to 200 characters or fewer.")
                .setPositiveButton("Got it", null)
                .show()
            return
        }
        val reasons = if (selectedTags.isEmpty()) "No tags selected" else selectedTags.joinToString(", ")
        AlertDialog.Builder(this)
            .setTitle("Preview only")
            .setMessage("Your $selectedRating-star rating is ready, with: $reasons. No rating will be submitted or saved in this screen.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketTaskRatingActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketTaskRatingActivity)
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
            typeface = ResourcesCompat.getFont(this@MarketTaskRatingActivity,
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
        const val EXTRA_HELPER_NAME = "market.rating.helper_name"
        const val EXTRA_TASK_TITLE = "market.rating.task_title"
        private const val STATE_RATING = "market.rating.selected_rating"
        private const val STATE_TAGS = "market.rating.selected_tags"
        private const val STATE_COMMENT = "market.rating.comment"
    }
}
