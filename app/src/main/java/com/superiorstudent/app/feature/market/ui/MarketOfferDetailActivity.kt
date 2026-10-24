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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketReportTargetType
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 17: live Offer detail page opened from the public offer feed. */
class MarketOfferDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private val repository by lazy { MarketRepository() }
    private var ratingView: TextView? = null
    private var messageButton: View? = null

    private val offerId get() = intent.getStringExtra(EXTRA_ID).orEmpty()
    private val category get() = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        .ifBlank { "Offer" }
    private val course get() = intent.getStringExtra(EXTRA_COURSE).orEmpty()
    private val offerTitle get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        .ifBlank { "Offer unavailable" }
    private val sellerName get() = intent.getStringExtra(EXTRA_SELLER).orEmpty()
        .ifBlank { "Market seller" }
    private val sellerId get() = intent.getStringExtra(EXTRA_SELLER_ID).orEmpty()
    private val sellerInitials get() = intent.getStringExtra(EXTRA_INITIALS).orEmpty()
        .ifBlank { "ST" }
    private val priceLabel get() = intent.getStringExtra(EXTRA_PRICE_LABEL).orEmpty()
        .ifBlank { "Price unavailable" }
    private val priceValue get() = intent.getIntExtra(EXTRA_PRICE_VALUE, -1)
    private val rating get() = intent.getDoubleExtra(EXTRA_RATING, 0.0)
    private val reviewCount get() = intent.getIntExtra(EXTRA_REVIEW_COUNT, 0)
    private val verified get() = intent.getBooleanExtra(EXTRA_VERIFIED, false)
    private val avatarColor get() = intent.getIntExtra(EXTRA_AVATAR_COLOR,
        MarketOfferDetailUiPolicy.avatarColorFor(offerId))
    private val aboutText get() = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        .ifBlank { "No description provided." }

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
        loadSellerRating()
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
            if (offerId.isBlank()) {
                Toast.makeText(this, "This offer link is missing its ID.", Toast.LENGTH_SHORT).show()
            } else {
                MarketReportSheet.show(this, sellerName, MarketReportTargetType.OFFER, offerId, sellerId)
            }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    private fun buildCategoryChips(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makePill(category, color(R.color.market_lav), color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2, -2))
        course.takeIf { it.isNotBlank() }?.let {
            row.addView(makePill(it, color(R.color.market_bg), color(R.color.market_ink)),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        }
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
        ).also { ratingView = it }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
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
        row.addView(metricCard("Delivery", "Ask the seller in chat",
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
        val message = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true; isFocusable = true; contentDescription = "Message seller"
            addView(MarketOfferDetailIconView(this@MarketOfferDetailActivity,
                MarketOfferDetailIcon.MESSAGE, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(makeText("Message", 15f, true, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            val canMessage = offerId.isNotBlank() && sellerId.isNotBlank() &&
                sellerId != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            isEnabled = canMessage
            alpha = if (canMessage) 1f else 0.55f
            setOnClickListener { openOfferConversation() }
        }
        messageButton = message
        row.addView(message, LinearLayout.LayoutParams(-1, dp(52)))
        footer.addView(row, LinearLayout.LayoutParams(-1, -2))
        return footer
    }

    private fun openOfferConversation() {
        if (offerId.isBlank()) {
            Toast.makeText(this, "This offer link is missing its ID.", Toast.LENGTH_SHORT).show()
            return
        }
        messageButton?.isEnabled = false
        try {
            repository.openOfferConversation(offerId).addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                messageButton?.isEnabled = sellerId != com.google.firebase.auth.FirebaseAuth
                    .getInstance().currentUser?.uid
                if (!result.isSuccessful) {
                    Toast.makeText(this, result.exception?.localizedMessage
                        ?: "A Market conversation could not be opened.", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                startActivity(android.content.Intent(this, MarketTaskChatActivity::class.java).apply {
                    putExtra(MarketTaskChatActivity.EXTRA_CONVERSATION_ID, result.result)
                    putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, sellerName)
                    putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, offerTitle)
                    putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR, priceValue.coerceAtLeast(0))
                })
            }
        } catch (error: Exception) {
            messageButton?.isEnabled = true
            Toast.makeText(this, error.localizedMessage
                ?: "A Market conversation could not be opened.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSellerRating() {
        if (sellerId.isBlank()) {
            ratingView?.text = "Seller rating unavailable"
            return
        }
        try {
            repository.loadPublicUserStats(sellerId).addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                val stats = if (result.isSuccessful) result.result else null
                ratingView?.text = when {
                    !result.isSuccessful -> "Seller rating unavailable"
                    stats == null || stats.ratingCount == 0 -> "No ratings yet"
                    else -> "%.1f · %d %s".format(java.util.Locale.US,
                        stats.ratingSum / stats.ratingCount, stats.ratingCount,
                        if (stats.ratingCount == 1) "rating" else "ratings")
                }
            }
        } catch (_: Exception) {
            ratingView?.text = "Seller rating unavailable"
        }
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
        const val EXTRA_SELLER_ID = "market.offer.seller_id"
        const val EXTRA_INITIALS = "market.offer.initials"
        const val EXTRA_RATING = "market.offer.rating"
        const val EXTRA_REVIEW_COUNT = "market.offer.review_count"
        const val EXTRA_VERIFIED = "market.offer.verified"
        const val EXTRA_AVATAR_COLOR = "market.offer.avatar_color"
    }
}
