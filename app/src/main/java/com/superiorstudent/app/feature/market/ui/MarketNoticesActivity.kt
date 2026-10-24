package com.superiorstudent.app.feature.market.ui

import android.content.Intent

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
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
import com.superiorstudent.app.feature.market.data.MarketNotice
import com.superiorstudent.app.feature.market.data.MarketNoticeType
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 22: signed-in student's Market notice inbox. */
class MarketNoticesActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var tabActivity: TextView
    private lateinit var tabTeam: TextView
    private lateinit var noticeList: LinearLayout
    private val repository by lazy { MarketRepository() }
    private var notices: List<MarketNotice> = emptyList()
    private var loading = true
    private var loadError: String? = null
    private var selectedTab = MarketNoticesTab.ACTIVITY

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.market_bg)) }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = false; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(6), dp(16), dp(12))
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildTabs(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        noticeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(noticeList, LinearLayout.LayoutParams(-1, -2))
        page.addView(makeText("Open a notice to see its details and any available next steps.",
            12f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        renderTabs(); renderNotices(); applyWindowInsets(); loadNotices()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Back"
            addView(MarketNoticesIconView(this@MarketNoticesActivity, MarketNoticesGlyph.BACK,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(makeText(MarketNoticesUiPolicy.TITLE, 20f, true, color(R.color.market_ink)))
        heading.addView(makeText(MarketNoticesUiPolicy.SUBTITLE, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })
        row.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return row
    }

    private fun buildTabs(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }
        tabActivity = makeText(MarketNoticesTab.ACTIVITY.caption, 13.5f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(4), 0); isClickable = true; isFocusable = true
            setOnClickListener { selectedTab = MarketNoticesTab.ACTIVITY; renderTabs(); renderNotices() }
        }
        tabTeam = makeText(MarketNoticesTab.TEAM.caption, 13.5f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(4), 0); isClickable = true; isFocusable = true
            setOnClickListener { selectedTab = MarketNoticesTab.TEAM; renderTabs(); renderNotices() }
        }
        container.addView(tabActivity, LinearLayout.LayoutParams(0, dp(40), 1f))
        container.addView(tabTeam, LinearLayout.LayoutParams(0, dp(40), 1f))
        return container
    }

    private fun renderTabs() {
        tabActivity.text = "${MarketNoticesTab.ACTIVITY.caption} ${notices.count { isActivityNotice(it) && !it.read }}"
        tabTeam.text = "${MarketNoticesTab.TEAM.caption} ${notices.count { !isActivityNotice(it) && !it.read }}"
        styleTab(tabActivity, selectedTab == MarketNoticesTab.ACTIVITY)
        styleTab(tabTeam, selectedTab == MarketNoticesTab.TEAM)
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.background = if (selected) rounded(color(R.color.market_segment), 15f) else ColorDrawable(Color.TRANSPARENT)
        tab.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink2))
    }

    private fun renderNotices() {
        if (!::noticeList.isInitialized) return
        noticeList.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        val shown = notices.filter {
            if (selectedTab == MarketNoticesTab.ACTIVITY) isActivityNotice(it) else !isActivityNotice(it)
        }
        if (loading || loadError != null || shown.isEmpty()) {
            val message = loadError ?: if (loading) "Loading your notices…" else "No notices in this section yet."
            card.addView(makeText(message, 13f, false, color(R.color.market_ink2)).apply {
                setPadding(dp(16), dp(18), dp(16), dp(18))
            }, LinearLayout.LayoutParams(-1, -2))
            noticeList.addView(card, LinearLayout.LayoutParams(-1, -2))
            return
        }
        shown.forEachIndexed { index, notice ->
            card.addView(buildNoticeRow(notice), LinearLayout.LayoutParams(-1, -2))
            if (index < shown.lastIndex) card.addView(View(this).apply {
                setBackgroundColor(color(R.color.market_line))
            }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        noticeList.addView(card, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildNoticeRow(notice: MarketNotice): View {
        val timeLabel = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT).format(notice.createdAt.toDate())
        val glyph = when (notice.type) {
            MarketNoticeType.ANNOUNCEMENT -> MarketNoticesGlyph.SPEAKER
            MarketNoticeType.WARNING, MarketNoticeType.REMOVAL -> MarketNoticesGlyph.ALERT
            else -> MarketNoticesGlyph.MAIL
        }
        val iconBackground = if (notice.type == MarketNoticeType.REMOVAL ||
            notice.type == MarketNoticeType.WARNING) R.color.market_peach else R.color.market_sky
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            setPadding(dp(14), dp(13), dp(14), dp(13)); isClickable = true; isFocusable = true
            contentDescription = "${notice.title}. ${notice.body}. $timeLabel"
            setOnClickListener { openNotice(notice) }
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(iconBackground), 14f)
            addView(MarketNoticesIconView(this@MarketNoticesActivity, glyph, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        row.addView(iconTile, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        titleRow.addView(makeText(notice.title, 14f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f))
        if (!notice.read) titleRow.addView(View(this).apply {
            background = oval(color(R.color.market_menu_dot)); contentDescription = "Unread"
        }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginStart = dp(8); topMargin = dp(5) })
        textColumn.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        textColumn.addView(makeText(notice.body, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        textColumn.addView(makeText(timeLabel, 11.5f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun isActivityNotice(notice: MarketNotice): Boolean =
        notice.type !in setOf(MarketNoticeType.SUPPORT_REPLY, MarketNoticeType.ANNOUNCEMENT)

    private fun openNotice(notice: MarketNotice) {
        try {
            if (!notice.read) {
                repository.markNoticeRead(notice.id).addOnSuccessListener {
                    notices = notices.map { if (it.id == notice.id) it.copy(read = true) else it }
                    renderTabs(); renderNotices()
                }
            }
        } catch (_: Exception) { /* The detail remains available if its read marker fails. */ }
        startActivity(Intent(this, MarketNoticeDetailActivity::class.java).apply {
            putExtra(MarketNoticeDetailActivity.EXTRA_NOTICE_ID, notice.id)
            putExtra(MarketNoticeDetailActivity.EXTRA_TITLE, notice.title)
            putExtra(MarketNoticeDetailActivity.EXTRA_BODY, notice.body)
            putExtra(MarketNoticeDetailActivity.EXTRA_CREATED_AT, notice.createdAt.toDate().time)
            putExtra(MarketNoticeDetailActivity.EXTRA_CAN_APPEAL, notice.canAppeal && !notice.appealed)
        })
    }

    private fun loadNotices() {
        try {
            repository.loadMyNotices().addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                loading = false
                if (result.isSuccessful) {
                    notices = result.result.orEmpty()
                    loadError = null
                } else {
                    notices = emptyList()
                    loadError = result.exception?.localizedMessage ?: "Your notices could not be loaded."
                }
                renderTabs(); renderNotices()
            }
        } catch (error: Exception) {
            loading = false
            loadError = error.localizedMessage ?: "Your notices could not be loaded."
            renderTabs(); renderNotices()
        }
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size
            typeface = ResourcesCompat.getFont(this@MarketNoticesActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor); includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketNoticesActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketNoticesActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun oval(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
