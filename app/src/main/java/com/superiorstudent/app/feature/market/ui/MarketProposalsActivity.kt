package com.superiorstudent.app.feature.market.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

/** Reference screen 12: poster-side proposal list. Proposal actions are deliberately preview-only. */
class MarketProposalsActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var proposalList: LinearLayout
    private val filterChips = mutableMapOf<MarketProposalsFilter, TextView>()
    private var activeFilter = MarketProposalsFilter.ALL
    private var proposals = MarketProposalsUiPolicy.referenceProposals

    private val taskLabel by lazy {
        intent.getStringExtra(EXTRA_TASK_LABEL).orEmpty().ifBlank {
            MarketProposalsUiPolicy.TASK_LABEL
        }
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

        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })
        content.addView(buildFilterBar(), LinearLayout.LayoutParams(-1, dp(42)).apply {
            marginTop = dp(6)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })
        proposalList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        scroll.addView(proposalList, FrameLayout.LayoutParams(-1, -2))

        renderProposals()
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
            addView(MarketProposalsIconView(this@MarketProposalsActivity,
                MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Proposals", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText(
            MarketProposalsUiPolicy.capacityLabel() + " · " + taskLabel,
            12f, false, color(R.color.market_ink2)
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildFilterBar(): View {
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val filters = listOf(
            MarketProposalsFilter.ALL to "All ${MarketProposalsUiPolicy.TOTAL_PROPOSALS}",
            MarketProposalsFilter.SHORTLISTED to MarketProposalsUiPolicy.shortlistedLabel(proposals),
            MarketProposalsFilter.CHEAPEST to "Cheapest",
            MarketProposalsFilter.TOP_RATED to "Top rated"
        )
        filters.forEachIndexed { index, (filter, label) ->
            val chip = makeText(label, 12.5f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                minHeight = dp(38)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeFilter = filter
                    renderProposals()
                }
            }
            filterChips[filter] = chip
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(38)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        horizontal.addView(row, FrameLayout.LayoutParams(-2, -2))
        return horizontal
    }

    private fun renderProposals() {
        if (!::proposalList.isInitialized) return
        filterChips[MarketProposalsFilter.SHORTLISTED]?.text =
            MarketProposalsUiPolicy.shortlistedLabel(proposals)
        filterChips.forEach { (filter, chip) ->
            val selected = activeFilter == filter
            chip.background = if (selected) {
                rounded(color(R.color.market_segment), 99f)
            } else {
                rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            }
            chip.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink))
        }

        proposalList.removeAllViews()
        val shown = MarketProposalsUiPolicy.filterAndSort(proposals, activeFilter)
        if (shown.isEmpty()) {
            proposalList.addView(makeText("No proposals in this filter yet.", 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }
        shown.forEachIndexed { index, proposal ->
            proposalList.addView(buildProposalCard(proposal), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
    }

    private fun buildProposalCard(proposal: MarketProposalPreview): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = FrameLayout(this).apply {
            background = oval(color(avatarColor(proposal.avatarTone)))
            addView(makeText(proposal.initials, 14f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        top.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))

        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(makeText(proposal.name, 14f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ratingRow.addView(MarketProposalsIconView(this, MarketProposalsIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(14), dp(14)))
        ratingRow.addView(makeText(proposal.ratingLabel + " · " + proposal.ageLabel,
            12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        identity.addView(ratingRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })
        top.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(10)
            marginEnd = dp(8)
        })

        val offer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        offer.addView(makeText(MarketProposalsUiPolicy.priceLabel(proposal.pricePkr),
            14f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.END
        })
        if (proposal.shortlisted) {
            offer.addView(makeText("Shortlisted", 11f, true,
                color(if (AppThemePreferences.isNight(this@MarketProposalsActivity))
                    R.color.ok else R.color.ok)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = rounded(color(R.color.market_mint), 99f)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
        top.addView(offer, LinearLayout.LayoutParams(-2, -2))
        card.addView(top)

        card.addView(makeText(proposal.message, 12.5f, false, color(R.color.market_ink)).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(buildActionButton("Decline", ActionStyle.OUTLINE) {
            showActionInfo(proposal, "Decline proposal",
                "Declining is not connected in this preview. No notification will be sent and nothing will be saved.")
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        actions.addView(buildActionButton(if (proposal.shortlisted) "Shortlisted" else "Shortlist",
            ActionStyle.SECONDARY) {
            proposals = MarketProposalsUiPolicy.toggleShortlisted(proposals, proposal.id)
            renderProposals()
            toast("Shortlist updated in this preview only. Changes are not saved.")
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(8) })
        actions.addView(buildActionButton("Accept", ActionStyle.ACCEPT) {
            showActionInfo(proposal, "Accept proposal",
                "Acceptance is not connected in this preview. No student will be notified and your task will remain unchanged.")
        }, LinearLayout.LayoutParams(0, dp(38), 1.3f).apply { marginStart = dp(8) })
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return card
    }

    private enum class ActionStyle { OUTLINE, SECONDARY, ACCEPT }

    private fun buildActionButton(label: String, style: ActionStyle, action: () -> Unit): TextView =
        makeText(label, 12.5f, true, when (style) {
            ActionStyle.SECONDARY -> color(R.color.market_message_ink)
            ActionStyle.ACCEPT -> color(R.color.market_match_ink)
            ActionStyle.OUTLINE -> color(R.color.market_ink)
        }).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
            background = when (style) {
                ActionStyle.OUTLINE -> rounded(color(R.color.market_card), 12f, color(R.color.market_line))
                ActionStyle.SECONDARY -> rounded(color(R.color.market_message_bg), 12f)
                ActionStyle.ACCEPT -> rounded(color(R.color.market_match), 12f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun showActionInfo(proposal: MarketProposalPreview, title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(proposal.name + "\n\n" + message)
            .setPositiveButton("Got it", null)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun avatarColor(tone: String): Int = when (tone) {
        "peach" -> R.color.market_peach
        "lav" -> R.color.market_lav
        else -> R.color.market_sky
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketProposalsActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketProposalsActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketProposalsActivity,
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
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_TASK_LABEL = "market.proposals.task_label"
    }
}
