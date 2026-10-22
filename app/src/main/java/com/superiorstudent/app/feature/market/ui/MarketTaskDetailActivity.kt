package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import kotlin.math.min

/** Screen 05: task detail as seen by a student considering sending a proposal. */
class MarketTaskDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private var bookmarkedForPreview = false
    private var bookmarkButton: FrameLayout? = null

    private val model: MarketTaskDetailUiModel by lazy {
        MarketTaskDetailUiPolicy.create(
            MarketTaskDetailSeed(
                id = intent.getStringExtra(EXTRA_ID).orEmpty(),
                category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
                courseTag = intent.getStringExtra(EXTRA_COURSE),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty(),
                budgetLabel = intent.getStringExtra(EXTRA_BUDGET).orEmpty(),
                dueLabel = intent.getStringExtra(EXTRA_DUE).orEmpty(),
                proposalCount = intent.getIntExtra(EXTRA_PROPOSALS, 0),
                author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty(),
                initials = intent.getStringExtra(EXTRA_INITIALS).orEmpty(),
                ageLabel = intent.getStringExtra(EXTRA_AGE).orEmpty()
            )
        )
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
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(136))
        }
        scroll.addView(details, FrameLayout.LayoutParams(-1, -2))

        details.addView(buildCategoryTags())
        details.addView(makeText(model.title, 21f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        details.addView(buildAuthorRow(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
            bottomMargin = dp(16)
        })

        details.addView(buildMetricCards(), LinearLayout.LayoutParams(-1, -2))
        details.addView(makeText("Details", 16f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        details.addView(makeText(model.details, 13f, false, color(R.color.market_ink2)).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        details.addView(buildSkillTags(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        details.addView(buildContactLock(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })

        root.addView(buildProposalFooter(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

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
        row.addView(iconButton(TaskDetailIcon.BACK, "Back", 44) { finish() })

        row.addView(makeText("Task", 20f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
            })

        bookmarkButton = iconButton(TaskDetailIcon.BOOKMARK, "Save task", 44) {
            bookmarkedForPreview = !bookmarkedForPreview
            bookmarkButton?.alpha = if (bookmarkedForPreview) 0.62f else 1f
            toast("Bookmark is preview-only and is not saved yet.")
        }.also { row.addView(it, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginEnd = dp(10)
        }) }

        row.addView(iconButton(TaskDetailIcon.FLAG, "Report task", 44) {
            MarketReportSheet.show(this, model.author)
        })
        return row
    }

    private fun buildCategoryTags(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeChip(model.category, color(categoryColor(model.category)), color(R.color.market_ink)))
        model.courseTag?.let {
            row.addView(makeChip(it, color(R.color.market_bg), color(R.color.market_ink)),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        }
        return row
    }

    private fun buildAuthorRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeText(model.initials, 14f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = oval(color(R.color.market_sky))
        }, LinearLayout.LayoutParams(dp(40), dp(40)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(makeText(model.author, 13.5f, true, color(R.color.market_ink)))
        val rating = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rating.addView(TaskDetailIconView(this, TaskDetailIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(13), dp(13)))
        rating.addView(makeText(model.ratingLabel, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(3) })
        info.addView(rating)
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        row.addView(makeText(model.ageLabel, 12f, false, color(R.color.market_ink2)))
        return row
    }

    private fun buildMetricCards(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val metrics = listOf(
            Triple("Budget", model.budgetValue + "\n" + model.budgetFootnote, R.color.market_lav),
            Triple("Deadline", model.deadlineDate + "\n" + model.deadlineFootnote, R.color.market_peach),
            Triple("Proposals", model.proposalsValue, R.color.market_sky)
        )
        metrics.forEachIndexed { index, metric ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(11), dp(10), dp(11))
                background = rounded(color(metric.third), 18f)
                addView(makeText(metric.first, 11f, false, color(R.color.market_ink2)))
                addView(makeText(metric.second, 13.5f, true, color(R.color.market_ink)).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            }
            val params = LinearLayout.LayoutParams(0, -1, 1f)
            if (index > 0) params.marginStart = dp(8)
            row.addView(card, params)
        }
        return row
    }

    private fun buildSkillTags(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        model.skillTags.forEachIndexed { index, tag ->
            val chip = makeChip(tag, color(R.color.market_bg), color(R.color.market_ink))
            val params = LinearLayout.LayoutParams(-2, -2)
            if (index > 0) params.marginStart = dp(6)
            row.addView(chip, params)
        }
        scroll.addView(row, FrameLayout.LayoutParams(-2, -2))
        return scroll
    }

    private fun buildContactLock(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_sky), 18f)
        }
        card.addView(TaskDetailIconView(this, TaskDetailIcon.LOCK, color(R.color.market_ink)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) })
        card.addView(makeText(model.contactLockMessage, 12f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return card
    }

    private fun buildProposalFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            setPadding(dp(16), 0, dp(16), 0)
            addView(TaskDetailIconView(this@MarketTaskDetailActivity, TaskDetailIcon.SEND, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Send proposal", 15f, true, color(R.color.market_match_ink)))
        }
        button.setOnClickListener {
            startActivity(Intent(this@MarketTaskDetailActivity, MarketSendProposalActivity::class.java).apply {
                putExtra(MarketSendProposalActivity.EXTRA_TASK_ID, intent.getStringExtra(EXTRA_ID).orEmpty())
                putExtra(MarketSendProposalActivity.EXTRA_TASK_TITLE, model.title)
                putExtra(MarketSendProposalActivity.EXTRA_CATEGORY, model.category)
                putExtra(
                    MarketSendProposalActivity.EXTRA_BUDGET,
                    (model.budgetValue + " " + model.budgetFootnote).trim()
                )
                putExtra(MarketSendProposalActivity.EXTRA_DEADLINE, model.deadlineDate)
                putExtra(MarketSendProposalActivity.EXTRA_AUTHOR, model.author)
            })
        }
        footer.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun iconButton(icon: TaskDetailIcon, label: String, size: Int, action: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(TaskDetailIconView(this@MarketTaskDetailActivity, icon, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            setOnClickListener { action() }
        }.also { it.layoutParams = ViewGroup.LayoutParams(dp(size), dp(size)) }

    private fun makeChip(label: String, fill: Int, text: Int): TextView =
        makeText(label, 11f, true, text).apply {
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = rounded(fill, 99f)
        }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketTaskDetailActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketTaskDetailActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketTaskDetailActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun categoryColor(category: String): Int = when (category.lowercase()) {
        "design", "typing" -> R.color.market_peach
        "notes" -> R.color.market_lav
        "coding", "tutoring" -> R.color.market_mint
        else -> R.color.market_sky
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_ID = "market_task_id"
        const val EXTRA_CATEGORY = "market_task_category"
        const val EXTRA_COURSE = "market_task_course"
        const val EXTRA_TITLE = "market_task_title"
        const val EXTRA_DESCRIPTION = "market_task_description"
        const val EXTRA_BUDGET = "market_task_budget"
        const val EXTRA_DUE = "market_task_due"
        const val EXTRA_PROPOSALS = "market_task_proposals"
        const val EXTRA_AUTHOR = "market_task_author"
        const val EXTRA_INITIALS = "market_task_initials"
        const val EXTRA_AGE = "market_task_age"
        const val EXTRA_AVATAR_COLOR = "market_task_avatar_color"
    }
}
