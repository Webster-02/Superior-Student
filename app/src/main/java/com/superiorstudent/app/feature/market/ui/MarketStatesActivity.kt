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
 * Screen 21: visual reference gallery for Market loading, empty, offline, refresh-error,
 * and expired states. Feed/cache/network behaviour remains a separate integration step.
 */
class MarketStatesActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout

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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val states = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(12))
        }
        scroll.addView(states, FrameLayout.LayoutParams(-1, -2))

        states.addView(buildOfflineBanner(), spaced())
        states.addView(buildLoadingCard(), spaced())
        states.addView(buildEmptyCard(), spaced())
        states.addView(buildErrorCard(), spaced())
        states.addView(buildExpiredCard())

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
            addView(
                MarketProposalsIconView(this@MarketStatesActivity,
                    MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            )
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(makeText(MarketStatesUiPolicy.TITLE, 20f, true, color(R.color.market_ink)))
        heading.addView(makeText(MarketStatesUiPolicy.SUBTITLE, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })
        row.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildOfflineBanner(): View {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.warn_bg), 18f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        banner.addView(
            MarketStatesIconView(this, MarketStatesIcon.OFFLINE, color(R.color.warn)),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(12) }
        )
        banner.addView(makeText(MarketStatesUiPolicy.OFFLINE_NOTICE, 12.5f, true, color(R.color.warn))
            .apply { setLineSpacing(dp(2).toFloat(), 1f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        return banner
    }

    private fun buildLoadingCard(): View {
        val card = cardContainer(22f, dp(14))
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chips.addView(skeletonBlock(60, 20), LinearLayout.LayoutParams(dp(60), dp(20)))
        chips.addView(skeletonBlock(80, 20), LinearLayout.LayoutParams(dp(80), dp(20)).apply {
            marginStart = dp(6)
        })
        card.addView(chips)
        card.addView(skeletonLine(14, 0.85f), LinearLayout.LayoutParams(-2, dp(14)).apply {
            topMargin = dp(12)
        })
        card.addView(skeletonLine(10, 0.60f), LinearLayout.LayoutParams(-2, dp(10)).apply {
            topMargin = dp(8)
        })
        card.addView(skeletonLine(12, 0.40f), LinearLayout.LayoutParams(-2, dp(12)).apply {
            topMargin = dp(14)
        })
        card.contentDescription = "Loading task skeleton"
        return card
    }

    private fun buildEmptyCard(): View {
        val card = cardContainer(22f, dp(20)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(R.color.market_lav), 18f)
            addView(MarketStatesIconView(this@MarketStatesActivity, MarketStatesIcon.INBOX,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        card.addView(iconTile, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10)
        })
        card.addView(makeText(MarketStatesUiPolicy.EMPTY_TITLE, 15f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(makeText(MarketStatesUiPolicy.EMPTY_DESCRIPTION, 12.5f, false,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
            bottomMargin = dp(12)
        })
        val post = actionButton("Post a task", color(R.color.market_match), color(R.color.market_match_ink), 13f)
        post.setOnClickListener {
            startActivity(Intent(this, MarketPostTaskStep1Activity::class.java))
        }
        card.addView(post, LinearLayout.LayoutParams(-2, dp(38)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        return card
    }

    private fun buildErrorCard(): View {
        val card = cardContainer(22f, dp(20)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val iconTile = FrameLayout(this).apply {
            background = rounded(color(R.color.danger_bg), 18f)
            addView(MarketStatesIconView(this@MarketStatesActivity, MarketStatesIcon.REFRESH,
                color(R.color.danger)), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        card.addView(iconTile, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10)
        })
        card.addView(makeText(MarketStatesUiPolicy.ERROR_TITLE, 15f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(makeText(MarketStatesUiPolicy.ERROR_DESCRIPTION, 12.5f, false,
            color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
            bottomMargin = dp(12)
        })
        val retry = actionButton("Retry", color(R.color.market_message_bg),
            color(R.color.market_message_ink), 13f)
        retry.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(MarketStatesUiPolicy.ERROR_TITLE)
                .setMessage(MarketStatesUiPolicy.RETRY_EXPLANATION)
                .setPositiveButton("Got it", null)
                .show()
        }
        card.addView(retry, LinearLayout.LayoutParams(-2, dp(38)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        return card
    }

    private fun buildExpiredCard(): View {
        val card = cardContainer(12f, dp(12))
        card.addView(makeText(MarketStatesUiPolicy.EXPIRED_TITLE, 13.5f, true, color(R.color.market_ink)))
        card.addView(makeText(MarketStatesUiPolicy.EXPIRED_DESCRIPTION, 12.5f, false,
            color(R.color.market_ink2)).apply { setLineSpacing(dp(2).toFloat(), 1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.contentDescription = "${MarketStatesUiPolicy.EXPIRED_TITLE}. ${MarketStatesUiPolicy.EXPIRED_DESCRIPTION}"
        return card
    }

    private fun cardContainer(radius: Float, padding: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), radius, color(R.color.market_line))
            setPadding(padding, padding, padding, padding)
        }

    private fun skeletonBlock(width: Int, height: Int): View =
        View(this).apply { background = rounded(color(R.color.market_line), 99f) }

    private fun skeletonLine(height: Int, fraction: Float): View {
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val lineWidthDp = ((screenWidthDp - 32f - 28f) * fraction).toInt().coerceAtLeast(1)
        return View(this).apply {
            background = rounded(color(R.color.market_line), 99f)
            minimumWidth = dp(lineWidthDp)
            minimumHeight = dp(height)
        }
    }

    private fun actionButton(label: String, fill: Int, foreground: Int, textSize: Float): TextView =
        makeText(label, textSize, true, foreground).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            minWidth = dp(70)
            background = rounded(fill, 12f)
            isClickable = true
            isFocusable = true
            contentDescription = label
        }

    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(10)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketStatesActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketStatesActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketStatesActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
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
