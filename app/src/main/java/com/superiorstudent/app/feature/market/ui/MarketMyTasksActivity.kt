package com.superiorstudent.app.feature.market.ui

import android.content.Intent
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 15: reference My tasks list. All entries are visual fixtures until data wiring is approved. */
class MarketMyTasksActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var taskList: LinearLayout
    private lateinit var postedTab: TextView
    private lateinit var appliedTab: TextView
    private var activeTab = MarketMyTasksTab.APPLIED

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        activeTab = savedInstanceState?.getString(STATE_TAB)
            ?.let { runCatching { MarketMyTasksTab.valueOf(it) }.getOrNull() }
            ?: MarketMyTasksTab.APPLIED

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })
        content.addView(buildTabs(), LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(6)
            marginStart = dp(16)
            marginEnd = dp(16)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })
        taskList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        scroll.addView(taskList, FrameLayout.LayoutParams(-1, -2))

        renderTasks()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, activeTab.name)
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
            addView(MarketProposalsIconView(this@MarketMyTasksActivity,
                MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("My tasks", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText("Posted and applied", 13f, false,
            color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildTabs(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }

        postedTab = buildTab(MarketMyTasksUiPolicy.tabLabel(MarketMyTasksTab.POSTED),
            MarketMyTasksTab.POSTED)
        appliedTab = buildTab(MarketMyTasksUiPolicy.tabLabel(MarketMyTasksTab.APPLIED),
            MarketMyTasksTab.APPLIED)
        outer.addView(postedTab, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(appliedTab, LinearLayout.LayoutParams(0, -1, 1f))
        return outer
    }

    private fun buildTab(label: String, tab: MarketMyTasksTab): TextView =
        makeText(label, 13f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            isFocusable = true
            setOnClickListener {
                activeTab = tab
                renderTasks()
            }
        }

    private fun renderTasks() {
        if (!::taskList.isInitialized) return
        updateTabs()
        taskList.removeAllViews()
        val items = MarketMyTasksUiPolicy.items(activeTab)
        items.forEachIndexed { index, item ->
            taskList.addView(buildTaskCard(item), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
    }

    private fun updateTabs() {
        listOf(
            Triple(MarketMyTasksTab.POSTED, postedTab, MarketMyTasksUiPolicy.tabLabel(MarketMyTasksTab.POSTED)),
            Triple(MarketMyTasksTab.APPLIED, appliedTab, MarketMyTasksUiPolicy.tabLabel(MarketMyTasksTab.APPLIED))
        ).forEach { (tab, view, label) ->
            view.text = label
            val selected = activeTab == tab
            view.background = if (selected) {
                rounded(color(R.color.market_segment), 15f)
            } else {
                GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) }
            }
            view.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink2))
        }
    }

    private fun buildTaskCard(item: MarketMyTaskItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTaskSelected(item) }
            contentDescription = item.category + ", " + item.title + ", " + item.status
        }

        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pills.addView(makePill(item.category, categoryBackground(item.categoryTone),
            color(R.color.market_ink)), LinearLayout.LayoutParams(-2, -2))
        val status = makePill(item.status, statusBackground(item.statusTone),
            statusForeground(item.statusTone))
        pills.addView(status, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_VERTICAL
            weight = 0f
        })
        val spacer = View(this)
        pills.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        // Keep status anchored to the card's right edge, like the reference.
        pills.removeView(status)
        pills.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        pills.addView(status, LinearLayout.LayoutParams(-2, -2))
        card.addView(pills)

        card.addView(makeText(item.title, 14f, true, color(R.color.market_ink)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(1).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        card.addView(makeText(item.detailLine, 12.5f, false,
            color(R.color.market_ink2)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return card
    }

    private fun makePill(label: String, fill: Int, foreground: Int): TextView =
        makeText(label, 11f, true, foreground).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(fill, 99f)
        }

    private fun categoryBackground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH -> R.color.market_peach
        MarketMyTaskTone.MINT -> R.color.market_mint
        MarketMyTaskTone.LAVENDER -> R.color.market_lav
        MarketMyTaskTone.SKY -> R.color.market_sky
        MarketMyTaskTone.LIME -> R.color.market_match
        MarketMyTaskTone.SUCCESS -> R.color.market_mint
        MarketMyTaskTone.WARNING -> R.color.warn_bg
    })

    private fun statusBackground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH -> R.color.market_peach
        MarketMyTaskTone.MINT -> R.color.market_mint
        MarketMyTaskTone.LAVENDER -> R.color.market_lav
        MarketMyTaskTone.SKY -> R.color.market_sky
        MarketMyTaskTone.LIME -> R.color.market_match
        MarketMyTaskTone.SUCCESS -> R.color.market_mint
        MarketMyTaskTone.WARNING -> R.color.warn_bg
    })

    private fun statusForeground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH,
        MarketMyTaskTone.MINT,
        MarketMyTaskTone.LAVENDER,
        MarketMyTaskTone.SKY -> R.color.market_ink
        MarketMyTaskTone.LIME -> R.color.market_match_ink
        MarketMyTaskTone.SUCCESS -> R.color.ok
        MarketMyTaskTone.WARNING -> R.color.warn
    })

    private fun onTaskSelected(item: MarketMyTaskItem) {
        when (item.action) {
            MarketMyTaskAction.OPEN_POSTED_DETAIL ->
                startActivity(Intent(this, MarketTaskPosterDetailActivity::class.java))
            MarketMyTaskAction.OPEN_CHAT_PREVIEW -> AlertDialog.Builder(this)
                .setTitle("Sample chat preview")
                .setMessage("This opens a local preview of the accepted task conversation. No messages are sent and no task state is changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open chat preview") { _, _ ->
                    startActivity(Intent(this, MarketTaskChatActivity::class.java).apply {
                        putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, item.helperName)
                        putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, item.title)
                        putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR, item.pricePkr)
                    })
                }
                .show()
            MarketMyTaskAction.PREVIEW_INFO -> AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.status + "\n\n" + item.detailLine +
                    "\n\nThis task uses local reference data. Live task and proposal actions are not connected.")
                .setPositiveButton("Got it", null)
                .show()
        }
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketMyTasksActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketMyTasksActivity)
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
            typeface = ResourcesCompat.getFont(this@MarketMyTasksActivity,
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STATE_TAB = "market.my_tasks.active_tab"
    }
}
