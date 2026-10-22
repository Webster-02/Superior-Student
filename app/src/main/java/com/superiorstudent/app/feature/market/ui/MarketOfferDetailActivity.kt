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

/** Screen 17: native Offer detail page built from local reference fixtures only. */
class MarketOfferDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var saveLabel: TextView
    private var isSaved = false

    private val offerId get() = intent.getStringExtra(EXTRA_ID).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_OFFER_ID }
    private val category get() = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_CATEGORY }
    private val course get() = intent.getStringExtra(EXTRA_COURSE).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.courseLabel(offerId, category) }
    private val offerTitle get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_TITLE }
    private val sellerName get() = intent.getStringExtra(EXTRA_SELLER).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_SELLER }
    private val sellerInitials get() = intent.getStringExtra(EXTRA_INITIALS).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_INITIALS }
    private val priceLabel get() = intent.getStringExtra(EXTRA_PRICE_LABEL).orEmpty()
        .ifBlank { MarketOfferDetailUiPolicy.REFERENCE_PRICE }
    private val priceValue get() = intent.getIntExtra(EXTRA_PRICE_VALUE, 300)
    private val rating get() = intent.getDoubleExtra(EXTRA_RATING, MarketOfferDetailUiPolicy.REFERENCE_RATING)
    private val reviewCount get() = intent.getIntExtra(EXTRA_REVIEW_COUNT, MarketOfferDetailUiPolicy.REFERENCE_REVIEW_COUNT)
    private val verified get() = intent.getBooleanExtra(EXTRA_VERIFIED, true)
    private val avatarColor get() = intent.getIntExtra(EXTRA_AVATAR_COLOR,
        MarketOfferDetailUiPolicy.avatarColorFor(offerId))
    private val aboutText get() = MarketOfferDetailUiPolicy.descriptionFor(
        offerId, intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty())

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg)); clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = false; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(20))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        body.addView(buildCategoryChips(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4); bottomMargin = dp(8)
        })
        body.addView(makeText(offerTitle, 21f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f; setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(buildSellerCard(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14); bottomMargin = dp(14)
        })
        body.addView(buildMetrics(), LinearLayout.LayoutParams(-1, -2))
        body.addView(makeText("About this offer", 14.5f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14); bottomMargin = dp(4)
        })
        body.addView(makeText(aboutText, 13f, false, color(R.color.market_ink2)).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(buildSafetyNote(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(iconButton(MarketOfferDetailIcon.BACK, "Back", color(R.color.market_ink)) {
            finish()
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(makeText("Offer", 20f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        row.addView(iconButton(MarketOfferDetailIcon.FLAG, "Report offer", color(R.color.market_ink)) {
            AlertDialog.Builder(this)
                .setTitle("Report offer")
                .setMessage("Reporting is not connected in this reference preview. No report was submitted.")
                .setPositiveButton("Got it", null).show()
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    private fun buildCategoryChips(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makePill(category, color(R.color.market_lav), color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2, -2))
        row.addView(makePill(course, color(R.color.market_bg), color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        return row
    }

    private fun buildSellerCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
        }
        val avatar = FrameLayout(this).apply {
            background = oval(color(avatarColor))
            addView(makeText(sellerInitials, 15f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        val seller = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        seller.addView(makeText(sellerName, 14f, true, color(R.color.market_ink)))
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        ratingRow.addView(MarketOfferDetailIconView(this@MarketOfferDetailActivity,
            MarketOfferDetailIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(13), dp(13)))
        ratingRow.addView(makeText(
            MarketOfferDetailUiPolicy.ratingLine(rating, reviewCount, verified),
            12f, false, color(R.color.market_ink2)
        ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        seller.addView(ratingRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.addView(seller, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return card
    }

    private fun buildMetrics(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(metricCard("Price", priceLabel, color(R.color.market_lav)),
            LinearLayout.LayoutParams(0, dp(94), 1f))
        row.addView(metricCard("Delivery", MarketOfferDetailUiPolicy.DELIVERY_LABEL,
            color(R.color.market_sky)), LinearLayout.LayoutParams(0, dp(94), 1f).apply {
            marginStart = dp(8)
        })
        return row
    }

    private fun metricCard(label: String, value: String, fill: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(fill, 18f)
            addView(makeText(label, 11f, false, color(R.color.market_ink2)))
            addView(makeText(value, 13.5f, true, color(R.color.market_ink)).apply {
                setLineSpacing(dp(1).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }

    private fun buildSafetyNote(): View {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_sky), 18f)
        }
        note.addView(MarketOfferDetailIconView(this, MarketOfferDetailIcon.INFO,
            color(R.color.market_ink)), LinearLayout.LayoutParams(dp(20), dp(20)).apply {
            topMargin = dp(1)
        })
        note.addView(makeText(MarketOfferDetailUiPolicy.SAFETY_NOTE, 12f, false,
            color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        return note
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val save = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Save offer"
            addView(MarketOfferDetailIconView(this@MarketOfferDetailActivity,
                MarketOfferDetailIcon.SAVE, color(R.color.market_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            saveLabel = makeText("Save", 15f, true, color(R.color.market_ink))
            addView(saveLabel, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            setOnClickListener {
                isSaved = !isSaved
                saveLabel.text = if (isSaved) "Saved" else "Save"
                contentDescription = if (isSaved) "Offer saved in this preview" else "Save offer"
                Toast.makeText(this@MarketOfferDetailActivity,
                    if (isSaved) "Saved in this preview only." else "Removed from preview saves.",
                    Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(save, LinearLayout.LayoutParams(0, dp(52), 1f))
        val message = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true; isFocusable = true; contentDescription = "Message seller"
            addView(MarketOfferDetailIconView(this@MarketOfferDetailActivity,
                MarketOfferDetailIcon.MESSAGE, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(makeText("Message", 15f, true, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            setOnClickListener { showChatPreviewConfirmation() }
        }
        row.addView(message, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(10) })
        footer.addView(row, LinearLayout.LayoutParams(-1, -2))
        return footer
    }

    private fun showChatPreviewConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Chat preview only")
            .setMessage("Open a sample chat with $sellerName about this offer? No message will be sent and no live chat will be created.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open chat preview") { _, _ ->
                startActivity(Intent(this, MarketTaskChatActivity::class.java).apply {
                    putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, sellerName)
                    putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, offerTitle)
                    putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR,
                        MarketOfferDetailUiPolicy.priceValueForChat(priceValue))
                    putExtra(MarketTaskChatActivity.EXTRA_CHAT_CONTEXT,
                        MarketTaskChatUiPolicy.CHAT_CONTEXT_OFFER)
                })
            }.show()
    }

    private fun iconButton(icon: MarketOfferDetailIcon, description: String, tint: Int,
        action: () -> Unit): FrameLayout = FrameLayout(this).apply {
        background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
        isClickable = true; isFocusable = true; contentDescription = description
        addView(MarketOfferDetailIconView(this@MarketOfferDetailActivity, icon, tint),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnClickListener { action() }
    }

    private fun makePill(label: String, fill: Int, foreground: Int): TextView =
        makeText(label, 11f, true, foreground).apply {
            gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(fill, 99f)
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketOfferDetailActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketOfferDetailActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size
            typeface = ResourcesCompat.getFont(this@MarketOfferDetailActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor); includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(fill)
    }
    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ID = "market.offer.id"
        const val EXTRA_CATEGORY = "market.offer.category"
        const val EXTRA_COURSE = "market.offer.course"
        const val EXTRA_TITLE = "market.offer.title"
        const val EXTRA_DESCRIPTION = "market.offer.description"
        const val EXTRA_PRICE_LABEL = "market.offer.price_label"
        const val EXTRA_PRICE_VALUE = "market.offer.price_value"
        const val EXTRA_SELLER = "market.offer.seller"
        const val EXTRA_INITIALS = "market.offer.initials"
        const val EXTRA_RATING = "market.offer.rating"
        const val EXTRA_REVIEW_COUNT = "market.offer.review_count"
        const val EXTRA_VERIFIED = "market.offer.verified"
        const val EXTRA_AVATAR_COLOR = "market.offer.avatar_color"
    }
}
