package com.superiorstudent.app.feature.fees.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.InvoiceData
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.ui.Palette
import com.superiorstudent.app.ui.components.Chip
import com.superiorstudent.app.ui.components.ChipTone
import com.superiorstudent.app.ui.components.EmptyState
import com.superiorstudent.app.ui.components.HeroCard
import com.superiorstudent.app.ui.components.IconBox
import com.superiorstudent.app.ui.components.ListCard
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** ERP-backed invoice history; the existing PDF action and paid/unpaid filters remain. */
class FeeRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val metricCardFactory: (String, String) -> View,
    private val emptyStateFactory: (String, String) -> View,
    private val onDownloadInvoice: (String, String) -> Unit,
    private val onRetry: () -> Unit = {}
) {
    private val regularTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)

    private val mediumTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private enum class Filter { ALL, UNPAID, PAID }
    private var selectedFilter = Filter.ALL

    /** Header metadata is shown only when the ERP provides a single unambiguous term. */
    fun headerSubtitle(data: ModuleData): String? {
        val invoices = data.invoices.ifEmpty { fallbackInvoices(data) }
        val terms = invoices.map { it.term.trim() }.filter(String::isNotBlank).distinct()
        val semesters = invoices.map { it.semester.trim() }.filter(String::isNotBlank).distinct()
        val parts = mutableListOf<String>()
        if (terms.size == 1) parts.add(terms.first())
        if (semesters.size == 1 && semesters.first().lowercase(Locale.US) !in parts.map { it.lowercase(Locale.US) }) {
            parts.add(semesters.first())
        }
        return parts.joinToString(" · ").ifBlank { null }
    }

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val invoices = data.invoices.ifEmpty { fallbackInvoices(data) }.take(100)
        if (invoices.isEmpty()) {
            val cards = data.cards.filter { it.first.isNotBlank() && it.second.isNotBlank() }
            if (cards.isNotEmpty()) {
                val details = ListCard.container(activity)
                cards.take(8).forEachIndexed { index, pair ->
                    if (index > 0) details.addView(ListCard.divider(activity))
                    details.addView(ListCard.keyValueRow(activity, pair.first, pair.second))
                }
                container.addView(details)
            }
            container.addView(EmptyState.create(
                activity,
                "No invoices found",
                "The ERP did not return any invoice rows. Refresh to try again."
            ))
            applyTypography(container)
            return
        }

        // Keep the pre-existing outstanding-balance calculation unchanged.
        val paid = invoices.count { it.status.equals("paid", true) }
        val unpaid = invoices.count { it.status.equals("unpaid", true) }
        val outstanding = invoices.sumOf { invoice ->
            parseAmount(invoice.payable)
                ?: if (invoice.status.equals("unpaid", true)) parseAmount(invoice.totalAmount) ?: 0.0 else 0.0
        }
        val allPaid = unpaid == 0 && outstanding <= 0.0 && invoices.all {
            it.status.equals("paid", true) ||
                parseAmount(it.payable)?.let { value -> value <= 0.0 } == true ||
                parseAmount(it.totalAmount)?.let { value -> value <= 0.0 } == true
        }
        val dueInvoices = invoices.filter { it.status.equals("unpaid", true) || it.status.contains("overdue", true) }
        val hasOverdue = dueInvoices.any(::isOverdue)
        val summaryStatus = when {
            allPaid -> "All paid"
            hasOverdue -> "Overdue"
            else -> "Due soon"
        }
        val summaryTone = when {
            allPaid -> ChipTone.OK
            hasOverdue -> ChipTone.BAD
            else -> ChipTone.WARN
        }
        val amountComplete = dueInvoices.isNotEmpty() && dueInvoices.all { invoice ->
            parseAmount(invoice.payable) != null || parseAmount(invoice.totalAmount) != null
        }
        val amountText = if (!allPaid && amountComplete && outstanding > 0.0) money(outstanding) else null
        val nextDue = dueInvoices.mapNotNull { invoice ->
            parseDate(invoice.dueDate)?.let { parsed -> parsed to dateLabel(parsed) }
        }.minByOrNull { it.first }?.second

        container.addView(HeroCard.invoiceSummary(
            activity, amountText, summaryStatus, summaryTone, nextDue, allPaid
        ))
        container.addView(sectionHeadingFactory("History", ""))
        container.addView(createFilters(container, data, invoices, paid, unpaid))

        val shown = when (selectedFilter) {
            Filter.ALL -> invoices
            Filter.UNPAID -> invoices.filter { it.status.equals("unpaid", true) || it.status.contains("overdue", true) }
            Filter.PAID -> invoices.filter { it.status.equals("paid", true) }
        }
        if (shown.isEmpty()) {
            container.addView(EmptyState.create(
                activity,
                if (selectedFilter == Filter.PAID) "No paid invoices" else "No unpaid invoices",
                "There are no invoices in this category in the latest ERP response."
            ))
        } else {
            val list = ListCard.container(activity)
            shown.forEachIndexed { index, invoice ->
                if (index > 0) list.addView(ListCard.divider(activity))
                list.addView(createInvoiceRow(invoice))
            }
            container.addView(list)
        }
        applyTypography(container)
    }

    private fun createFilters(
        container: LinearLayout,
        data: ModuleData,
        invoices: List<InvoiceData>,
        paid: Int,
        unpaid: Int
    ): View = HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setPadding(dp(12), dp(1), dp(12), dp(5))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
            text = "$label · $count"
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = mediumTypeface
            includeFontPadding = false
            minHeight = dp(48)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTextColor(if (selectedFilter == filter) Palette.segmentInk(activity) else Palette.ink(activity))
            background = rounded(
                if (selectedFilter == filter) Palette.segment(activity) else Palette.card(activity),
                999f,
                if (selectedFilter == filter) null else Palette.stroke(activity)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
            ).apply { setMargins(dp(3), dp(2), dp(5), dp(2)) }
            isClickable = true
            isFocusable = true
            contentDescription = "$label invoices, $count"
            setOnClickListener { action() }
        }

    private fun createInvoiceRow(invoice: InvoiceData): View {
        val paid = invoice.status.equals("paid", true)
        val overdue = !paid && isOverdue(invoice)
        val hasPdf = invoice.downloadUrl.isNotBlank()
        val statusParts = when {
            paid -> Quad("Paid", ChipTone.OK, Palette.okBg(activity), Palette.ok(activity))
            overdue -> Quad("Overdue", ChipTone.BAD, Palette.badBg(activity), Palette.bad(activity))
            invoice.status.equals("unpaid", true) || invoice.status.contains("due", true) ->
                Quad("Due", ChipTone.WARN, Palette.warnBg(activity), Palette.warn(activity))
            else -> Quad(
                invoice.status.trim().takeIf(String::isNotBlank) ?: "Status unavailable",
                ChipTone.NEUTRAL, Palette.lav(activity), Palette.ink2(activity)
            )
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(12), dp(14))
            minimumHeight = dp(76)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            addView(IconBox.tinted(
                activity, R.drawable.ic_pt_receipt, statusParts.background, statusParts.tint, 40, 14f
            ).apply {
                contentDescription = "Invoice receipt"
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
            })

            val title = invoice.receiptFor.trim().ifBlank { invoice.number.trim() }
            val titleAndDate = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(Palette.ink(activity))
                    typeface = mediumTypeface
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val dateLine = buildList {
                    if (invoice.dueDate.isNotBlank()) add("Due " + readableDate(invoice.dueDate))
                    else if (invoice.invoiceDate.isNotBlank()) add(readableDate(invoice.invoiceDate))
                    if (invoice.invoiceDate.isNotBlank() && invoice.dueDate.isNotBlank()) add("Issued " + readableDate(invoice.invoiceDate))
                }.joinToString(" · ")
                if (dateLine.isNotBlank()) addView(TextView(activity).apply {
                    text = dateLine
                    textSize = 11f
                    setTextColor(Palette.ink2(activity))
                    typeface = regularTypeface
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
                val termLine = listOf(invoice.term.trim(), invoice.semester.trim())
                    .filter(String::isNotBlank).distinct().joinToString(" · ")
                if (termLine.isNotBlank()) addView(TextView(activity).apply {
                    text = termLine
                    textSize = 10f
                    setTextColor(Palette.ink2(activity))
                    typeface = regularTypeface
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                if (invoice.number.isNotBlank() && invoice.receiptFor.isNotBlank()) addView(TextView(activity).apply {
                    text = invoice.number
                    textSize = 10f
                    setTextColor(Palette.ink3(activity))
                    typeface = regularTypeface
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                if (invoice.barcode.isNotBlank()) addView(TextView(activity).apply {
                    text = "Reference: " + invoice.barcode
                    textSize = 10f
                    setTextColor(Palette.ink3(activity))
                    typeface = regularTypeface
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                if (hasPdf) addView(TextView(activity).apply {
                    text = "Download invoice PDF"
                    textSize = 11f
                    setTextColor(Palette.primaryLight(activity))
                    typeface = mediumTypeface
                    includeFontPadding = false
                    minHeight = dp(48)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), dp(8), 0)
                    contentDescription = "Download invoice PDF " + invoice.number
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onDownloadInvoice(invoice.downloadUrl, invoice.number) }
                }) else addView(TextView(activity).apply {
                    text = "PDF unavailable in ERP"
                    textSize = 10f
                    setTextColor(Palette.ink3(activity))
                    typeface = regularTypeface
                    includeFontPadding = false
                    setPadding(0, dp(5), 0, 0)
                })
            }
            addView(titleAndDate, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val amountColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                val primaryAmount = invoice.payable.trim().takeIf(String::isNotBlank)
                    ?: invoice.totalAmount.trim()
                formatAmount(primaryAmount)?.let { amountText ->
                    addView(TextView(activity).apply {
                        text = amountText
                        textSize = 14f
                        setTextColor(Palette.ink(activity))
                        typeface = mediumTypeface
                        includeFontPadding = false
                        setFontFeatureSettings("tnum")
                        gravity = Gravity.END
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                val total = formatAmount(invoice.totalAmount)
                val payable = formatAmount(invoice.payable)
                if (total != null && payable != null && total != payable) {
                    addView(TextView(activity).apply {
                        text = "Total $total"
                        textSize = 9f
                        setTextColor(Palette.ink2(activity))
                        typeface = regularTypeface
                        includeFontPadding = false
                        gravity = Gravity.END
                        setPadding(0, dp(3), 0, 0)
                    })
                }
                addView(Chip.create(activity, statusParts.status, statusParts.tone).apply {
                    setPadding(dp(9), dp(5), dp(9), dp(5))
                    minHeight = dp(28)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
                })
            }
            addView(amountColumn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) })
            contentDescription = buildString {
                append(title)
                if (invoice.number.isNotBlank()) append(". Invoice " + invoice.number)
                if (invoice.dueDate.isNotBlank()) append(". Due " + invoice.dueDate)
                append(". " + statusParts.status)
            }
        }
    }

    private data class Quad(
        val status: String,
        val tone: ChipTone,
        val background: Int,
        val tint: Int
    )

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

    private fun isOverdue(invoice: InvoiceData): Boolean {
        if (invoice.status.contains("overdue", true)) return true
        if (!invoice.status.equals("unpaid", true)) return false
        val dueDate = parseDate(invoice.dueDate) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return dueDate.before(today)
    }

    private fun parseAmount(value: String): Double? =
        value.replace(",", "").replace(Regex("(?i)\\b(?:PKR|RS\\.?|RUPEES)\\s*"), "")
            .trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun formatAmount(value: String): String? {
        if (value.isBlank()) return null
        return parseAmount(value)?.let(::money) ?: value.trim()
    }

    private fun money(value: Double): String =
        "Rs " + NumberFormat.getNumberInstance(Locale.US).format(value)

    private fun parseDate(value: String): Date? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd", "dd MMM yyyy", "d MMM yyyy", "dd/MM/yyyy",
            "d/M/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd-MM-yyyy"
        )
        for (format in formats) {
            try {
                val parser = SimpleDateFormat(format, Locale.US).apply { isLenient = false }
                val position = ParsePosition(0)
                val parsed = parser.parse(clean, position)
                if (parsed != null && position.index == clean.length) return parsed
            } catch (_: Exception) { }
        }
        return null
    }

    private fun dateLabel(date: Date): String = SimpleDateFormat("d MMM yyyy", Locale.US).format(date)
    private fun readableDate(value: String): String = parseDate(value)?.let(::dateLabel) ?: value.trim()

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.roundToInt()).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun applyTypography(view: View) {
        when (view) {
            is TextView -> {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = if (style and Typeface.BOLD != 0) Typeface.create(mediumTypeface, Typeface.BOLD) else regularTypeface
                view.includeFontPadding = false
            }
            is ViewGroup -> for (index in 0 until view.childCount) applyTypography(view.getChildAt(index))
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
