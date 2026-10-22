package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

/** Screen 06: task detail in the poster/owner view from the supplied reference ZIP. */
class MarketTaskPosterDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout

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
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(8)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        scroll.addView(details, FrameLayout.LayoutParams(-1, -2))

        details.addView(buildCategoryTags())
        details.addView(makeText(
            MarketTaskPosterUiPolicy.reference.title, 22f, true, color(R.color.market_ink)
        ).apply {
            letterSpacing = -0.02f
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        details.addView(buildAuthorRow(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
            bottomMargin = dp(18)
        })
        details.addView(buildMetricCards(), LinearLayout.LayoutParams(-1, -2))

        details.addView(makeText("Details", 16f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        details.addView(makeText(
            MarketTaskPosterUiPolicy.reference.details, 13f, false, color(R.color.market_ink2)
        ).apply { setLineSpacing(dp(4).toFloat(), 1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        details.addView(buildSkillTags(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        details.addView(buildProposalsSummary(), LinearLayout.LayoutParams(-1, dp(84)).apply {
            topMargin = dp(28)
        })

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
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
            background = rounded(color(R.color.market_card), 18f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            addView(TaskDetailIconView(this@MarketTaskPosterDetailActivity, TaskDetailIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        row.addView(makeText("Your task", 22f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })

        val status = makeText("Open", 13f, true, statusTextColor()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(statusBackgroundColor(), 99f)
        }
        row.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(14) })

        val edit = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 18f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Edit task"
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, PosterDetailIcon.EDIT, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { toast("Edit task is part of the upcoming posting/edit flow.") }
        }
        row.addView(edit, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildCategoryTags(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeChip("Design", color(R.color.market_peach), color(R.color.market_ink)))
        row.addView(makeText("BSOPM", 13f, true, color(R.color.market_ink)).apply {
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        return row
    }

    private fun buildAuthorRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeText("HA", 16f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = oval(color(R.color.market_sky))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(makeText("Hamza Ali", 15f, true, color(R.color.market_ink)))
        val rating = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rating.addView(TaskDetailIconView(this, TaskDetailIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(16), dp(16)))
        rating.addView(makeText("4.8 · Verified student", 13f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        info.addView(rating, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        row.addView(makeText("2h ago", 13f, false, color(R.color.market_ink2)))
        return row
    }

    private fun buildMetricCards(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val metrics = listOf(
            Triple("Budget", "Rs. 1,500\nfixed", R.color.market_lav),
            Triple("Deadline", "24 Oct\n3 days left", R.color.market_peach),
            Triple("Proposals", "4 of 15", R.color.market_sky)
        )
        metrics.forEachIndexed { index, metric ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(8), dp(12))
                background = rounded(color(metric.third), 22f)
                addView(makeText(metric.first, 12f, false, color(R.color.market_ink2)))
                addView(makeText(metric.second, 15f, true, color(R.color.market_ink)).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            val params = LinearLayout.LayoutParams(0, dp(104), 1f)
            if (index > 0) params.marginStart = dp(9)
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
        listOf("Canva", "Photoshop", "Layout").forEachIndexed { index, tag ->
            val label = makeText(tag, 13f, true, color(R.color.market_ink)).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
            val params = LinearLayout.LayoutParams(-2, -2)
            if (index > 0) params.marginStart = dp(8)
            row.addView(label, params)
        }
        scroll.addView(row, FrameLayout.LayoutParams(-2, -2))
        return scroll
    }

    private fun buildProposalsSummary(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 28f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { openProposals() }
        }
        val avatars = FrameLayout(this)
        val avatarSpecs = listOf(
            Triple("AK", R.color.market_lav, 0),
            Triple("ZM", R.color.market_sky, 24),
            Triple("NF", R.color.market_peach, 48)
        )
        avatarSpecs.forEach { (initials, resource, left) ->
            val avatar = makeText(initials, 12f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                background = oval(color(resource))
            }
            avatars.addView(avatar, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(left)
            })
        }
        card.addView(avatars, LinearLayout.LayoutParams(dp(96), dp(48)))

        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(makeText("4 proposals", 17f, true, color(R.color.market_ink)))
        text.addView(makeText("1 shortlisted", 13f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        card.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        card.addView(PosterDetailIconView(this, PosterDetailIcon.CHEVRON, color(R.color.market_ink3)),
            LinearLayout.LayoutParams(dp(20), dp(20)))
        return card
    }

    private fun openProposals() {
        startActivity(Intent(this, MarketProposalsActivity::class.java).apply {
            putExtra(MarketProposalsActivity.EXTRA_TASK_LABEL, MarketProposalsUiPolicy.TASK_LABEL)
        })
    }

    private fun buildFooter(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val cancel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(color(R.color.market_card), 28f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, PosterDetailIcon.CLOSE, color(R.color.market_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Cancel task", 15f, true, color(R.color.market_ink)))
        }
        cancel.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cancel task?")
                .setMessage("This preview does not change your task status yet.")
                .setNegativeButton("Keep open", null)
                .setPositiveButton("Continue") { _, _ ->
                    toast("Task cancellation is not connected in this visual step.")
                }
                .show()
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, dp(60), 0.9f))

        val viewProposals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(color(R.color.market_match), 28f)
            isClickable = true
            isFocusable = true
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, PosterDetailIcon.USERS, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
            addView(makeText("View proposals", 15f, true, color(R.color.market_match_ink)))
        }
        viewProposals.setOnClickListener { openProposals() }
        row.addView(viewProposals, LinearLayout.LayoutParams(0, dp(60), 1.35f).apply {
            marginStart = dp(10)
        })
        return row
    }

    private fun makeChip(label: String, fill: Int, text: Int): TextView =
        makeText(label, 13f, true, text).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = rounded(fill, 99f)
        }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketTaskPosterDetailActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketTaskPosterDetailActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketTaskPosterDetailActivity)
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

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun statusBackgroundColor(): Int =
        if (AppThemePreferences.isNight(this)) 0xFF3B301D.toInt() else 0xFFFFEFC9.toInt()
    private fun statusTextColor(): Int =
        if (AppThemePreferences.isNight(this)) 0xFFFFC857.toInt() else 0xFF8B5A00.toInt()
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
