package com.superiorstudent.app.feature.fees.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.ui.Palette
import kotlin.math.roundToInt

/**
 * Fee/invoice screen presentation. The host supplies shared design components,
 * while invoice grouping, mapping and cards stay within the fees feature.
 */
class FeeRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val metricCardFactory: (String, String) -> View,
    private val emptyStateFactory: (String, String) -> View
) {
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

    fun render(container: LinearLayout, data: ModuleData) {
        val invoices = data.tables.flatMap { table ->
            table.rows.map { row -> table.headers.zip(row).toMap() }
        }.filter { it.isNotEmpty() }

        if (invoices.isNotEmpty()) {
            container.addView(createFeeSummary(invoices.size))
            container.addView(sectionHeadingFactory("Invoice history", "Recent fee records from your student account"))
            invoices.take(30).forEach { invoice -> container.addView(createInvoiceCard(invoice)) }
        } else {
            data.cards.take(4).forEach { (label, value) -> container.addView(metricCardFactory(label, value)) }
            if (data.cards.isEmpty()) container.addView(
                emptyStateFactory("Fee information is unavailable", "Refresh to sync your latest fee records.")
            )
        }
    }

    private fun createFeeSummary(count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(6), dp(12), dp(7)) }
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "Fee records"
                    setTextColor(Palette.ink900(activity))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = "Your latest invoices"
                    setTextColor(Palette.ink500(activity))
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            addView(TextView(activity).apply {
                text = count.toString()
                gravity = Gravity.CENTER
                setTextColor(Palette.primary(activity))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(activity), 12f)
                minWidth = dp(44)
                minHeight = dp(38)
            })
        }

    private fun findMapValue(map: Map<String, String>, vararg keys: String): String {
        val normalized = map.mapKeys { it.key.trim().lowercase() }
        for (key in keys) {
            val exact = normalized.entries.firstOrNull { it.key == key.lowercase() }
            if (exact != null) return exact.value
        }
        for (key in keys) {
            val partial = normalized.entries.firstOrNull { it.key.contains(key.lowercase()) }
            if (partial != null) return partial.value
        }
        return ""
    }

    private fun createInvoiceCard(invoice: Map<String, String>): View {
        val no = findMapValue(invoice, "invoice no", "invoice number", "number", "no").ifBlank { "Invoice" }
        val date = findMapValue(invoice, "invoice date", "date")
        val due = findMapValue(invoice, "due date", "due")
        val amount = findMapValue(invoice, "amount", "total", "balance")
        val status = findMapValue(invoice, "status", "state")

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = no
                    setTextColor(Palette.ink900(activity))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (status.isNotBlank()) addView(TextView(activity).apply {
                    text = status
                    setTextColor(Palette.success(activity))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Palette.successBg(activity), 10f)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                })
            })
            val details = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            addInvoiceDetail(details, "Invoice date", date)
            addInvoiceDetail(details, "Due date", due)
            if (amount.isNotBlank()) addInvoiceDetail(details, "Amount", amount)
            addView(details)
        }
    }

    private fun addInvoiceDetail(container: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        container.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(activity).apply {
                text = label
                setTextColor(Palette.ink500(activity))
                textSize = 9f
            })
            addView(TextView(activity).apply {
                text = value
                setTextColor(Palette.ink900(activity))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, 0)
            })
        })
    }

    private fun roundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
