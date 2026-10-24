package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketReportReason
import com.superiorstudent.app.feature.market.data.MarketReportTargetType
import com.superiorstudent.app.feature.market.data.MarketRepository

/**
 * Screen 20: reference-matched report sheet shown over the existing task detail page.
 * Report and optional block actions use the signed-in student's Market repository.
 */
internal object MarketReportSheet {
    fun show(
        activity: AppCompatActivity,
        authorName: String,
        targetType: MarketReportTargetType,
        targetId: String,
        blockedUid: String? = null
    ) {
        val dialog = Dialog(activity)
        var selectedReason = MarketReportUiPolicy.DEFAULT_REASON
        var alsoBlock = !blockedUid.isNullOrBlank()
        val radioMarkers = linkedMapOf<String, FrameLayout>()

        val sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 20))
            background = sheetBackground(activity)
            elevation = dp(activity, 16).toFloat()
        }

        val handle = View(activity).apply {
            background = rounded(activity, color(activity, R.color.market_line), 99f)
        }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(activity, 14)
        })

        sheet.addView(text(activity, MarketReportUiPolicy.TITLE, 18f, true,
            color(activity, R.color.market_ink)), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(activity, 10)
        })

        fun refreshRadioMarkers() {
            radioMarkers.forEach { (reason, marker) ->
                val selected = reason == selectedReason
                marker.background = radioBackground(activity, selected)
                marker.removeAllViews()
                if (selected) {
                    marker.addView(
                        View(activity).apply {
                            background = oval(activity, color(activity, R.color.market_match))
                        },
                        FrameLayout.LayoutParams(dp(activity, 10), dp(activity, 10), Gravity.CENTER)
                    )
                }
                marker.contentDescription = if (selected) "$reason, selected" else reason
            }
        }

        MarketReportUiPolicy.REASONS.forEachIndexed { index, reason ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(activity, 10), 0, dp(activity, 10))
                isClickable = true
                isFocusable = true
                contentDescription = reason
            }
            val marker = FrameLayout(activity)
            radioMarkers[reason] = marker
            row.addView(marker, LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20)))
            row.addView(
                text(activity, reason, 13.5f, false, color(activity, R.color.market_ink)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(activity, 12) }
            )
            row.setOnClickListener {
                selectedReason = reason
                refreshRadioMarkers()
            }
            sheet.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (index < MarketReportUiPolicy.REASONS.lastIndex) {
                sheet.addView(View(activity).apply {
                    setBackgroundColor(color(activity, R.color.market_line))
                }, LinearLayout.LayoutParams(-1, dp(activity, 1)))
            }
        }
        refreshRadioMarkers()

        val blockRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 12), 0, dp(activity, 14))
            isClickable = true
            isFocusable = true
        }
        blockRow.addView(
            text(activity, "Also block ${authorName.ifBlank { "the task owner" }}", 13.5f, false,
                color(activity, R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val switch = FrameLayout(activity).apply {
            isClickable = false
        }
        blockRow.addView(switch, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 26)))
        fun refreshBlockSwitch() {
            switch.background = rounded(
                activity,
                color(activity, if (alsoBlock) R.color.market_match else R.color.market_ink3),
                99f
            )
            switch.removeAllViews()
            val knob = View(activity).apply {
                background = oval(activity, color(activity, R.color.market_match_ink))
            }
            val knobParams = FrameLayout.LayoutParams(
                dp(activity, 20), dp(activity, 20),
                if (alsoBlock) Gravity.END or Gravity.CENTER_VERTICAL
                else Gravity.START or Gravity.CENTER_VERTICAL
            )
            if (alsoBlock) {
                knobParams.rightMargin = dp(activity, 3)
            } else {
                knobParams.leftMargin = dp(activity, 3)
            }
            switch.addView(knob, knobParams)
            switch.contentDescription = if (alsoBlock) "Block selected" else "Block not selected"
            blockRow.contentDescription = "Also block ${authorName.ifBlank { "the task owner" }}: ${if (alsoBlock) "on" else "off"}"
        }
        blockRow.setOnClickListener {
            alsoBlock = !alsoBlock
            refreshBlockSwitch()
        }
        refreshBlockSwitch()
        if (!blockedUid.isNullOrBlank()) {
            sheet.addView(blockRow, LinearLayout.LayoutParams(-1, -2))
        }

        val submitButton = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(activity, color(activity, R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            contentDescription = "Submit report"
            addView(
                TaskDetailIconView(activity, TaskDetailIcon.FLAG, color(activity, R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20)).apply {
                    marginEnd = dp(activity, 8)
                }
            )
            addView(text(activity, "Submit report", 15f, true,
                color(activity, R.color.market_match_ink)))
        }
        submitButton.setOnClickListener {
            val reason = when (selectedReason) {
                MarketReportUiPolicy.REASONS[0] -> MarketReportReason.GRADED_WORK
                MarketReportUiPolicy.REASONS[1] -> MarketReportReason.SCAM
                MarketReportUiPolicy.REASONS[2] -> MarketReportReason.SPAM
                MarketReportUiPolicy.REASONS[3] -> MarketReportReason.ABUSIVE
                else -> MarketReportReason.OTHER
            }
            val uidToBlock = if (alsoBlock) blockedUid?.takeIf { it.isNotBlank() } else null
            submitButton.isEnabled = false
            val repository = MarketRepository()
            val report = try {
                repository.submitReport(targetType, targetId, reason)
            } catch (error: Exception) {
                submitButton.isEnabled = true
                AlertDialog.Builder(activity).setTitle("Report not submitted")
                    .setMessage(error.localizedMessage ?: "The report could not be submitted.")
                    .setPositiveButton("Got it", null).show()
                return@setOnClickListener
            }
            report.addOnCompleteListener { result ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
                if (!result.isSuccessful) {
                    submitButton.isEnabled = true
                    AlertDialog.Builder(activity).setTitle("Report not submitted")
                        .setMessage(result.exception?.localizedMessage ?: "The report could not be submitted.")
                        .setPositiveButton("Got it", null).show()
                    return@addOnCompleteListener
                }
                val blockTask = if (uidToBlock != null) {
                    try { repository.blockUser(uidToBlock) } catch (error: Exception) {
                        dialog.dismiss()
                        AlertDialog.Builder(activity).setTitle("Report submitted")
                            .setMessage("Your report was saved, but blocking could not be completed: " +
                                (error.localizedMessage ?: "please try again from Market settings."))
                            .setPositiveButton("Got it", null).show()
                        return@addOnCompleteListener
                    }
                } else null
                if (blockTask == null) {
                    dialog.dismiss()
                    AlertDialog.Builder(activity).setTitle("Report submitted")
                        .setMessage("Your report was submitted for review.")
                        .setPositiveButton("Got it", null).show()
                } else {
                    blockTask.addOnCompleteListener { blocked ->
                        if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
                        dialog.dismiss()
                        AlertDialog.Builder(activity).setTitle("Report submitted")
                            .setMessage(if (blocked.isSuccessful)
                                "Your report was submitted and this user was blocked."
                            else "Your report was submitted, but the user could not be blocked: " +
                                (blocked.exception?.localizedMessage ?: "please try again from Market settings."))
                            .setPositiveButton("Got it", null).show()
                    }
                }
            }
        }
        sheet.addView(submitButton, LinearLayout.LayoutParams(-1, dp(activity, 52)))

        dialog.setContentView(sheet)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val params = attributes
            params.gravity = Gravity.BOTTOM
            params.dimAmount = 0.45f
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = params
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun text(
        context: Context,
        value: String,
        size: Float,
        medium: Boolean,
        textColor: Int
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        typeface = ResourcesCompat.getFont(context, if (medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setTextColor(textColor)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setLineSpacing(dp(context, 2).toFloat(), 1f)
    }

    private fun sheetBackground(context: Context) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color(context, R.color.market_bg))
        val radius = dp(context, 28f).toFloat()
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    }

    private fun rounded(context: Context, fill: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, radius).toFloat()
        setColor(fill)
    }

    private fun oval(context: Context, fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun radioBackground(context: Context, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        val stroke = if (selected) color(context, R.color.market_match) else color(context, R.color.market_ink3)
        setStroke(dp(context, 2), stroke)
    }

    private fun color(context: Context, id: Int): Int = context.getColor(id)
    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
