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
import com.superiorstudent.app.feature.market.data.MarketErrorMapper
import com.superiorstudent.app.feature.market.data.MarketProfile
import com.superiorstudent.app.feature.market.data.MarketProfileStatus
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences
import java.text.SimpleDateFormat
import java.util.Locale

/** Screen 25 reads the signed-in student's real Market account status. */
class MarketSuspendedActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var statusTitleText: TextView
    private lateinit var untilText: TextView
    private lateinit var reasonText: TextView
    private lateinit var deniedHeader: TextView
    private lateinit var deniedRow: View

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
        deniedHeader = makeText(MarketSuspendedUiPolicy.DENIED_TITLE, 13f, true, color(R.color.market_ink))
        page.addView(deniedHeader,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(7) })
        deniedRow = buildRuleRow(MarketSuspendedUiPolicy.DENIED_ACTIONS, false)
        page.addView(deniedRow, LinearLayout.LayoutParams(-1, -2))

        main.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        applyWindowInsets()
        loadAccountStatus()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun loadAccountStatus() {
        MarketRepository().loadCurrentMarketProfile()
            .addOnSuccessListener { profile ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                renderAccountStatus(profile)
            }
            .addOnFailureListener { failure ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                statusTitleText.text = "Account status unavailable"
                untilText.text = "Market could not verify your current posting status."
                reasonText.text = MarketErrorMapper.map(failure).message
                // Fail closed in the presentation; Firestore Rules remain the actual protection.
                deniedHeader.visibility = View.VISIBLE
                deniedRow.visibility = View.VISIBLE
            }
    }

    private fun renderAccountStatus(profile: MarketProfile?) {
        if (profile == null) {
            statusTitleText.text = "Market profile not found"
            untilText.text = "No Market profile is linked to this account yet."
            reasonText.text = "A profile is created when your verified student account first uses Market."
            deniedHeader.visibility = View.VISIBLE
            deniedRow.visibility = View.VISIBLE
            return
        }

        val now = System.currentTimeMillis()
        var postingBlocked = false
        when (profile.status) {
            MarketProfileStatus.ACTIVE -> {
                statusTitleText.text = "No active posting restriction"
                untilText.text = "Account status: Active"
                reasonText.text = "Your Market profile does not show a current posting restriction."
            }
            MarketProfileStatus.BANNED -> {
                postingBlocked = true
                statusTitleText.text = "Market account banned"
                untilText.text = "This restriction has no automatic end date."
                reasonText.text = "This account is banned from posting. Check your Market notices for the decision details."
            }
            MarketProfileStatus.SUSPENDED -> {
                val until = profile.suspendedUntil?.toDate()
                if (until == null) {
                    postingBlocked = true
                    statusTitleText.text = "Posting is paused"
                    untilText.text = "No automatic end date is recorded."
                    reasonText.text = "Check your Market notices for the decision details."
                } else {
                    val formatted = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(until)
                    val remaining = until.time - now
                    if (remaining > 0L) {
                        postingBlocked = true
                        val days = ((remaining + DAY_MS - 1L) / DAY_MS).coerceAtLeast(1L)
                        statusTitleText.text = "Posting is paused"
                        untilText.text = "Until $formatted · $days day${if (days == 1L) "" else "s"} left"
                        reasonText.text = "Your account is temporarily restricted from posting. Check your Market notices for the decision details."
                    } else {
                        statusTitleText.text = "Suspension period ended"
                        untilText.text = "Restriction ended $formatted"
                        reasonText.text = "The Rules allow posting after suspendedUntil. Your profile still records the past suspension."
                    }
                }
            }
        }
        deniedHeader.visibility = if (postingBlocked) View.VISIBLE else View.GONE
        deniedRow.visibility = if (postingBlocked) View.VISIBLE else View.GONE
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
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
        statusTitleText = makeText("Checking account status…", 20f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        card.addView(statusTitleText, LinearLayout.LayoutParams(-1, -2))
        untilText = makeText("Loading your Market profile…", 12f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        card.addView(untilText, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4); bottomMargin = dp(14)
        })

        val reason = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_bg), 16f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        reason.addView(makeText(MarketSuspendedUiPolicy.REASON_LABEL, 13f, true, color(R.color.market_ink)))
        reasonText = makeText("Your Market account status is being checked.", 13f, false,
            color(R.color.market_ink)).apply { setLineSpacing(dp(2).toFloat(), 1f) }
        reason.addView(reasonText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
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
