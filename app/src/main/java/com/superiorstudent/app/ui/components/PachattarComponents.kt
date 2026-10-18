package com.superiorstudent.app.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AttendanceBand
import com.superiorstudent.app.ui.HomeDisplayRules
import com.superiorstudent.app.ui.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun px(context: Context, dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
private fun regularTypeface(context: Context): Typeface =
    ResourcesCompat.getFont(context, R.font.poppins) ?: Typeface.create("sans-serif", Typeface.NORMAL)
private fun mediumTypeface(context: Context): Typeface =
    ResourcesCompat.getFont(context, R.font.poppins_medium) ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
private fun roundRect(context: Context, color: Int, radiusDp: Float): GradientDrawable =
    GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = px(context, radiusDp).toFloat() }
private fun outlinedCard(context: Context, color: Int, radiusDp: Float): GradientDrawable =
    roundRect(context, color, radiusDp).apply { setStroke(px(context, 1f), Palette.line(context)) }

enum class ChipTone { OK, WARN, BAD, NEUTRAL, LIME }

object Chip {
    fun create(context: Context, text: String, tone: ChipTone): TextView {
        val colors = when (tone) {
            ChipTone.OK -> Palette.okBg(context) to Palette.ok(context)
            ChipTone.WARN -> Palette.warnBg(context) to Palette.warn(context)
            ChipTone.BAD -> Palette.badBg(context) to Palette.bad(context)
            ChipTone.NEUTRAL -> Palette.lav(context) to Palette.ink(context)
            ChipTone.LIME -> Palette.lime(context) to Palette.limeInk(context)
        }
        return createCustom(context, text, colors.first, colors.second)
    }
    fun createCustom(context: Context, text: String, backgroundColor: Int, textColor: Int): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(textColor)
            typeface = mediumTypeface(context)
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(px(context, 10f), px(context, 4f), px(context, 10f), px(context, 4f))
            background = roundRect(context, backgroundColor, 999f)
            minHeight = px(context, 24f)
            contentDescription = text
        }
}

object Avatar {
    fun bind(context: Context, view: TextView, fullName: String) {
        view.text = HomeDisplayRules.initials(fullName)
        view.typeface = mediumTypeface(context)
        view.textSize = 14f
        view.gravity = Gravity.CENTER
        view.includeFontPadding = false
        view.setTextColor(Palette.limeInk(context))
        view.background = roundRect(context, Palette.lime(context), 999f)
        view.minWidth = px(context, 42f)
        view.minHeight = px(context, 42f)
        view.contentDescription = if (fullName.isBlank()) "Student avatar" else "Avatar for " + fullName
    }
}

object PageHeader {
    fun bind(context: Context, dateView: TextView, greetingView: TextView, avatarView: TextView, fullName: String) {
        dateView.text = SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH).format(Date())
        dateView.textSize = 12f
        dateView.setTextColor(Palette.ink2(context))
        dateView.typeface = regularTypeface(context)
        dateView.includeFontPadding = false
        val firstName = HomeDisplayRules.firstName(fullName)
        greetingView.text = if (firstName.isNotBlank()) "Salam, " + firstName else "Salam"
        greetingView.textSize = 26f
        greetingView.setTextColor(Palette.ink(context))
        greetingView.typeface = mediumTypeface(context)
        greetingView.letterSpacing = -0.023f
        greetingView.includeFontPadding = false
        greetingView.maxLines = 1
        greetingView.setSingleLine(true)
        greetingView.ellipsize = android.text.TextUtils.TruncateAt.END
        Avatar.bind(context, avatarView, fullName)
    }
}

object ProgressBar {
    fun create(context: Context, percentage: Double): View = AttendanceProgressView(context, percentage.coerceIn(0.0, 100.0))
}

private class AttendanceProgressView(context: Context, private val percentage: Double) : View(context) {
    private val density = resources.displayMetrics.density
    private fun d(value: Float) = value * density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = d(11f); textAlign = Paint.Align.CENTER; typeface = mediumTypeface(context) }
    init {
        contentDescription = percentage.toInt().toString() + "% attendance; 75% target marker"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(d(120f).toInt())
        setMeasuredDimension(requestedWidth, d(40f).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = d(1f)
        val available = (width.toFloat() - pad * 2f).coerceAtLeast(1f)
        val top = d(3f)
        val barHeight = d(12f)
        val radius = barHeight / 2f
        track.color = Palette.heroTrack(context)
        fill.color = Palette.heroFill(context)
        marker.color = Palette.heroInk(context)
        label.color = Palette.heroInk(context)
        canvas.drawRoundRect(RectF(pad, top, pad + available, top + barHeight), radius, radius, track)
        val filled = (available * percentage.toFloat() / 100f).coerceIn(0f, available)
        if (filled > 0f) canvas.drawRoundRect(RectF(pad, top, pad + filled, top + barHeight), radius, radius, fill)
        val markerX = pad + available * 0.75f
        val halfMarker = d(1f)
        canvas.drawRoundRect(RectF(markerX - halfMarker, d(0f), markerX + halfMarker, d(25f)), d(1f), d(1f), marker)
        canvas.drawText("75% line", markerX, d(38f), label)
    }
}

object HeroCard {
    fun attendance(context: Context, percentage: Double, safeLeaves: Int?): LinearLayout {
        val band = HomeDisplayRules.attendanceBand(percentage) ?: AttendanceBand.SHORT
        val statusText = when (band) {
            AttendanceBand.SAFE -> "Safe zone"
            AttendanceBand.NEAR_LIMIT -> "Near limit"
            AttendanceBand.SHORT -> "Short"
        }
        return base(context).apply {
            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "Attendance"; textSize = 13f; setTextColor(Palette.heroInk(context))
                    typeface = regularTypeface(context); includeFontPadding = false
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Chip.createCustom(context, statusText, Palette.heroTrack(context), Palette.heroInk(context)))
            }
            addView(top)
            val valueRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                setPadding(0, px(context, 6f), 0, px(context, 8f))
                addView(TextView(context).apply {
                    text = formatPercent(percentage); textSize = 68f; setTextColor(Palette.heroInk(context))
                    typeface = mediumTypeface(context); includeFontPadding = false
                    setFontFeatureSettings("tnum"); letterSpacing = -0.035f
                })
                addView(TextView(context).apply {
                    text = "%"; textSize = 26f; setTextColor(Palette.heroInk(context))
                    typeface = mediumTypeface(context); includeFontPadding = false
                    setPadding(px(context, 2f), 0, 0, px(context, 3f))
                })
            }
            addView(valueRow)
            addView(ProgressBar.create(context, percentage), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(context, 40f)))
            if (safeLeaves != null) addView(TextView(context).apply {
                text = "You can miss " + safeLeaves + " more " + if (safeLeaves == 1) "lecture" else "lectures"
                textSize = 14f; setTextColor(Palette.heroInk(context)); typeface = regularTypeface(context)
                includeFontPadding = false; setPadding(0, px(context, 8f), 0, 0)
            })
        }.also { it.contentDescription = "Overall attendance " + formatPercent(percentage) + " percent, " + statusText }
    }

    fun cgpa(context: Context, cgpa: String): LinearLayout = base(context).apply {
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "CGPA"; textSize = 13f; setTextColor(Palette.heroInk(context))
                typeface = regularTypeface(context); includeFontPadding = false
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Chip.createCustom(context, if (cgpa.isBlank()) "Unavailable" else "Academic overview", Palette.heroTrack(context), Palette.heroInk(context)))
        }
        addView(top)
        addView(TextView(context).apply {
            text = cgpa.ifBlank { "—" }; textSize = 68f; setTextColor(Palette.heroInk(context))
            typeface = mediumTypeface(context); includeFontPadding = false; setFontFeatureSettings("tnum")
            letterSpacing = -0.035f; setPadding(0, px(context, 10f), 0, px(context, 6f))
        })
        addView(TextView(context).apply {
            text = if (cgpa.isBlank()) "CGPA value unavailable from ERP" else "Cumulative grade point average"
            textSize = 12f; setTextColor(Palette.heroInk(context)); typeface = regularTypeface(context)
            includeFontPadding = false; alpha = 0.9f
        })
    }

    private fun base(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(context, 20f), px(context, 20f), px(context, 20f), px(context, 20f))
        background = roundRect(context, Palette.hero(context), 28f)
        isClickable = true; isFocusable = true; minimumHeight = px(context, 150f)
    }

    private fun formatPercent(value: Double): String =
        if (kotlin.math.abs(value - value.toInt().toDouble()) < 0.05) String.format(Locale.US, "%.0f", value)
        else String.format(Locale.US, "%.1f", value)
}

object BentoCard {
    fun bind(context: Context, root: LinearLayout, labelView: TextView, valueView: TextView, captionView: TextView,
             label: String, value: String, caption: String, surface: Int, loading: Boolean, valueSizeSp: Float = 38f) {
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(context, 16f), px(context, 14f), px(context, 16f), px(context, 14f))
        root.background = roundRect(context, surface, 24f)
        root.minHeight = px(context, 88f)
        labelView.text = label; labelView.textSize = 12f; labelView.setTextColor(Palette.ink2(context))
        labelView.typeface = regularTypeface(context); labelView.includeFontPadding = false
        valueView.text = value.ifBlank { "—" }; valueView.textSize = valueSizeSp
        valueView.setTextColor(Palette.ink(context)); valueView.typeface = mediumTypeface(context)
        valueView.includeFontPadding = false; valueView.setFontFeatureSettings("tnum")
        valueView.letterSpacing = if (valueSizeSp >= 35f) -0.025f else -0.01f
        valueView.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        captionView.text = caption; captionView.textSize = 12f; captionView.setTextColor(Palette.ink2(context))
        captionView.typeface = regularTypeface(context); captionView.includeFontPadding = false
        captionView.visibility = if (loading || caption.isBlank()) View.GONE else View.VISIBLE
        var skeleton: View? = null
        for (index in 0 until root.childCount) if (root.getChildAt(index).tag == "pachattar_bento_skeleton") skeleton = root.getChildAt(index)
        val skeletonView = skeleton ?: View(context).apply {
            tag = "pachattar_bento_skeleton"; background = roundRect(context, Color.argb(65, 80, 100, 88), 8f)
            layoutParams = LinearLayout.LayoutParams(px(context, 74f), px(context, 17f)).apply { topMargin = px(context, 7f) }
            root.addView(this)
        }
        skeletonView.visibility = if (loading) View.VISIBLE else View.GONE
    }
}

object IconBox {
    fun create(context: Context, iconRes: Int, tint: Int = Palette.ink(context), sizeDp: Int = 24): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(px(context, sizeDp.toFloat()), px(context, sizeDp.toFloat()))
            addView(ImageView(context).apply {
                setImageResource(iconRes); imageTintList = ColorStateList.valueOf(tint)
                scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = null
            }, FrameLayout.LayoutParams(px(context, sizeDp.toFloat()), px(context, sizeDp.toFloat()), Gravity.CENTER))
        }
}

object ListCard {
    fun bind(context: Context, root: LinearLayout, iconRes: Int, label: String) {
        root.removeAllViews(); root.orientation = LinearLayout.VERTICAL; root.gravity = Gravity.CENTER
        root.setPadding(px(context, 2f), px(context, 10f), px(context, 2f), px(context, 10f))
        root.minimumHeight = px(context, 68f); root.background = outlinedCard(context, Palette.card(context), 20f)
        root.isClickable = true; root.isFocusable = true; root.contentDescription = label
        root.addView(IconBox.create(context, iconRes, Palette.ink(context), 22))
        root.addView(TextView(context).apply {
            text = label; textSize = 11f; setTextColor(Palette.ink(context)); typeface = mediumTypeface(context)
            gravity = Gravity.CENTER; includeFontPadding = false; maxLines = 1; setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(context, 4f) }
        })
    }
}

object BackHeader {
    fun create(context: Context, title: String, onBack: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(px(context, 8f), px(context, 4f), px(context, 16f), px(context, 4f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_pt_arrow_left); imageTintList = ColorStateList.valueOf(Palette.ink(context))
            contentDescription = "Back"; isClickable = true; isFocusable = true; setOnClickListener { onBack() }
            setPadding(px(context, 12f), px(context, 12f), px(context, 12f), px(context, 12f))
        }, LinearLayout.LayoutParams(px(context, 48f), px(context, 48f)))
        addView(TextView(context).apply {
            text = title; textSize = 18f; setTextColor(Palette.ink(context)); typeface = mediumTypeface(context); includeFontPadding = false
        })
    }
}

data class FloatingNavItem(val view: LinearLayout, val iconRes: Int, val label: String, val selected: Boolean)

object FloatingNav {
    fun apply(context: Context, root: LinearLayout, items: List<FloatingNavItem>) {
        root.removeAllViews(); root.orientation = LinearLayout.HORIZONTAL; root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(px(context, 7f), px(context, 5f), px(context, 7f), px(context, 5f))
        root.background = roundRect(context, Palette.nav(context), 999f); root.elevation = px(context, 12f).toFloat()
        root.clipChildren = false
        (root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = px(context, 58f); params.marginStart = px(context, 14f); params.marginEnd = px(context, 14f)
            params.bottomMargin = px(context, 16f); root.layoutParams = params
        }
        items.forEachIndexed { index, item ->
            val navItem = item.view
            navItem.removeAllViews(); navItem.orientation = LinearLayout.HORIZONTAL; navItem.gravity = Gravity.CENTER
            navItem.isClickable = true; navItem.isFocusable = true; navItem.contentDescription = item.label
            navItem.background = if (item.selected) roundRect(context, Palette.lime(context), 999f) else ColorDrawable(Color.TRANSPARENT)
            navItem.setPadding(if (item.selected) px(context, 12f) else 0, 0, if (item.selected) px(context, 16f) else 0, 0)
            navItem.addView(ImageView(context).apply {
                setImageResource(item.iconRes)
                imageTintList = ColorStateList.valueOf(if (item.selected) Palette.limeInk(context) else Palette.navIcon(context))
                contentDescription = null; scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(px(context, if (item.selected) 20f else 22f), px(context, if (item.selected) 20f else 22f)))
            navItem.addView(TextView(context).apply {
                text = item.label; textSize = 13f
                setTextColor(if (item.selected) Palette.limeInk(context) else Palette.navIcon(context))
                typeface = mediumTypeface(context); includeFontPadding = false
                visibility = if (item.selected) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (item.selected) marginStart = px(context, 6f)
                }
            })
            navItem.layoutParams = LinearLayout.LayoutParams(if (item.selected) ViewGroup.LayoutParams.WRAP_CONTENT else px(context, 48f), px(context, 48f))
            root.addView(navItem)
            if (index < items.lastIndex) root.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        }
    }
}

object Skeleton {
    fun hero(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(px(context, 20f), px(context, 20f), px(context, 20f), px(context, 20f))
        background = roundRect(context, Palette.hero(context), 28f); minimumHeight = px(context, 204f)
        val muted = Color.argb(60, Color.red(Palette.heroInk(context)), Color.green(Palette.heroInk(context)), Color.blue(Palette.heroInk(context)))
        fun line(widthDp: Float, heightDp: Float, topDp: Float = 0f): View = View(context).apply {
            background = roundRect(context, muted, 8f)
            layoutParams = LinearLayout.LayoutParams(px(context, widthDp), px(context, heightDp)).apply { topMargin = px(context, topDp) }
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(line(90f, 12f))
            addView(line(78f, 22f).apply {
                layoutParams = LinearLayout.LayoutParams(px(context, 78f), px(context, 22f)).apply { marginStart = px(context, 110f) }
            })
        })
        addView(line(132f, 48f, 18f)); addView(line(132f, 13f, 9f))
        addView(line(260f, 12f, 14f), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(context, 12f)).apply { topMargin = px(context, 14f) })
        addView(line(162f, 11f, 9f))
    }
}

object EmptyState {
    fun create(context: Context, title: String, message: String): LinearLayout =
        stateCard(context, title, message, Palette.card(context), Palette.ink(context), Palette.ink2(context))
}
object ErrorState {
    fun create(context: Context, title: String, message: String): LinearLayout =
        stateCard(context, title, message, Palette.badBg(context), Palette.bad(context), Palette.ink(context))
}
private fun stateCard(context: Context, title: String, message: String, backgroundColor: Int, titleColor: Int, bodyColor: Int): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(px(context, 16f), px(context, 16f), px(context, 16f), px(context, 16f))
        background = outlinedCard(context, backgroundColor, 20f)
        addView(TextView(context).apply { text = title; textSize = 15f; setTextColor(titleColor); typeface = mediumTypeface(context); includeFontPadding = false })
        addView(TextView(context).apply {
            text = message; textSize = 12f; setTextColor(bodyColor); typeface = regularTypeface(context); includeFontPadding = false
            setPadding(0, px(context, 6f), 0, 0)
        })
    }
