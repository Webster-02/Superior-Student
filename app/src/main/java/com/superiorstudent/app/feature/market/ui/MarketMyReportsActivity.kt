package com.superiorstudent.app.feature.market.ui

import android.graphics.Color
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

/** Screen 30. Reference rows are examples, not account report data. */
class MarketMyReportsActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout

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
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(24))
            clipToPadding = false
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildReportList(), LinearLayout.LayoutParams(-1, -2))
        page.addView(buildPrivacyNote(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
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
            addView(MarketMyReportsIconView(this@MarketMyReportsActivity,
                MarketMyReportsGlyph.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeText(MarketMyReportsUiPolicy.TITLE, 20f, true,
            color(R.color.market_ink)))
        titleColumn.addView(makeText(MarketMyReportsUiPolicy.SUBTITLE, 12f, false,
            color(R.color.market_ink2)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(1)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildReportList(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        MarketMyReportsUiPolicy.reports.forEachIndexed { index, report ->
            list.addView(buildReportRow(report), LinearLayout.LayoutParams(-1, -2))
            if (index < MarketMyReportsUiPolicy.reports.lastIndex) {
                list.addView(View(this).apply {
                    setBackgroundColor(color(R.color.market_line))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        return list
    }

    private fun buildReportRow(report: MarketMyReport): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            contentDescription = report.title + ", " + report.context + ", " + report.status.label
            setOnClickListener {
                AlertDialog.Builder(this@MarketMyReportsActivity)
                    .setTitle(MarketMyReportsUiPolicy.SAMPLE_ROW_NOTICE_TITLE)
                    .setMessage(MarketMyReportsUiPolicy.SAMPLE_ROW_NOTICE)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        val icon = FrameLayout(this).apply {
            background = rounded(color(report.iconBackgroundRes), 14f)
            addView(MarketMyReportsIconView(this@MarketMyReportsActivity,
                MarketMyReportsGlyph.FLAG, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            marginEnd = dp(12)
        })
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(makeText(report.title, 13.5f, true, color(R.color.market_ink)))
        textColumn.addView(makeText(report.context, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(8)
        })
        val badge = makeText(report.status.label, 11.5f, true,
            color(report.status.textRes)).apply {
            gravity = Gravity.CENTER
            background = rounded(color(report.status.backgroundRes), 99f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            maxLines = 1
            contentDescription = "Status: " + report.status.label
        }
        row.addView(badge, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun buildPrivacyNote(): View {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(color(R.color.market_sky), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        note.addView(MarketMyReportsIconView(this, MarketMyReportsGlyph.INFO,
            color(R.color.market_ink)), LinearLayout.LayoutParams(dp(20), dp(22)).apply {
            marginEnd = dp(12)
        })
        note.addView(makeText(MarketMyReportsUiPolicy.PRIVACY_NOTE, 12f, false,
            color(R.color.market_ink)).apply {
            gravity = Gravity.TOP
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return note
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
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketMyReportsActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketMyReportsActivity)
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
