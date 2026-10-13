package com.superiorstudent.app.data.parser

import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TimetableRow
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Converts the ERP calendar's per-event records to normalized rows.
 * Prefer the explicit records emitted by the calendar extractor over generic
 * surrounding DOM/text rows; those generic rows can contain several subjects.
 */
object TimetableParser {

    fun toRows(records: List<ModuleRecord>): List<TimetableRow> {
        val explicitRows = records
            .filter(::hasExplicitScheduleHeaders)
            .mapNotNull(::toRow)
            .filter(::isUsefulScheduleRow)
            .distinctBy(::rowKey)

        if (explicitRows.isNotEmpty()) return explicitRows

        return records
            .mapNotNull(::toRow)
            .filter(::isUsefulScheduleRow)
            .distinctBy(::rowKey)
    }

    fun toRow(record: ModuleRecord): TimetableRow? {
        val values = record.values.map(ParsingUtils::cleanDisplayText)
        if (values.none(String::isNotBlank)) return null

        val headers = record.headers.map(ParsingUtils::cleanDisplayText)
        val joined = values.filter(String::isNotBlank).joinToString(" ")
        if (joined.contains("session", true) ||
            joined.contains("inactive", true) ||
            joined.contains("stay online", true)
        ) return null

        fun headerValue(vararg keys: String): String {
            // Prioritize the requested semantic field, not whichever header happens
            // to contain a broad word such as "course".
            for (key in keys) {
                val index = headers.indexOfFirst { it.equals(key, true) || it.contains(key, true) }
                if (index >= 0 && index < values.size) return values[index]
            }
            return ""
        }

        val timeSource = headerValue("start time", "time", "timing", "period")
            .ifBlank { findTimeText(joined) }
        val time = normalizeTimeRange(timeSource)

        val daySource = headerValue("weekday", "day", "date")
            .ifBlank { findDayText(joined) }
        val day = normalizeDay(daySource)

        val codeSource = headerValue("course code", "subject code", "code")
        val code = codeSource.ifBlank {
            Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
                .find(joined)?.value.orEmpty()
        }

        val explicitTitle = headerValue("subject", "course name", "class", "title", "name", "course")
        val titleSource = explicitTitle.ifBlank {
            joined
                .replace(timeSource, " ")
                .replace(daySource, " ")
                .replace(code, " ")
        }
        val course = cleanScheduleTitle(titleSource, code, daySource)

        val room = headerValue("room", "venue", "location", "class room").ifBlank {
            Regex("""(?i)\b(?:room|venue)\s*[:#-]?\s*([A-Z0-9-]+)\b""")
                .find(joined)?.groupValues?.getOrNull(1).orEmpty()
        }

        if (course.length < 3 || (time.isBlank() && day.isBlank())) return null
        return TimetableRow(day = day, time = time, course = course, room = room, code = code)
    }

    private fun hasExplicitScheduleHeaders(record: ModuleRecord): Boolean {
        val headers = record.headers.map { it.trim().lowercase(Locale.US) }
        val hasDay = headers.any { it == "day" || it == "date" || it == "weekday" }
        val hasTime = headers.any { it == "time" || it == "start time" || it == "timing" || it == "period" }
        val hasCourse = headers.any {
            it == "class" || it == "subject" || it == "course name" || it == "title" || it == "name" || it == "course"
        }
        return hasDay && hasTime && hasCourse
    }

    private fun isUsefulScheduleRow(row: TimetableRow): Boolean =
        row.course.length >= 3 &&
            (row.day.isNotBlank() || row.time.isNotBlank()) &&
            !row.course.equals("time", true) &&
            !row.course.equals("class", true) &&
            !row.course.equals("schedule", true)

    private fun rowKey(row: TimetableRow): String =
        listOf(row.day, row.time, normalizeTitle(row.course), canonicalCode(row.code)).joinToString("|")

    private fun canonicalCode(value: String): String =
        Regex("""\b[A-Z]{2,8}[- ]?\d{3,}""", RegexOption.IGNORE_CASE)
            .find(value)?.value?.replace(" ", "")?.uppercase(Locale.US).orEmpty()

    private fun findTimeText(value: String): String =
        Regex(
            """(?i)\b\d{1,2}:\d{2}\s*(?:AM|PM)?\s*(?:[-–—]|to)\s*\d{1,2}:\d{2}\s*(?:AM|PM)?|\b\d{1,2}:\d{2}\s*(?:AM|PM)?"""
        ).find(value)?.value.orEmpty()

    private fun findDayText(value: String): String =
        Regex("""(?i)\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""")
            .find(value)?.value.orEmpty()

    /** Normalize 12/24-hour clock text to 24-hour HH:mm or HH:mm - HH:mm. */
    internal fun normalizeTimeRange(value: String): String {
        val matches = Regex("""(?i)\b(\d{1,2}):([0-5]\d)\s*(AM|PM)?\b""")
            .findAll(value)
            .take(2)
            .toList()
        if (matches.isEmpty()) return ""

        fun toMinutes(match: MatchResult): Int? {
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val suffix = match.groupValues[3].uppercase(Locale.US)
            if (hour !in 0..23 || minute !in 0..59) return null
            if (suffix == "AM") {
                if (hour == 12) hour = 0
            } else if (suffix == "PM") {
                if (hour < 12) hour += 12
            }
            return hour * 60 + minute
        }

        fun format(minutes: Int): String =
            String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

        val start = toMinutes(matches[0]) ?: return ""
        val end = matches.getOrNull(1)?.let(::toMinutes)
        return if (end != null && end > start) "${format(start)} - ${format(end)}" else format(start)
    }

    private fun normalizeDay(value: String): String {
        val clean = value.trim()
        val lower = clean.lowercase(Locale.US)
        val days = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )
        days.firstOrNull { day ->
            lower == day.lowercase(Locale.US) || lower == day.take(3).lowercase(Locale.US)
        }?.let { return it }

        val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(clean) ?: return ""
        return try {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
            calendar.clear()
            calendar.isLenient = false
            calendar.set(
                iso.groupValues[1].toInt(),
                iso.groupValues[2].toInt() - 1,
                iso.groupValues[3].toInt(),
                12, 0, 0
            )
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                Calendar.SUNDAY -> "Sunday"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanScheduleTitle(value: String, code: String, day: String): String {
        var title = ParsingUtils.cleanDisplayText(value)
        if (title.isBlank()) return ""

        if (code.isNotBlank()) title = title.replace(code, " ", ignoreCase = true)
        if (day.isNotBlank()) title = title.replace(Regex("""(?i)\b${Regex.escape(day)}\b"""), " ")
        title = title
            .replace(Regex("""\b20\d{2}\s*[-–]\s*20\d{2}\s*[-–]\s*[A-Z0-9-]+\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b\d+\s*\.{1,3}(?:\s*\(\s*\)\s*)+"""), " ")
            .replace(Regex("""\b\d+\s*\.{1,3}\b"""), " ")
            .replace(Regex("""\(\s*\)"""), " ")
            .replace(Regex("""\b\d{1,2}\s*[-–]\s*\d{1,2}\s*\[?\s*(?:lecture|lab|tutorial|practical|theory)\s*\]?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:lecture|lab|practical|theory|tutorial)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""(?i)\b(?:room|venue|location)\s*[:#-]?\s*[A-Z0-9-]+\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '|', '•', ':', '.', '(', ')')
        return title
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "").trim()
}
