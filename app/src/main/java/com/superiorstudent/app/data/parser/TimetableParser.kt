package com.superiorstudent.app.data.parser

import com.superiorstudent.app.ParsingUtils
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TimetableRow

/**
 * Pure ERP timetable-row parser shared by the timetable screen and dashboard
 * "next class" preview, so both features interpret records consistently.
 */
object TimetableParser {
    fun toRow(record: ModuleRecord): TimetableRow? {
        val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
        if (values.isEmpty()) return null
        val headers = record.headers.map(ParsingUtils::cleanDisplayText)
        val joined = values.joinToString(" ")
        if (joined.contains("session", true) || joined.contains("inactive", true) || joined.contains("stay online", true)) return null

        fun headerValue(vararg keys: String): String {
            val index = headers.indexOfFirst { h -> keys.any { key -> h.contains(key, true) } }
            return if (index >= 0 && index < values.size) values[index] else ""
        }

        val time = headerValue("time", "timing", "start time", "period").ifBlank {
            Regex("""(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?""")
                .find(joined)?.value.orEmpty()
        }
        val day = headerValue("day", "date", "weekday").ifBlank {
            Regex("""(?i)\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""")
                .find(joined)?.value.orEmpty()
        }
        val code = headerValue("course code", "subject code", "code").ifBlank {
            Regex("""\b[A-Z]{2,8}[- ]?\d{2,6}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
                .find(joined)?.value.orEmpty()
        }
        val course = headerValue("subject", "course name", "course", "class", "title", "name").ifBlank {
            val withoutMeta = joined
                .replace(time, "")
                .replace(day, "")
                .replace(code, "")
                .replace(Regex("""(?i)\b(?:room|venue|location)\s*[:#-]?\s*[A-Z0-9-]+\b"""), "")
                .trim(' ', '-', '–', '|', '•')
            withoutMeta.split(Regex("""\s{2,}|\|"""))
                .map { it.trim() }
                .firstOrNull { it.length >= 3 }
                ?: withoutMeta
        }
        val room = headerValue("room", "venue", "location", "class room").ifBlank {
            Regex("""(?i)\b(?:room|venue)\s*[:#-]?\s*([A-Z0-9-]+)\b""")
                .find(joined)?.groupValues?.getOrNull(1).orEmpty()
        }
        if (course.length < 3 || (time.isBlank() && day.isBlank())) return null
        return TimetableRow(day, time, course, room, code)
    }
}
