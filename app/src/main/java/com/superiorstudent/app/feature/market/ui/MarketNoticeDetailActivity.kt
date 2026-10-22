package com.superiorstudent.app.feature.market.ui

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

/** Screen 23: detail for the sample offer-removal notice. Appeal and live rules routes are separate steps. */
class MarketNoticeDetailActivity : AppCompatActivity() {
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
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, dp(150))
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildNoticeCard(), LinearLayout.LayoutParams(-1, -2))
        page.addView(buildInfoBanner(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })

        root.addView(buildFooter(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
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
            addView(MarketNoticeDetailIconView(this@MarketNoticeDetailActivity, MarketNoticeDetailGlyph.BACK,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(makeText(MarketNoticeDetailUiPolicy.TITLE, 20f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildNoticeCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(R.color.market_peach), 16f)
            addView(MarketNoticeDetailIconView(this@MarketNoticeDetailActivity,
                MarketNoticeDetailGlyph.ALERT, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        titleRow.addView(iconTile, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeText(MarketNoticeDetailUiPolicy.NOTICE_TITLE, 16f, true, color(R.color.market_ink)))
        titleColumn.addView(makeText(MarketNoticeDetailUiPolicy.TIMESTAMP, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        titleRow.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(titleRow)

        val offerSummary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_bg), 16f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        offerSummary.addView(makeText(MarketNoticeDetailUiPolicy.OFFER_TITLE, 14f, true, color(R.color.market_ink)))
        offerSummary.addView(makeText(MarketNoticeDetailUiPolicy.OFFER_META, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        card.addView(offerSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        card.addView(makeText(MarketNoticeDetailUiPolicy.WHY_LABEL, 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.addView(makeText(MarketNoticeDetailUiPolicy.WHY_TEXT, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        card.addView(makeText(MarketNoticeDetailUiPolicy.WHAT_HAPPENED_LABEL, 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        card.addView(makeText(MarketNoticeDetailUiPolicy.WHAT_HAPPENED_TEXT, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return card
    }

    private fun buildInfoBanner(): View {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(color(R.color.market_sky), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        banner.addView(MarketNoticeDetailIconView(this, MarketNoticeDetailGlyph.INFO,
            color(R.color.market_ink)), LinearLayout.LayoutParams(dp(20), dp(20)).apply {
            marginEnd = dp(12); topMargin = dp(1)
        })
        banner.addView(makeText(MarketNoticeDetailUiPolicy.INFO_TEXT, 12f, false,
            color(R.color.market_ink)).apply { setLineSpacing(dp(2).toFloat(), 1f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        return banner
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val appeal = actionButton(MarketNoticeDetailUiPolicy.APPEAL_BUTTON,
            color(R.color.market_match), color(R.color.market_match_ink), MarketNoticeDetailGlyph.FLAG)
        appeal.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(MarketNoticeDetailUiPolicy.APPEAL_BUTTON)
                .setMessage(MarketNoticeDetailUiPolicy.APPEAL_PREVIEW_MESSAGE)
                .setPositiveButton("Got it", null)
                .show()
        }
        footer.addView(appeal, LinearLayout.LayoutParams(-1, dp(52)))
        val rules = actionButton(MarketNoticeDetailUiPolicy.RULES_BUTTON,
            color(R.color.market_card), color(R.color.market_ink), MarketNoticeDetailGlyph.HELP).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        rules.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(MarketNoticeDetailUiPolicy.RULES_BUTTON)
                .setMessage(MarketNoticeDetailUiPolicy.RULES_PREVIEW_MESSAGE)
                .setPositiveButton("Got it", null)
                .show()
        }
        footer.addView(rules, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        return footer
    }

    private fun actionButton(label: String, fill: Int, foreground: Int, glyph: MarketNoticeDetailGlyph): View {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(fill, 16f)
            isClickable = true; isFocusable = true; contentDescription = label
        }
        button.addView(MarketNoticeDetailIconView(this, glyph, foreground),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        button.addView(makeText(label, 15f, true, foreground))
        return button
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size
            typeface = ResourcesCompat.getFont(this@MarketNoticeDetailActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor); includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketNoticeDetailActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketNoticeDetailActivity)
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
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
