package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences
import kotlin.math.min

internal data class MarketMenuItem(
    val label: String,
    val subtitle: String,
    val icon: MarketMenuIcon,
    val iconBackground: Int,
    val badge: Int = 0,
    val feedbackMessage: String
)

internal enum class MarketMenuIcon { TASKS, OFFERS, NOTICES, HELP, SETTINGS, CHEVRON }

internal object MarketMenuItems {
    val all = listOf(
        MarketMenuItem(
            label = "My tasks",
            subtitle = "Posted and applied",
            icon = MarketMenuIcon.TASKS,
            iconBackground = R.color.market_peach,
            feedbackMessage = "Review your posted tasks and applications."
        ),
        MarketMenuItem(
            label = "My offers",
            subtitle = "Active, paused, expired",
            icon = MarketMenuIcon.OFFERS,
            iconBackground = R.color.market_lav,
            feedbackMessage = "Manage your active, paused and expired offers."
        ),
        MarketMenuItem(
            label = "Notices",
            subtitle = "Activity and messages from U2U team",
            icon = MarketMenuIcon.NOTICES,
            iconBackground = R.color.market_sky,
            badge = 2,
            feedbackMessage = "Review Market activity updates and messages from the U2U team."
        ),
        MarketMenuItem(
            label = "Help and safety",
            subtitle = "Rules, FAQs, support",
            icon = MarketMenuIcon.HELP,
            iconBackground = R.color.market_mint,
            feedbackMessage = "Help and safety screen will be implemented in its own reference step."
        ),
        MarketMenuItem(
            label = "Market settings",
            subtitle = "Skills, alerts, blocked users",
            icon = MarketMenuIcon.SETTINGS,
            iconBackground = R.color.market_lav,
            feedbackMessage = "Market settings will be implemented in its own reference step."
        )
    )
}

/** Reference-style bottom sheet used by the Market header overflow action on both listing screens. */
internal object MarketMenuSheet {
    fun show(activity: AppCompatActivity, onSelected: (MarketMenuItem) -> Unit) {
        val dialog = Dialog(activity)
        val sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(activity.getColor(R.color.market_bg))
                val radius = dp(activity, 28).toFloat()
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }

        val handle = View(activity).apply {
            background = rounded(activity.getColor(R.color.market_line), 99f, null, activity)
        }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(activity, 14)
        })

        val title = TextView(activity).apply {
            text = "Market menu"
            textSize = 18f
            typeface = font(activity, true)
            setTextColor(activity.getColor(R.color.market_ink))
            includeFontPadding = false
        }
        sheet.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(activity, 8) })

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                activity.getColor(R.color.market_card), 24f,
                activity.getColor(R.color.market_line), activity
            )
            clipToOutline = true
        }
        MarketMenuItems.all.forEachIndexed { index, item ->
            card.addView(buildRow(activity, item, dialog, onSelected), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            if (index < MarketMenuItems.all.lastIndex) {
                card.addView(View(activity).apply {
                    setBackgroundColor(activity.getColor(R.color.market_line))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)))
            }
        }
        sheet.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setContentView(sheet)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.45f)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.navigationBarColor = activity.getColor(R.color.market_bg)
            WindowCompat.getInsetsController(window, sheet).isAppearanceLightNavigationBars =
                !AppThemePreferences.isNight(activity)
        }
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildRow(
        context: Context,
        item: MarketMenuItem,
        dialog: Dialog,
        onSelected: (MarketMenuItem) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            minimumHeight = dp(context, 66)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                onSelected(item)
            }
        }
        val iconTile = FrameLayout(context).apply {
            background = rounded(context.getColor(item.iconBackground), 14f, null, context)
        }
        iconTile.addView(MenuGlyphView(context, item.icon, context.getColor(R.color.market_ink)),
            FrameLayout.LayoutParams(dp(context, 20), dp(context, 20), Gravity.CENTER))
        row.addView(iconTile, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))

        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(TextView(context).apply {
            text = item.label
            textSize = 14f
            typeface = font(context, true)
            setTextColor(context.getColor(R.color.market_ink))
            includeFontPadding = false
        })
        textColumn.addView(TextView(context).apply {
            text = item.subtitle
            textSize = 12f
            typeface = font(context, false)
            setTextColor(context.getColor(R.color.market_ink2))
            includeFontPadding = false
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, 3)
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 12)
            marginEnd = dp(context, 8)
        })

        if (item.badge > 0) {
            val badge = TextView(context).apply {
                text = item.badge.toString()
                textSize = 11f
                typeface = font(context, true)
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(context, 6), 0, dp(context, 6), 0)
                background = rounded(context.getColor(R.color.market_menu_dot), 99f, null, context)
                minWidth = dp(context, 20)
                minHeight = dp(context, 20)
            }
            row.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 20)).apply {
                marginEnd = dp(context, 8)
            })
        }
        row.addView(MenuGlyphView(context, MarketMenuIcon.CHEVRON, context.getColor(R.color.market_ink3)),
            LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)))
        return row
    }

    private fun font(context: Context, medium: Boolean) =
        ResourcesCompat.getFont(context, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: android.graphics.Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif",
                android.graphics.Typeface.NORMAL
            )

    private fun rounded(fill: Int, radius: Float, stroke: Int?, context: Context) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radius).toFloat()
            setColor(fill)
            stroke?.let { setStroke(dp(context, 1), it) }
        }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dp(context: Context, value: Float) =
        (value * context.resources.displayMetrics.density).toInt()
}

private class MenuGlyphView(
    context: Context,
    private val glyph: MarketMenuIcon,
    tint: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height) / 24f
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        val path = Path()
        when (glyph) {
            MarketMenuIcon.TASKS -> {
                canvas.drawRoundRect(3f, 7f, 21f, 20f, 2f, 2f, paint)
                path.moveTo(8f, 7f); path.lineTo(8f, 5f)
                path.quadTo(8f, 3f, 10f, 3f); path.lineTo(14f, 3f)
                path.quadTo(16f, 3f, 16f, 5f); path.lineTo(16f, 7f)
                path.moveTo(3f, 13f); path.lineTo(21f, 13f)
                canvas.drawPath(path, paint)
            }
            MarketMenuIcon.OFFERS -> {
                path.moveTo(4f, 13f); path.lineTo(6f, 5f); path.lineTo(18f, 5f)
                path.lineTo(20f, 13f); path.lineTo(20f, 19f)
                path.quadTo(20f, 20f, 18f, 20f); path.lineTo(6f, 20f)
                path.quadTo(4f, 20f, 4f, 19f); path.close()
                path.moveTo(4f, 13f); path.lineTo(20f, 13f)
                canvas.drawPath(path, paint)
            }
            MarketMenuIcon.NOTICES -> {
                path.moveTo(5f, 17f); path.quadTo(7f, 14f, 7f, 10f)
                path.quadTo(7f, 5f, 12f, 5f); path.quadTo(17f, 5f, 17f, 10f)
                path.quadTo(17f, 14f, 19f, 17f); path.close()
                path.moveTo(4f, 17f); path.lineTo(20f, 17f)
                path.moveTo(10f, 20f); path.quadTo(12f, 22f, 14f, 20f)
                canvas.drawPath(path, paint)
            }
            MarketMenuIcon.HELP -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 13f
                canvas.drawText("?", 8.6f, 16.6f, paint)
                paint.style = Paint.Style.STROKE
            }
            MarketMenuIcon.SETTINGS -> {
                path.moveTo(3f, 7f); path.lineTo(21f, 7f)
                path.moveTo(3f, 17f); path.lineTo(21f, 17f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(8f, 7f, 2f, paint)
                canvas.drawCircle(15f, 17f, 2f, paint)
            }
            MarketMenuIcon.CHEVRON -> {
                path.moveTo(9f, 6f); path.lineTo(15f, 12f); path.lineTo(9f, 18f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
