package com.superiorstudent.app.feature.market.ui

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
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 32: blocked-user list from the signed-in student's Market profile. */
class MarketBlockedUsersActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var countText: TextView
    private lateinit var usersCard: LinearLayout
    private val repository by lazy { MarketRepository() }
    private val blockedUsers = mutableListOf<MarketBlockedUser>()
    private var loading = true
    private var loadError: String? = null

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

        usersCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.market_card), 24f, color(R.color.market_line))
            clipToOutline = true
        }
        page.addView(usersCard, LinearLayout.LayoutParams(-1, -2))
        renderBlockedUsers()
        loadBlockedUsers()

        page.addView(buildInformationNote(), LinearLayout.LayoutParams(-1, -2).apply {
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
            addView(
                MarketBlockedUsersIconView(
                    this@MarketBlockedUsersActivity,
                    MarketBlockedUsersGlyph.BACK,
                    color(R.color.market_ink)
                ),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            )
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeText(
            MarketBlockedUsersUiPolicy.TITLE, 20f, true, color(R.color.market_ink)
        ))
        countText = makeText(
            MarketBlockedUsersUiPolicy.countLabel(blockedUsers.size),
            12f, false, color(R.color.market_ink2)
        )
        countText.contentDescription = "Blocked user count"
        titleColumn.addView(countText, LinearLayout.LayoutParams(-1, -2))
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun renderBlockedUsers() {
        usersCard.removeAllViews()
        countText.text = MarketBlockedUsersUiPolicy.countLabel(blockedUsers.size)

        if (loading || loadError != null) {
            usersCard.addView(makeText(loadError ?: "Loading blocked users…", 14f, false,
                color(R.color.market_ink2)), LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(16), dp(18), dp(16), dp(18))
            })
            return
        }

        if (blockedUsers.isEmpty()) {
            usersCard.addView(makeText(
                "No blocked students.", 14f, false, color(R.color.market_ink2)
            ), LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(16), dp(18), dp(16), dp(18))
            })
            return
        }

        blockedUsers.forEachIndexed { index, user ->
            usersCard.addView(buildUserRow(user), LinearLayout.LayoutParams(-1, -2))
            if (index < blockedUsers.lastIndex) {
                usersCard.addView(View(this).apply {
                    setBackgroundColor(color(R.color.market_line))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
    }

    private fun buildUserRow(user: MarketBlockedUser): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }

        val avatar = TextView(this).apply {
            text = user.initials
            textSize = 14f
            typeface = font(true)
            setTextColor(color(R.color.market_ink))
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(user.avatarBackgroundRes))
            }
            contentDescription = "Initials " + user.initials
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(12)
        })

        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(makeText(user.name, 14f, true, color(R.color.market_ink)))
        textColumn.addView(makeText(user.universityId, 12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(8)
        })

        val unblock = TextView(this).apply {
            text = "Unblock"
            textSize = 12.5f
            typeface = font(true)
            setTextColor(color(R.color.market_ink))
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 12f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Unblock " + user.name
            setOnClickListener { confirmUnblock(user) }
        }
        row.addView(unblock, LinearLayout.LayoutParams(-2, dp(36)))
        return row
    }

    private fun loadBlockedUsers() {
        try {
            repository.ensureMarketProfile().addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                loading = false
                if (!result.isSuccessful) {
                    loadError = result.exception?.localizedMessage ?: "Blocked users could not be loaded."
                } else {
                    loadError = null
                    blockedUsers.clear()
                    blockedUsers.addAll(result.result?.blockedUids.orEmpty().map { uid ->
                        MarketBlockedUser(uid, "Blocked student", uid,
                            "ST", R.color.market_sky)
                    })
                }
                renderBlockedUsers()
            }
        } catch (error: Exception) {
            loading = false
            loadError = error.localizedMessage ?: "Blocked users could not be loaded."
            renderBlockedUsers()
        }
    }

    private fun confirmUnblock(user: MarketBlockedUser) {
        AlertDialog.Builder(this)
            .setTitle("Unblock student?")
            .setMessage("You may see this student's Market listings again.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unblock") { _, _ ->
                try {
                    repository.unblockUser(user.id).addOnCompleteListener { result ->
                        if (isFinishing || isDestroyed) return@addOnCompleteListener
                        if (result.isSuccessful) {
                            blockedUsers.removeAll { it.id == user.id }
                            renderBlockedUsers()
                            countText.announceForAccessibility("Student unblocked")
                        } else {
                            android.widget.Toast.makeText(this,
                                result.exception?.localizedMessage ?: "Student could not be unblocked.",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (error: Exception) {
                    android.widget.Toast.makeText(this,
                        error.localizedMessage ?: "Student could not be unblocked.",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun buildInformationNote(): View {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(color(R.color.market_sky), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        note.addView(
            MarketBlockedUsersIconView(
                this,
                MarketBlockedUsersGlyph.INFO,
                color(R.color.market_ink)
            ),
            LinearLayout.LayoutParams(dp(20), dp(22)).apply { marginEnd = dp(12) }
        )
        note.addView(makeText(
            MarketBlockedUsersUiPolicy.INFORMATION_NOTE, 12f, false, color(R.color.market_ink)
        ).apply {
            gravity = Gravity.TOP
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return note
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
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketBlockedUsersActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketBlockedUsersActivity)
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

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
}
