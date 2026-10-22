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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 28: posting rules and safety, with native light/dark theme support. */
class MarketRulesActivity : AppCompatActivity() {
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

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
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
            setPadding(dp(16), dp(6), dp(16), dp(24))
            clipToPadding = false
        }
        scroll.addView(page, android.widget.FrameLayout.LayoutParams(-1, -2))

        page.addView(sectionHeading(MarketRulesUiPolicy.ALLOWED_HEADING),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        MarketRulesUiPolicy.allowedItems.forEachIndexed { index, text ->
            page.addView(buildRuleRow(text, allowed = true),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = if (index == MarketRulesUiPolicy.allowedItems.lastIndex) dp(4) else dp(8)
                })
        }

        page.addView(sectionHeading(MarketRulesUiPolicy.NOT_ALLOWED_HEADING),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            })
        MarketRulesUiPolicy.prohibitedItems.forEachIndexed { index, text ->
            page.addView(buildRuleRow(text, allowed = false),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = if (index == MarketRulesUiPolicy.prohibitedItems.lastIndex) dp(8) else dp(8)
                })
        }

        page.addView(sectionHeading(MarketRulesUiPolicy.STAY_SAFE_HEADING),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            })
        page.addView(buildSafetyCard(), LinearLayout.LayoutParams(-1, -2))

        page.addView(sectionHeading(MarketRulesUiPolicy.BROKEN_RULES_HEADING),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
                bottomMargin = dp(8)
            })
        page.addView(buildEnforcementSteps(), LinearLayout.LayoutParams(-1, -2))

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
            setOnClickListener { finish() }
            addView(
                MarketRulesIconView(this@MarketRulesActivity, MarketRulesGlyph.BACK,
                    color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
            )
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = dp(12)
        })

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titles.addView(makeText(MarketRulesUiPolicy.TITLE, 20f, true,
            color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(-1, -2))
        titles.addView(makeText(MarketRulesUiPolicy.SUBTITLE, 12f, false,
            color(R.color.market_ink2)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })
        row.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun sectionHeading(text: String): TextView =
        makeText(text, 13.5f, true, color(R.color.market_ink))

    private fun buildRuleRow(text: String, allowed: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            contentDescription = text
        }
        row.addView(
            MarketRulesIconView(
                this,
                if (allowed) MarketRulesGlyph.CHECK else MarketRulesGlyph.CROSS,
                color(if (allowed) R.color.market_rules_ok else R.color.market_rules_bad)
            ),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                marginEnd = dp(10)
                topMargin = dp(1)
            }
        )
        row.addView(
            makeText(text, 13f, false, color(R.color.market_ink)).apply {
                setLineSpacing(dp(1).toFloat(), 1f)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        return row
    }

    private fun buildSafetyCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_rules_safe_bg), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(
            makeText(MarketRulesUiPolicy.STAY_SAFE_COPY, 12.5f, false,
                color(R.color.market_ink)).apply {
                setLineSpacing(dp(2).toFloat(), 1f)
            },
            LinearLayout.LayoutParams(-1, -2)
        )
        return card
    }

    private fun buildEnforcementSteps(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        MarketRulesUiPolicy.enforcementSteps.forEachIndexed { index, step ->
            val toneColor = when (step.tone) {
                MarketRulesPenaltyTone.INFO -> R.color.market_rules_info_bg
                MarketRulesPenaltyTone.WARNING -> R.color.market_rules_warning_bg
                MarketRulesPenaltyTone.PEACH -> R.color.market_rules_peach_bg
                MarketRulesPenaltyTone.DANGER -> R.color.market_rules_ban_bg
            }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(color(toneColor), 14f)
                setPadding(dp(2), dp(10), dp(2), dp(10))
                contentDescription = "${step.number}, ${step.label}"
            }
            tile.addView(
                makeText(step.number, 15f, true, color(R.color.market_ink)).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, -2)
            )
            tile.addView(
                makeText(step.label, 11f, false, color(R.color.market_ink)).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(4)
                }
            )
            row.addView(tile, LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                if (index < MarketRulesUiPolicy.enforcementSteps.lastIndex) marginEnd = dp(6)
            })
        }
        return row
    }

    private fun makeText(
        value: String,
        size: Float,
        medium: Boolean,
        textColor: Int
    ): TextView = TextView(this).apply {
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
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketRulesActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketRulesActivity)
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

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
