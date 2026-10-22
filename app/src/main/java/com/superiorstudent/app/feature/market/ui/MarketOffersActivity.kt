package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import kotlin.math.min

/** Native reference implementation for screen 03 Offers. Data here is visual preview content only. */
class MarketOffersActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var list: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var categoryChips: Map<String, TextView>
    private var selectedCategory = "All"
    private var selectedSort = MarketOfferSort.REFERENCE_ORDER
    private var searchVisible = false

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
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16)
        })
        searchRow = buildSearchRow().apply { visibility = View.GONE }
        content.addView(searchRow, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(6); marginStart = dp(16); marginEnd = dp(16)
        })
        content.addView(buildTabs(), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(6); marginStart = dp(16); marginEnd = dp(16)
        })
        content.addView(buildCategoryRow(), LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(12)
        })

        val panel = FrameLayout(this)
        content.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        val scroll = ScrollView(this).apply {
            clipToPadding = false; isFillViewport = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        panel.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(100))
        }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        emptyState = makeText("No offers found\\nTry another category or search term.", 14f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        panel.addView(emptyState, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        val fab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), 0)
            background = rounded(color(R.color.market_match), 99f)
            elevation = dp(8).toFloat(); isClickable = true; isFocusable = true
            addView(OfferIconView(this@MarketOffersActivity, OfferIcon.PLUS, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText("Post an offer", 14f, true, color(R.color.market_match_ink)))
        }
        fab.setOnClickListener { startActivity(Intent(this, MarketPostOfferActivity::class.java)) }
        root.addView(fab, FrameLayout.LayoutParams(-2, dp(52), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(16); bottomMargin = dp(22)
        })

        applyWindowInsets()
        wireActions()
        updateCategoryChips()
        renderOffers()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (searchVisible) closeSearch() else finish() }
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(iconButton(OfferIcon.BACK, "Back", 40) { finish() })
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        }
        titleColumn.addView(makeText("Market", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        titleColumn.addView(makeText("Help each other on campus", 12f, false, color(R.color.market_ink2)))
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12); marginEnd = dp(8)
        })
        row.addView(iconButton(OfferIcon.SEARCH, "Search offers", 40) {
            if (searchVisible) closeSearch() else openSearch()
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
        val menu = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = "Market menu"
        }
        menu.addView(OfferIconView(this, OfferIcon.MORE, color(R.color.market_ink)),
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        menu.addView(View(this).apply { background = rounded(color(R.color.market_menu_dot), 99f) },
            FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(7); marginEnd = dp(7)
            })
        menu.setOnClickListener { showMenu(menu) }
        row.addView(menu, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    private fun buildSearchRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            setPadding(dp(10), 0, dp(8), 0)
        }
        row.addView(OfferIconView(this, OfferIcon.SEARCH, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        searchInput = EditText(this).apply {
            setSingleLine(true); textSize = 12f; typeface = font(false)
            setTextColor(color(R.color.market_ink)); setHintTextColor(color(R.color.market_ink2))
            hint = "Search offers"; background = null
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT; setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderOffers() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        row.addView(searchInput, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(makeText("×", 22f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; setOnClickListener { closeSearch() }
        }, LinearLayout.LayoutParams(dp(28), -1))
        return row
    }

    private fun buildTabs(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }
        outer.addView(makeText("Requests", 13f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(makeText("Offers", 13f, true, color(R.color.market_segment_ink)).apply {
            gravity = Gravity.CENTER; background = rounded(color(R.color.market_segment), 15f)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        return outer
    }

    private fun buildCategoryRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val labels = listOf("All", "Notes", "Tutoring", "Design", "Typing")
        val mapped = linkedMapOf<String, TextView>()
        labels.forEachIndexed { index, category ->
            val chip = makeText(category, 12.5f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
                setOnClickListener {
                    selectedCategory = category
                    updateCategoryChips()
                    renderOffers()
                }
            }
            val params = LinearLayout.LayoutParams(-2, dp(38))
            if (index > 0) params.marginStart = dp(8)
            chips.addView(chip, params)
            mapped[category] = chip
        }
        categoryChips = mapped
        scroll.addView(chips, FrameLayout.LayoutParams(-2, -1))
        row.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(iconButton(OfferIcon.FILTER, "Sort offers", 38) { showSortDialog() },
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(8) })
        return row
    }

    private fun updateCategoryChips() {
        categoryChips.forEach { (category, chip) ->
            val selected = category == selectedCategory
            chip.background = rounded(
                color(if (selected) R.color.market_segment else R.color.market_card),
                99f,
                if (selected) null else color(R.color.market_line)
            )
            chip.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink))
        }
    }

    private fun iconButton(icon: OfferIcon, label: String, size: Int, action: () -> Unit): View =
        FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 14f, color(R.color.market_line))
            isClickable = true; isFocusable = true; contentDescription = label
            val iconSize = if (icon == OfferIcon.FILTER) 18 else 20
            addView(OfferIconView(this@MarketOffersActivity, icon, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER))
            setOnClickListener { action() }
        }.also { it.layoutParams = ViewGroup.LayoutParams(dp(size), dp(size)) }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketOffersActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketOffersActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun wireActions() {
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(searchInput.windowToken, 0)
                true
            } else false
        }
    }

    private fun openSearch() {
        searchVisible = true; searchRow.visibility = View.VISIBLE; searchInput.requestFocus()
        searchInput.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        searchVisible = false; searchInput.setText(""); searchRow.visibility = View.GONE
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
        renderOffers()
    }

    private fun openOfferDetail(offer: MarketOfferPreview) {
        startActivity(Intent(this, MarketOfferDetailActivity::class.java).apply {
            putExtra(MarketOfferDetailActivity.EXTRA_ID, offer.id)
            putExtra(MarketOfferDetailActivity.EXTRA_CATEGORY, offer.category)
            putExtra(MarketOfferDetailActivity.EXTRA_COURSE,
                MarketOfferDetailUiPolicy.courseLabel(offer.id, offer.category))
            putExtra(MarketOfferDetailActivity.EXTRA_TITLE, offer.title)
            putExtra(MarketOfferDetailActivity.EXTRA_DESCRIPTION, offer.description)
            putExtra(MarketOfferDetailActivity.EXTRA_PRICE_LABEL, offer.priceLabel)
            putExtra(MarketOfferDetailActivity.EXTRA_PRICE_VALUE, offer.priceValue ?: -1)
            putExtra(MarketOfferDetailActivity.EXTRA_SELLER, offer.sellerName)
            putExtra(MarketOfferDetailActivity.EXTRA_INITIALS, offer.initials)
            putExtra(MarketOfferDetailActivity.EXTRA_RATING, offer.rating)
            putExtra(MarketOfferDetailActivity.EXTRA_REVIEW_COUNT, offer.ratingCount)
            putExtra(MarketOfferDetailActivity.EXTRA_VERIFIED, offer.verifiedStudent)
            putExtra(MarketOfferDetailActivity.EXTRA_AVATAR_COLOR, offer.avatarColor)
        })
    }

    private fun showOfferChatPreview(offer: MarketOfferPreview) {
        AlertDialog.Builder(this)
            .setTitle("Chat preview only")
            .setMessage("Open a sample chat with " + offer.sellerName +
                "? No message will be sent and no live chat will be created.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open chat preview") { _, _ ->
                startActivity(Intent(this, MarketTaskChatActivity::class.java).apply {
                    putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, offer.sellerName)
                    putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, offer.title)
                    putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR,
                        MarketOfferDetailUiPolicy.priceValueForChat(offer.priceValue ?: -1))
                    putExtra(MarketTaskChatActivity.EXTRA_CHAT_CONTEXT,
                        MarketTaskChatUiPolicy.CHAT_CONTEXT_OFFER)
                })
            }
            .show()
    }

    private fun showMenu(anchor: View) {
        MarketMenuSheet.show(this) { item ->
            when (item.label) {
                "My tasks" -> startActivity(Intent(this, MarketMyTasksActivity::class.java))
                "My offers" -> startActivity(Intent(this, MarketMyOffersActivity::class.java))
                "Notices" -> startActivity(Intent(this, MarketNoticesActivity::class.java))
                "Help and safety" -> startActivity(Intent(this, MarketHelpActivity::class.java))
                else -> toast(item.feedbackMessage)
            }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Reference order", "Highest rated", "Lowest price")
        AlertDialog.Builder(this).setTitle("Sort offers")
            .setSingleChoiceItems(options, selectedSort.ordinal) { dialog, which ->
                selectedSort = MarketOfferSort.values()[which]
                renderOffers()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renderOffers() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val shown = MarketOffersUiPolicy.filterAndSort(
            MarketOfferFixtures.all, selectedCategory, searchInput.text?.toString().orEmpty(), selectedSort
        )
        shown.forEach { offer ->
            list.addView(buildOfferCard(offer), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        emptyState.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildOfferCard(offer: MarketOfferPreview): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "View offer: " + offer.title
            setOnClickListener { openOfferDetail(offer) }
        }
        val sellerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        sellerRow.addView(makeText(offer.initials, 13f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = oval(color(offer.avatarColor))
        }, LinearLayout.LayoutParams(dp(38), dp(38)))
        val sellerInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sellerInfo.addView(makeText(offer.sellerName, 13f, true, color(R.color.market_ink)))
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        ratingRow.addView(OfferIconView(this, OfferIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(13), dp(13)))
        val verified = if (offer.verifiedStudent) " · Verified student" else " · Student"
        ratingRow.addView(makeText(offer.rating.toString() + " (" + offer.ratingCount + ")" + verified,
            12f, false, color(R.color.market_ink2)), LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(3)
        })
        sellerInfo.addView(ratingRow)
        sellerRow.addView(sellerInfo, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(10)
        })
        sellerRow.addView(makeText(offer.category, 12f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(color(categoryColor(offer.category)), 99f)
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        card.addView(sellerRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        card.addView(makeText(offer.title, 15f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.01f; setLineSpacing(dp(2).toFloat(), 1f)
        })
        card.addView(makeText(offer.description, 12f, false, color(R.color.market_ink2)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(makeText(offer.priceLabel, 14f, true, color(R.color.market_ink)))
        footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val message = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color(R.color.market_message_bg), 12f)
            addView(OfferIconView(this@MarketOffersActivity, OfferIcon.MESSAGE, color(R.color.market_message_ink)),
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) })
            addView(makeText("Message", 13f, true, color(R.color.market_message_ink)))
            isClickable = true; isFocusable = true
            setOnClickListener { showOfferChatPreview(offer) }
        }
        footer.addView(message, LinearLayout.LayoutParams(-2, dp(34)))
        card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return card
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value; textSize = size; typeface = font(medium); setTextColor(textColor)
            includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
            if (size <= 12.5f) setLineSpacing(dp(2).toFloat(), 1f)
        }

    private fun font(medium: Boolean): Typeface =
        ResourcesCompat.getFont(this, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun oval(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
    private fun categoryColor(category: String) = when (category) {
        "Notes" -> R.color.market_lav
        "Typing" -> R.color.market_peach
        else -> R.color.market_sky
    }
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
