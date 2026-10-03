package com.superiorstudent.app

import java.util.Locale

/**
 * Pure text/number parsing helpers extracted from MainActivity so they can be
 * unit-tested without any Android framework.
 */
object ParsingUtils {

    fun cleanDisplayText(value: String): String = value
        .replace(Regex("(?i)\\bERP\\b"), "")
        .replace(Regex("(?i)\\bOdoo\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun isUsefulDisplayText(value: String): Boolean {
        val text = value.trim()
        if (text.length < 2) return false
        if (text.equals("home", true) || text.equals("logout", true)) return false
        if (text.equals("dashboard", true) || text.equals("menu", true)) return false
        if (text.contains("your session", true) || text.contains("you've been inactive", true)) return false
        if (text.contains("stay online", true) || text.contains("session will expire", true)) return false
        if (text.equals("attendance", true) || text.equals("attendance classes", true) ||
            text.equals("active classes", true)
        ) return false
        return true
    }

    fun isValidStudentName(value: String): Boolean {
        val text = value.trim()
        return text.isNotBlank() &&
            text.length >= 2 &&
            !text.contains("session", true) &&
            !text.contains("expire", true) &&
            !text.contains("dashboard", true) &&
            !text.contains("welcome", true) &&
            !text.contains("student information", true) &&
            !text.matches(Regex("SU\\d+[-A-Z0-9]*", RegexOption.IGNORE_CASE))
    }

    /** Parses "87%", "87.5 %", or a bare number in 0..100; null otherwise. */
    fun parsePercent(value: String): Double? {
        val withSymbol = Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""")
            .find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (withSymbol != null) return withSymbol.coerceIn(0.0, 100.0)
        return value.trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    }

    /** Extracts the numeric count that follows a label, e.g. "Present 42" -> "42". */
    fun extractCountNearLabel(text: String, vararg labels: String): String {
        for (label in labels) {
            val pattern = "(?i)\\b" + Regex.escape(label) + "\\b\\s*[:\\-]?\\s*(\\d+(?:\\.\\d+)?)"
            val match = Regex(pattern).find(text)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    /** Finds a GPA value (0.00-4.00) next to a label such as CGPA/SGPA, in either order. */
    fun findGpaValue(text: String, label: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ""

        val escapedLabel = Regex.escape(label)
        val after = Regex(
            """\b$escapedLabel\b\s*[:\-]?\s*([0-4](?:\.\d{1,2})?)\b""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (after != null) return validGpa(after.groupValues[1])

        val before = Regex(
            """([0-4](?:\.\d{1,2})?)\s*\b$escapedLabel\b""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (before != null) return validGpa(before.groupValues[1])

        return ""
    }

    /** Normalises a GPA string to two decimals (trailing zeros trimmed); "" if out of range. */
    fun validGpa(value: String): String {
        val number = value.trim().toDoubleOrNull() ?: return ""
        if (number !in 0.0..4.0) return ""
        return String.format(Locale.US, "%.2f", number).trimEnd('0').trimEnd('.')
    }

    /** Minutes since midnight for the first HH:mm found in [value]; 9999 when absent. */
    fun timeSortKey(value: String): Int = startMinutesOf(value) ?: 9999

    /** Minutes since midnight for the first HH:mm found in [value]; null when absent. */
    fun startMinutesOf(value: String): Int? {
        val match = Regex("""([01]?\d|2[0-3]):([0-5]\d)""").find(value) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }

    /** Formats "13:30 - 15:00" as "01:30 PM\n–\n03:00 PM"; returns input fallback when unparseable. */
    fun formatScheduleTime(value: String): String {
        val match = Regex("""([01]?\d|2[0-3]):([0-5]\d)(?:\s*[-–]\s*([01]?\d|2[0-3]):([0-5]\d))?""").find(value)
            ?: return value.ifBlank { "Time" }

        fun format(hour: Int, minute: Int): String {
            val suffix = if (hour >= 12) "PM" else "AM"
            val h = when (val twelve = hour % 12) { 0 -> 12; else -> twelve }
            return String.format(Locale.US, "%02d:%02d %s", h, minute, suffix)
        }

        val start = format(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        val end = if (match.groupValues[3].isNotBlank()) {
            format(match.groupValues[3].toInt(), match.groupValues[4].toInt())
        } else ""
        return if (end.isNotBlank()) start + "\n–\n" + end else start
    }

    /** True when [label] matches (fuzzily) any of the given [candidates]. */
    fun isProfileLabel(label: String, vararg candidates: String): Boolean {
        val normalized = normalizeLabel(label)
        return candidates.any { candidate ->
            val target = normalizeLabel(candidate)
            target.isNotEmpty() && (normalized == target || normalized.contains(target))
        }
    }

    private fun normalizeLabel(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Decodes the JSON-string result returned by WebView.evaluateJavascript. */
    fun decodeJavascriptString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return try {
            org.json.JSONObject("{\"value\":$value}").optString("value", "")
        } catch (_: Exception) {
            ""
        }
    }
}
