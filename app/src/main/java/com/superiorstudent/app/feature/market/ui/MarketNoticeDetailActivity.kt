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
import com.superiorstudent.app.feature.market.ui.MarketNoticesActivity
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 23: detail opened from the signed-in user's notice inbox. */
class MarketNoticeDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private val noticeId get() = intent.getStringExtra(EXTRA_NOTICE_ID).orEmpty()
    private val noticeTitle get() = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Market notice" }
    private val noticeBody get() = intent.getStringExtra(EXTRA_BODY).orEmpty().ifBlank { "No notice details were provided." }
    private val canAppeal get() = intent.getBooleanExtra(EXTRA_CAN_APPEAL, false) && noticeId.isNotBlank()
    private val noticeDate get() = intent.getLongExtra(EXTRA_CREATED_AT, -1L).takeIf { it >= 0L }
        ?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "Date unavailable"

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
        titleColumn.addView(makeText(noticeTitle, 16f, true, color(R.color.market_ink)))
        titleColumn.addView(makeText(noticeDate, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        titleRow.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(titleRow)
        card.addView(makeText("Details", 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.addView(makeText(noticeBody, 13f, false, color(R.color.market_ink)).apply {
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
        val info = if (canAppeal) "You can submit one appeal for this decision." else
            "This notice is saved in your Market account. Contact support if you need help."
        banner.addView(makeText(info, 12f, false,
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
        appeal.visibility = if (canAppeal) View.VISIBLE else View.GONE
        appeal.setOnClickListener {
            startActivity(android.content.Intent(this, MarketAppealActivity::class.java).apply {
                putExtra(MarketAppealActivity.EXTRA_NOTICE_ID, noticeId)
                putExtra(MarketAppealActivity.EXTRA_NOTICE_TITLE, noticeTitle)
                putExtra(MarketAppealActivity.EXTRA_NOTICE_BODY, noticeBody)
                putExtra(MarketAppealActivity.EXTRA_CAN_APPEAL, canAppeal)
            })
        }
        footer.addView(appeal, LinearLayout.LayoutParams(-1, dp(52)))
        val rules = actionButton(MarketNoticeDetailUiPolicy.RULES_BUTTON,
            color(R.color.market_card), color(R.color.market_ink), MarketNoticeDetailGlyph.HELP).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        rules.setOnClickListener {
            startActivity(android.content.Intent(this, MarketRulesActivity::class.java))
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

    companion object {
        const val EXTRA_NOTICE_ID = "market.notice.id"
        const val EXTRA_TITLE = "market.notice.title"
        const val EXTRA_BODY = "market.notice.body"
        const val EXTRA_CREATED_AT = "market.notice.created_at"
        const val EXTRA_CAN_APPEAL = "market.notice.can_appeal"
    }
}
