package com.superiorstudent.app.feature.fees.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.data.model.InvoiceData
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.ui.Palette
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Branded, ERP-backed invoice listing with paid/unpaid filters and PDF actions. */
class FeeRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val metricCardFactory: (String, String) -> View,
    private val emptyStateFactory: (String, String) -> View,
    private val onDownloadInvoice: (String, String) -> Unit
) {
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

    private enum class Filter { ALL, UNPAID, PAID }
    private var selectedFilter = Filter.ALL

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val invoices = data.invoices.ifEmpty { fallbackInvoices(data) }.take(100)
        if (invoices.isEmpty()) {
            data.cards.take(4).forEach { (label, value) ->
                container.addView(metricCardFactory(label, value))
            }
            container.addView(emptyStateFactory(
                "No invoices found",
                "The ERP did not return invoice rows. Tap Refresh to try again."
            ))
            return
        }

        val paid = invoices.count { it.status.equals("paid", true) }
        val unpaid = invoices.count { it.status.equals("unpaid", true) }
        val outstanding = invoices.sumOf { invoice ->
            parseAmount(invoice.payable)
                ?: if (invoice.status.equals("unpaid", true)) parseAmount(invoice.totalAmount) ?: 0.0 else 0.0
        }

        container.addView(createBalanceCard(outstanding, unpaid, invoices.size))
        container.addView(createCounts(paid, unpaid))
        container.addView(sectionHeadingFactory("Your invoices", "Paid and unpaid vouchers from your student account"))
        container.addView(createFilters(container, data, invoices, paid, unpaid))

        val shown = when (selectedFilter) {
            Filter.ALL -> invoices
            Filter.UNPAID -> invoices.filter { it.status.equals("unpaid", true) }
            Filter.PAID -> invoices.filter { it.status.equals("paid", true) }
        }
        if (shown.isEmpty()) {
            container.addView(emptyStateFactory(
                if (selectedFilter == Filter.PAID) "No paid invoices" else "No unpaid invoices",
                "There are no invoices in this category in the latest ERP response."
            ))
        } else {
            shown.forEach { container.addView(createInvoiceCard(it)) }
        }
    }

    private fun createBalanceCard(balance: Double, unpaid: Int, total: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradient(Color.rgb(30, 64, 175), Color.rgb(79, 70, 229), 22f)
            elevation = dp(3).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "OUTSTANDING BALANCE"
                    setTextColor(Color.rgb(224, 231, 255))
                    textSize = 10f
                    letterSpacing = 0.08f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = money(balance)
                    setTextColor(Color.WHITE)
                    textSize = 25f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(5), 0, dp(3))
                })
                addView(TextView(activity).apply {
                    text = when (unpaid) {
                        0 -> "No unpaid invoices · $total total"
                        1 -> "1 unpaid invoice · $total total"
                        else -> "$unpaid unpaid invoices · $total total"
                    }
                    setTextColor(Color.rgb(224, 231, 255))
                    textSize = 11f
                })
            })
            addView(TextView(activity).apply {
                text = "▤"
                gravity = Gravity.CENTER
                textSize = 36f
                setTextColor(Color.WHITE)
                background = rounded(Color.argb(38, 255, 255, 255), 16f)
                layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            })
        }

    private fun createCounts(paid: Int, unpaid: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(9), dp(1), dp(9), dp(5)) }
            addView(countCard("Paid", paid, Palette.success(activity), Palette.successBg(activity)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(4), dp(3))
                })
            addView(countCard("Unpaid", unpaid, Palette.warning(activity), Palette.warningBg(activity)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(3), dp(3), dp(3))
                })
        }

    private fun countCard(label: String, count: Int, color: Int, tint: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tint, 16f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(activity).apply {
                text = label
                setTextColor(Palette.ink700(activity))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = count.toString()
                setTextColor(color)
                textSize = 23f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(2), 0, 0)
            })
            addView(TextView(activity).apply {
                text = if (count == 1) "invoice" else "invoices"
                setTextColor(Palette.ink500(activity))
                textSize = 10f
            })
        }

    private fun createFilters(
        container: LinearLayout,
        data: ModuleData,
        invoices: List<InvoiceData>,
        paid: Int,
        unpaid: Int
    ): View = HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(8), dp(1), dp(8), dp(7)) }
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(filterChip("All", invoices.size, Filter.ALL) {
                selectedFilter = Filter.ALL
                render(container, data)
            })
            addView(filterChip("Unpaid", unpaid, Filter.UNPAID) {
                selectedFilter = Filter.UNPAID
                render(container, data)
            })
            addView(filterChip("Paid", paid, Filter.PAID) {
                selectedFilter = Filter.PAID
                render(container, data)
            })
        })
    }

    private fun filterChip(label: String, count: Int, filter: Filter, action: () -> Unit): View =
        TextView(activity).apply {
            text = "$label  $count"
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = rounded(
                if (selectedFilter == filter) Color.rgb(79, 70, 229) else Palette.surfaceAlt(activity), 22f
            )
            setTextColor(if (selectedFilter == filter) Color.WHITE else Palette.ink700(activity))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun createInvoiceCard(invoice: InvoiceData): View {
        val paid = invoice.status.equals("paid", true)
        val unpaid = invoice.status.equals("unpaid", true)
        val status = when {
            paid -> "PAID"
            unpaid -> "UNPAID"
            invoice.status.isNotBlank() -> invoice.status.uppercase(Locale.US)
            else -> "STATUS UNKNOWN"
        }
        val statusColor = when {
            paid -> Palette.success(activity)
            unpaid -> Palette.warning(activity)
            else -> Palette.ink500(activity)
        }
        val statusBg = when {
            paid -> Palette.successBg(activity)
            unpaid -> Palette.warningBg(activity)
            else -> Palette.surfaceAlt(activity)
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 19f)
            elevation = dp(2).toFloat()
            setPadding(dp(15), dp(14), dp(15), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(activity).apply {
                        text = "INVOICE NUMBER"
                        setTextColor(Palette.ink500(activity))
                        textSize = 9f
                        letterSpacing = 0.04f
                    })
                    addView(TextView(activity).apply {
                        text = invoice.number
                        setTextColor(Palette.ink900(activity))
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(2), 0, 0)
                        maxLines = 3
                    })
                })
                addView(TextView(activity).apply {
                    text = status
                    setTextColor(statusColor)
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = rounded(statusBg, 11f)
                    setPadding(dp(9), dp(6), dp(9), dp(6))
                })
            })
            if (invoice.receiptFor.isNotBlank()) addView(TextView(activity).apply {
                text = invoice.receiptFor.replace(Regex("\\s+"), " ").trim()
                setTextColor(Palette.ink700(activity))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(9), 0, dp(2))
                maxLines = 3
            })

            val termLine = listOf(invoice.term, invoice.semester.takeIf { it.isNotBlank() }?.let { "$it semester" })
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            if (termLine.isNotBlank()) addView(TextView(activity).apply {
                text = termLine
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(0, dp(2), 0, dp(8))
            })

            val dates = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(9), 0, 0)
            }
            if (invoice.invoiceDate.isNotBlank()) addValue(dates, "Invoice date", date(invoice.invoiceDate))
            if (invoice.dueDate.isNotBlank()) addValue(dates, "Due date", date(invoice.dueDate))
            if (dates.childCount > 0) addView(dates)

            val amounts = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            addValue(amounts, "Invoice total", amount(invoice.totalAmount))
            addValue(amounts, if (unpaid) "Payable now" else "Remaining",
                amount(invoice.payable.ifBlank { if (paid) "0" else invoice.totalAmount }))
            addView(amounts)

            if (invoice.barcode.isNotBlank()) addView(TextView(activity).apply {
                text = "Reference: ${invoice.barcode}"
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(0, dp(9), 0, 0)
            })

            val hasPdf = invoice.downloadUrl.isNotBlank()
            addView(TextView(activity).apply {
                text = if (hasPdf) "↓   Download invoice PDF" else "PDF unavailable in ERP"
                gravity = Gravity.CENTER
                setTextColor(if (hasPdf) Color.rgb(67, 56, 202) else Palette.ink500(activity))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = rounded(if (hasPdf) Palette.infoBg(activity) else Palette.surfaceAlt(activity), 13f)
                setPadding(dp(12), dp(13), dp(12), dp(13))
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(13), 0, 0) }
                isClickable = hasPdf
                isFocusable = hasPdf
                setOnClickListener { if (hasPdf) onDownloadInvoice(invoice.downloadUrl, invoice.number) }
            })
        }
    }

    private fun addValue(container: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        container.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(activity).apply {
                text = label
                setTextColor(Palette.ink500(activity))
                textSize = 10f
            })
            addView(TextView(activity).apply {
                text = value
                setTextColor(Palette.ink900(activity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, 0)
            })
        })
    }

    private fun fallbackInvoices(data: ModuleData): List<InvoiceData> {
        val table = data.tables.firstOrNull {
            it.headers.any { h -> h.equals("Invoice No", true) } &&
                it.headers.any { h -> h.equals("Status", true) }
        } ?: return emptyList()
        val indexes = table.headers.mapIndexed { index, header -> header.lowercase(Locale.US) to index }.toMap()
        fun value(row: List<String>, key: String): String =
            indexes[key.lowercase(Locale.US)]?.let { row.getOrNull(it).orEmpty() }.orEmpty()
        return table.rows.map { row ->
            InvoiceData(
                number = value(row, "Invoice No"),
                invoiceDate = value(row, "Invoice Date"),
                dueDate = value(row, "Due Date"),
                term = value(row, "Term"),
                semester = value(row, "Semester"),
                receiptFor = value(row, "Receipt For"),
                barcode = value(row, "Barcode"),
                totalAmount = value(row, "Total Amount"),
                payable = value(row, "Payable"),
                status = value(row, "Status").lowercase(Locale.US),
                downloadUrl = ""
            )
        }.filter { it.number.isNotBlank() }
    }

    private fun parseAmount(value: String): Double? =
        value.replace(",", "").replace(Regex("(?i)\\b(?:PKR|RS\\.?|RUPEES)\\s*"), "")
            .trim().toDoubleOrNull()

    private fun amount(value: String): String = parseAmount(value)?.let(::money) ?: value.ifBlank { "Not provided" }
    private fun money(value: Double): String =
        "Rs. " + NumberFormat.getNumberInstance(Locale.US).format(value)

    private fun date(value: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = input.parse(value.trim())
            if (parsed == null) value else SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed)
        } catch (_: Exception) {
            value
        }
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun gradient(start: Int, end: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(start, end)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
