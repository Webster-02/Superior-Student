package com.superiorstudent.app.ui

import com.superiorstudent.app.data.model.InvoiceData
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AttendanceBand { SAFE, NEAR_LIMIT, SHORT }

object HomeDisplayRules {
    fun firstName(fullName: String): String = fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

    fun initials(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts.first().first().uppercaseChar().toString() + parts.last().first().uppercaseChar()
            parts.size == 1 -> parts.first().first().uppercaseChar().toString()
            else -> "S"
        }
    }

    fun attendanceBand(percent: Double): AttendanceBand? {
        if (!percent.isFinite() || percent !in 0.0..100.0) return null
        return when {
            percent >= 80.0 -> AttendanceBand.SAFE
            percent >= 75.0 -> AttendanceBand.NEAR_LIMIT
            else -> AttendanceBand.SHORT
        }
    }

    fun parsePayable(raw: String): Double? {
        if (raw.isBlank()) return null
        val normalized = raw.trim()
            .replace(Regex("""(?i)\b(?:rs|pkr|usd|eur|gbp)\.?\s*"""), "")
            .replace(Regex("""[₨₹$€£]"""), "")
            .replace(",", "")
            .replace(Regex("""\s+"""), "")
            .trim()
        val number = normalized.toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() && it > 0.0 }
    }

    fun parseDueDate(raw: String): Date? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "d-M-yyyy",
            "d MMM yyyy", "dd MMM yyyy", "d MMMM yyyy", "dd MMMM yyyy", "MMM d, yyyy", "MMMM d, yyyy",
            "MMM dd, yyyy", "MMMM dd, yyyy")
        for (pattern in patterns) {
            val format = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
            val position = ParsePosition(0)
            val parsed = format.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed
        }
        return null
    }

    fun feeDue(invoices: List<InvoiceData>): InvoiceData? =
        invoices.mapNotNull { invoice ->
            val status = invoice.status.trim().lowercase(Locale.US)
            if (status in setOf("paid", "fully paid", "settled", "closed", "cancelled", "canceled")) return@mapNotNull null
            if (parsePayable(invoice.payable) == null) return@mapNotNull null
            val date = parseDueDate(invoice.dueDate) ?: return@mapNotNull null
            invoice to date
        }.minByOrNull { it.second.time }?.first

    fun isPastDue(raw: String, now: Date = Date()): Boolean {
        val due = parseDueDate(raw) ?: return false
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return dayFormat.format(due) < dayFormat.format(now)
    }

    fun dueDateLabel(raw: String): String? =
        parseDueDate(raw)?.let { SimpleDateFormat("d MMM", Locale.ENGLISH).format(it) }

    fun dueDateFullLabel(raw: String): String? =
        parseDueDate(raw)?.let { SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(it) }

    fun payableLabel(raw: String): String? {
        val amount = parsePayable(raw) ?: return null
        return "Rs. " + DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(amount)
    }
}
