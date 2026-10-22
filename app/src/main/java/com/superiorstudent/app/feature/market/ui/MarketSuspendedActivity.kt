package com.superiorstudent.app.feature.market.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

/**
 * Screen 25 reference page for a paused-posting state.
 * It does not read or enforce an actual account restriction.
 */
class MarketSuspendedActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.market_bg)) }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        main.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(8)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildSuspensionCard(), LinearLayout.LayoutParams(-1, -2))
        page.addView(makeText(MarketSuspendedUiPolicy.ALLOWED_TITLE, 13f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(7) })
        MarketSuspendedUiPolicy.allowedItems.forEach { label ->
            page.addView(buildRuleRow(label, true), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        page.addView(makeText(MarketSuspendedUiPolicy.DENIED_TITLE, 13f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(7) })
        page.addView(buildRuleRow(MarketSuspendedUiPolicy.DENIED_ACTIONS, false),
            LinearLayout.LayoutParams(-1, -2))

        main.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
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
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Back"
            addView(MarketSuspendedIconView(this@MarketSuspendedActivity, MarketSuspendedGlyph.BACK,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(makeText(MarketSuspendedUiPolicy.TITLE, 20f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildSuspensionCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(R.color.market_peach), 22f)
            addView(MarketSuspendedIconView(this@MarketSuspendedActivity, MarketSuspendedGlyph.WARNING,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        }
        card.addView(iconTile, LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12)
        })
        card.addView(makeText(MarketSuspendedUiPolicy.STATUS_TITLE, 20f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(makeText(MarketSuspendedUiPolicy.UNTIL_LABEL, 12f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(14) })

        val reason = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_bg), 16f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        reason.addView(makeText(MarketSuspendedUiPolicy.REASON_LABEL, 13f, true, color(R.color.market_ink)))
        reason.addView(makeText(MarketSuspendedUiPolicy.REASON, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.addView(reason, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun buildRuleRow(label: String, allowed: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(2), dp(4), dp(2), dp(4))
            contentDescription = label
        }
        row.addView(MarketSuspendedIconView(this,
            if (allowed) MarketSuspendedGlyph.CHECK else MarketSuspendedGlyph.CROSS,
            color(if (allowed) R.color.ok else R.color.market_bad)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10); topMargin = dp(1) })
        row.addView(makeText(label, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            background = colorDrawable(R.color.market_bg)
        }
        footer.addView(buildAction(MarketSuspendedUiPolicy.RULES_BUTTON, false),
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
        footer.addView(buildAction(MarketSuspendedUiPolicy.APPEAL_BUTTON, true),
            LinearLayout.LayoutParams(0, dp(52), 1f))
        return footer
    }

    private fun buildAction(label: String, primary: Boolean): View {
        val foreground = color(if (primary) R.color.market_match_ink else R.color.market_ink)
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = if (primary) rounded(color(R.color.market_match), 16f)
                else rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = label
        }
        val glyph = if (primary) MarketSuspendedGlyph.FLAG else MarketSuspendedGlyph.RULES
        button.addView(MarketSuspendedIconView(this, glyph, foreground),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        button.addView(makeText(label, 15f, true, foreground))
        button.setOnClickListener {
            if (primary) {
                startActivity(Intent(this, MarketAppealActivity::class.java))
            } else {
                AlertDialog.Builder(this)
                    .setTitle(MarketSuspendedUiPolicy.RULES_BUTTON)
                    .setMessage(MarketSuspendedUiPolicy.RULES_PREVIEW + "\n\n" +
                        MarketSuspendedUiPolicy.PREVIEW_DISCLOSURE)
                    .setPositiveButton("Got it", null)
                    .show()
            }
        }
        return button
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size
            typeface = ResourcesCompat.getFont(this@MarketSuspendedActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor); includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketSuspendedActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketSuspendedActivity)
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
    private fun colorDrawable(id: Int) = android.graphics.drawable.ColorDrawable(color(id))
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
