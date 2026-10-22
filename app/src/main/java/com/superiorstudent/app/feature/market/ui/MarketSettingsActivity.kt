package com.superiorstudent.app.feature.market.ui

import android.content.Intent
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

/** Screen 31. Settings controls are local previews; Firebase settings persistence is not connected. */
class MarketSettingsActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var skillsContainer: LinearLayout
    private val selectedSkills = MarketSettingsUiPolicy.defaultSkills.toMutableList()
    private val alertValues = MarketSettingsUiPolicy.alerts.associateWith { it.initialEnabled }.toMutableMap()
    private val alertSwitches = linkedMapOf<MarketSettingsAlert, FrameLayout>()
    private val alertRows = linkedMapOf<MarketSettingsAlert, View>()

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
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
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
            setPadding(dp(16), dp(8), dp(16), dp(24))
            clipToPadding = false
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        page.addView(buildSkillsCard(), LinearLayout.LayoutParams(-1, -2))
        page.addView(makeText(MarketSettingsUiPolicy.ALERTS_TITLE, 16f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
            bottomMargin = dp(8)
        })
        page.addView(buildAlertsCard(), LinearLayout.LayoutParams(-1, -2))
        page.addView(buildLinksCard(), LinearLayout.LayoutParams(-1, -2).apply {
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
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            addView(MarketSettingsIconView(this@MarketSettingsActivity,
                MarketSettingsGlyph.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(makeText(MarketSettingsUiPolicy.TITLE, 20f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildSkillsCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        card.addView(makeText(MarketSettingsUiPolicy.SKILLS_TITLE, 14f, true,
            color(R.color.market_ink)))
        card.addView(makeText(MarketSettingsUiPolicy.SKILLS_SUBTITLE, 12f, false,
            color(R.color.market_ink2)), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
            topMargin = dp(2)
        })
        skillsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(skillsContainer, LinearLayout.LayoutParams(-1, -2))
        renderSkills()
        return card
    }

    private fun renderSkills() {
        if (!::skillsContainer.isInitialized) return
        skillsContainer.removeAllViews()
        selectedSkills.chunked(3).forEachIndexed { index, skills ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            skills.forEachIndexed { chipIndex, skill ->
                row.addView(buildSkillChip(skill), LinearLayout.LayoutParams(-2, dp(36)).apply {
                    if (chipIndex > 0) marginStart = dp(8)
                })
            }
            skillsContainer.addView(row, LinearLayout.LayoutParams(-1, dp(36)).apply {
                if (index > 0) topMargin = dp(8)
            })
        }
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val addChip = makeText(MarketSettingsUiPolicy.ADD_SKILL, 12.5f, true,
            color(R.color.market_ink)).apply {
            background = dashedRounded(color(R.color.market_card), 99f, color(R.color.market_ink3))
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(36)
            isClickable = true
            isFocusable = true
            contentDescription = "Add a skill"
            setOnClickListener { showAddSkillDialog() }
        }
        addRow.addView(addChip, LinearLayout.LayoutParams(-2, dp(36)))
        skillsContainer.addView(addRow, LinearLayout.LayoutParams(-1, dp(36)).apply {
            topMargin = if (selectedSkills.isEmpty()) 0 else dp(8)
        })
    }

    private fun buildSkillChip(skill: String): View =
        makeText(skill, 12.5f, true, skillTextColor()).apply {
            background = rounded(skillFillColor(), 99f)
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(36)
            isClickable = true
            isFocusable = true
            contentDescription = "Remove skill " + skill
            setOnClickListener {
                selectedSkills.remove(skill)
                renderSkills()
            }
        }

    private fun showAddSkillDialog() {
        val available = MarketSettingsUiPolicy.availableToAdd(selectedSkills)
        if (available.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("All skills added")
                .setMessage("Remove an existing skill before adding another one.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Add a skill")
            .setItems(available.toTypedArray()) { dialog, which ->
                val skill = available[which]
                if (MarketSettingsUiPolicy.canAddSkill(selectedSkills, skill)) {
                    selectedSkills.add(skill)
                    renderSkills()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildAlertsCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        MarketSettingsUiPolicy.alerts.forEachIndexed { index, alert ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                minimumHeight = dp(56)
                isClickable = true
                isFocusable = true
                setOnClickListener { toggleAlert(alert) }
            }
            row.addView(makeText(alert.label, 13.5f, false, color(R.color.market_ink)),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(10)
            })
            val switch = FrameLayout(this).apply {
                background = rounded(switchTrackColor(alert), 99f)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            alertSwitches[alert] = switch
            row.addView(switch, LinearLayout.LayoutParams(dp(46), dp(28)))
            alertRows[alert] = row
            refreshAlertSwitch(alert)
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (index < MarketSettingsUiPolicy.alerts.lastIndex) {
                card.addView(View(this).apply {
                    setBackgroundColor(color(R.color.market_line))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        return card
    }

    private fun toggleAlert(alert: MarketSettingsAlert) {
        alertValues[alert] = !(alertValues[alert] ?: false)
        refreshAlertSwitch(alert)
    }

    private fun refreshAlertSwitch(alert: MarketSettingsAlert) {
        val enabled = alertValues[alert] ?: false
        val track = alertSwitches[alert] ?: return
        track.background = rounded(switchTrackColor(alert), 99f)
        track.removeAllViews()
        val thumb = View(this).apply {
            background = rounded(if (enabled) color(R.color.market_match_ink)
                else color(R.color.market_ink3), 99f)
        }
        val params = FrameLayout.LayoutParams(dp(22), dp(22),
            if (enabled) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL)
        if (enabled) params.marginEnd = dp(3) else params.marginStart = dp(3)
        params.topMargin = dp(3)
        track.addView(thumb, params)
        alertRows[alert]?.contentDescription = alert.label + if (enabled) ", on" else ", off"
    }

    private fun switchTrackColor(alert: MarketSettingsAlert): Int =
        if (alertValues[alert] == true) color(R.color.market_match) else color(R.color.market_line)

    private fun buildLinksCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        card.addView(buildLinkRow(MarketSettingsUiPolicy.BLOCKED_USERS, MarketSettingsGlyph.BLOCKED,
            R.color.market_rules_ban_bg, MarketSettingsUiPolicy.BLOCKED_COUNT) {
            startActivity(Intent(this, MarketBlockedUsersActivity::class.java))
        })
        addDivider(card)
        card.addView(buildLinkRow(MarketSettingsUiPolicy.HELP_AND_SAFETY, MarketSettingsGlyph.HELP,
            R.color.market_mint) {
            startActivity(Intent(this, MarketHelpActivity::class.java))
        })
        addDivider(card)
        card.addView(buildLinkRow(MarketSettingsUiPolicy.MY_REPORTS, MarketSettingsGlyph.FLAG,
            R.color.market_peach) {
            startActivity(Intent(this, MarketMyReportsActivity::class.java))
        })
        return card
    }

    private fun buildLinkRow(
        title: String,
        glyph: MarketSettingsGlyph,
        iconBackground: Int,
        subtitle: String? = null,
        action: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            minimumHeight = dp(66)
            isClickable = true
            isFocusable = true
            contentDescription = if (subtitle.isNullOrBlank()) title else "$title, $subtitle"
            setOnClickListener { action() }
        }
        val tile = FrameLayout(this).apply {
            background = rounded(color(iconBackground), 14f)
            addView(MarketSettingsIconView(this@MarketSettingsActivity, glyph,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        }
        row.addView(tile, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = dp(12)
        })
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(makeText(title, 14f, true, color(R.color.market_ink)))
        if (!subtitle.isNullOrBlank()) {
            textColumn.addView(makeText(subtitle, 12f, false, color(R.color.market_ink2)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(MarketSettingsIconView(this, MarketSettingsGlyph.CHEVRON,
            color(R.color.market_ink3)), LinearLayout.LayoutParams(dp(18), dp(18)))
        return row
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(color(R.color.market_line))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun skillFillColor(): Int =
        if (AppThemePreferences.isNight(this)) color(R.color.market_match) else color(R.color.market_ink)

    private fun skillTextColor(): Int =
        if (AppThemePreferences.isNight(this)) color(R.color.market_match_ink) else Color.WHITE

    private fun showLocalPreviewNotice() {
        AlertDialog.Builder(this)
            .setTitle(MarketSettingsUiPolicy.LOCAL_PREVIEW_TITLE)
            .setMessage(MarketSettingsUiPolicy.LOCAL_PREVIEW_MESSAGE)
            .setPositiveButton("OK", null)
            .show()
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

    private fun font(medium: Boolean): Typeface =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketSettingsActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketSettingsActivity)
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

    private fun dashedRounded(fill: Int, radius: Float, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke, dp(4).toFloat(), dp(3).toFloat())
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
