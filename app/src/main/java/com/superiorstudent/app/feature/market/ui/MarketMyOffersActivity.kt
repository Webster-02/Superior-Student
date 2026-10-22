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

/** Screen 19: My offers. The listed offers and controls are local fixtures for UI review only. */
class MarketMyOffersActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var offerList: LinearLayout
    private lateinit var activeTab: TextView
    private lateinit var pausedTab: TextView
    private lateinit var expiredTab: TextView
    private var selectedStatus = MarketMyOfferStatus.ACTIVE
    private val offers = MarketMyOffersUiPolicy.referenceOffers.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        selectedStatus = savedInstanceState?.getString(STATE_STATUS)
            ?.let { runCatching { MarketMyOfferStatus.valueOf(it) }.getOrNull() }
            ?: MarketMyOfferStatus.ACTIVE

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
        content.addView(buildTabs(), LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(6)
            marginStart = dp(16)
            marginEnd = dp(16)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })
        offerList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(100))
        }
        scroll.addView(offerList, FrameLayout.LayoutParams(-1, -2))

        root.addView(buildPostButton(), FrameLayout.LayoutParams(-2, dp(52),
            Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(16)
            bottomMargin = dp(22)
        })

        renderOffers()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STATUS, selectedStatus.name)
        super.onSaveInstanceState(outState)
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(iconButton(MarketMyOffersIcon.BACK, "Back", 44) { finish() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("My offers", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText("Manage what you offer", 13f, false,
            color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildTabs(): View {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }
        activeTab = buildTab(MarketMyOfferStatus.ACTIVE)
        pausedTab = buildTab(MarketMyOfferStatus.PAUSED)
        expiredTab = buildTab(MarketMyOfferStatus.EXPIRED)
        holder.addView(activeTab, LinearLayout.LayoutParams(0, -1, 1f))
        holder.addView(pausedTab, LinearLayout.LayoutParams(0, -1, 1f))
        holder.addView(expiredTab, LinearLayout.LayoutParams(0, -1, 1f))
        return holder
    }

    private fun buildTab(status: MarketMyOfferStatus): TextView =
        makeText("", 13f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedStatus = status
                renderOffers()
            }
        }

    private fun updateTabs() {
        listOf(
            Triple(MarketMyOfferStatus.ACTIVE, activeTab, "Active"),
            Triple(MarketMyOfferStatus.PAUSED, pausedTab, "Paused"),
            Triple(MarketMyOfferStatus.EXPIRED, expiredTab, "Expired")
        ).forEach { (status, view, label) ->
            view.text = "$label ${MarketMyOffersUiPolicy.count(offers, status)}"
            val selected = selectedStatus == status
            view.background = rounded(
                color(if (selected) R.color.market_segment else android.R.color.transparent),
                15f
            )
            view.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink2))
        }
    }

    private fun renderOffers() {
        if (!::offerList.isInitialized) return
        updateTabs()
        offerList.removeAllViews()
        val items = MarketMyOffersUiPolicy.visibleItems(offers, selectedStatus)
        items.forEachIndexed { index, item ->
            offerList.addView(buildOfferCard(item), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
        if (items.isEmpty()) {
            offerList.addView(makeText(
                "No ${selectedStatus.name.lowercase()} offers right now.",
                13f, false, color(R.color.market_ink2)
            ).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(28), dp(20), dp(28)) },
                LinearLayout.LayoutParams(-1, -2))
        }
        offerList.addView(makeText(MarketMyOffersUiPolicy.RENEWAL_RULE,
            12f, false, color(R.color.market_ink)).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(2).toFloat(), 1f)
            background = rounded(color(R.color.market_sky), 18f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun buildOfferCard(item: MarketMyOfferPreview): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
        }
        val metadata = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        metadata.addView(makePill(item.category,
            color(categoryColor(MarketMyOffersUiPolicy.categoryTone(item.category))),
            color(R.color.market_ink)), LinearLayout.LayoutParams(-2, -2))
        metadata.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        metadata.addView(makeText(item.priceLabel, 14f, true, color(R.color.market_ink)))
        card.addView(metadata, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(6)
        })
        card.addView(makeText(item.title, 14.5f, true, color(R.color.market_ink)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(makeText(item.detailLine, 12f, false, color(R.color.market_ink2)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val primaryAction = when (item.status) {
            MarketMyOfferStatus.ACTIVE -> "Pause"
            MarketMyOfferStatus.PAUSED -> "Resume"
            MarketMyOfferStatus.EXPIRED -> "Pause"
        }
        addAction(actions, MarketMyOffersIcon.EDIT, "Edit", enabled = true) { editOffer(item) }
        addAction(actions,
            if (primaryAction == "Resume") MarketMyOffersIcon.RESUME else MarketMyOffersIcon.PAUSE,
            primaryAction, enabled = item.status != MarketMyOfferStatus.EXPIRED) {
            togglePause(item)
        }
        addAction(actions, MarketMyOffersIcon.RENEW, "Renew",
            enabled = item.canRenew && item.status != MarketMyOfferStatus.PAUSED) { renewOffer(item) }
        addAction(actions, MarketMyOffersIcon.DELETE, "Delete", enabled = true) { confirmDelete(item) }
        card.addView(actions, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(10) })
        return card
    }

    private fun addAction(
        row: LinearLayout,
        icon: MarketMyOffersIcon,
        label: String,
        enabled: Boolean,
        action: () -> Unit
    ) {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), 0, dp(3), 0)
            background = rounded(color(R.color.market_card), 12f, color(R.color.market_line))
            isClickable = enabled
            isFocusable = enabled
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.42f
            contentDescription = label
            addView(MarketMyOffersIconView(this@MarketMyOffersActivity, icon,
                color(if (enabled) R.color.market_ink else R.color.market_ink3)),
                LinearLayout.LayoutParams(dp(15), dp(15)))
            addView(makeText(label, 11f, true,
                color(if (enabled) R.color.market_ink else R.color.market_ink3)),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            setOnClickListener { if (enabled) action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
            if (row.childCount > 0) marginStart = dp(6)
        })
    }

    private fun togglePause(item: MarketMyOfferPreview) {
        if (item.status == MarketMyOfferStatus.EXPIRED) return
        val updated = MarketMyOffersUiPolicy.pauseOrResume(item)
        replaceOffer(updated)
        renderOffers()
        toast(if (updated.status == MarketMyOfferStatus.PAUSED)
            "Paused in this preview only." else "Resumed in this preview only.")
    }

    private fun renewOffer(item: MarketMyOfferPreview) {
        val updated = MarketMyOffersUiPolicy.renew(item)
        if (updated == null) {
            toast("Renewal is not available yet.")
            return
        }
        replaceOffer(updated)
        renderOffers()
        toast("Renewed in this preview only. Nothing was published.")
    }

    private fun confirmDelete(item: MarketMyOfferPreview) {
        AlertDialog.Builder(this)
            .setTitle("Delete offer?")
            .setMessage("Remove \"${item.title}\" from this local preview? No live offer will be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                offers.removeAll { it.id == item.id }
                renderOffers()
                toast("Removed from this preview only.")
            }
            .show()
    }

    private fun editOffer(item: MarketMyOfferPreview) {
        AlertDialog.Builder(this)
            .setTitle("Edit offer")
            .setMessage("Open an editable preview of this offer? Changes remain local and will not be saved.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open editor") { _, _ ->
                startActivity(Intent(this, MarketPostOfferActivity::class.java).apply {
                    putExtra(MarketPostOfferActivity.EXTRA_EDIT_MODE, true)
                    putExtra(MarketPostOfferActivity.EXTRA_TITLE, item.title)
                    putExtra(MarketPostOfferActivity.EXTRA_CATEGORY, item.category)
                    putExtra(MarketPostOfferActivity.EXTRA_PRICE_MODE, item.priceMode.name)
                    putExtra(MarketPostOfferActivity.EXTRA_AMOUNT, item.amount)
                    putExtra(MarketPostOfferActivity.EXTRA_DESCRIPTION, item.description)
                })
            }
            .show()
    }

    private fun replaceOffer(updated: MarketMyOfferPreview) {
        val index = offers.indexOfFirst { it.id == updated.id }
        if (index >= 0) offers[index] = updated
    }

    private fun buildPostButton(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(20), 0, dp(20), 0)
        background = rounded(color(R.color.market_match), 99f)
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "Post an offer"
        addView(MarketMyOffersIconView(this@MarketMyOffersActivity,
            MarketMyOffersIcon.PLUS, color(R.color.market_match_ink)),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        addView(makeText("Post an offer", 14f, true, color(R.color.market_match_ink)))
        setOnClickListener { startActivity(Intent(this@MarketMyOffersActivity,
            MarketPostOfferActivity::class.java)) }
    }

    private fun iconButton(
        icon: MarketMyOffersIcon,
        label: String,
        size: Int,
        action: () -> Unit
    ): View = FrameLayout(this).apply {
        background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        isClickable = true
        isFocusable = true
        contentDescription = label
        addView(MarketMyOffersIconView(this@MarketMyOffersActivity, icon,
            color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnClickListener { action() }
    }

    private fun makePill(label: String, fill: Int, foreground: Int): TextView =
        makeText(label, 11f, true, foreground).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(fill, 99f)
        }

    private fun categoryColor(tone: MarketMyOfferCategoryTone): Int = when (tone) {
        MarketMyOfferCategoryTone.LAVENDER -> R.color.market_lav
        MarketMyOfferCategoryTone.PEACH -> R.color.market_peach
        MarketMyOfferCategoryTone.SKY -> R.color.market_sky
        MarketMyOfferCategoryTone.MINT -> R.color.market_mint
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketMyOffersActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketMyOffersActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeLabel(value: String): TextView = makeText(value, 13f, true, color(R.color.market_ink))

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketMyOffersActivity,
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

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val STATE_STATUS = "market.myoffers.selected_status"
    }
}
