package com.superiorstudent.app.data.repository

import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.StudentModule
import com.superiorstudent.app.data.parser.ErpPageParser

/**
 * Central data boundary for ERP payload parsing, cache invalidation and
 * per-feature readiness checks. WebView authentication/navigation remains
 * owned by MainActivity so the university session flow is not hidden here.
 */
class ErpRepository {
    private val cachedPayloads = mutableMapOf<StudentModule, String>()

    fun parse(payload: String): ModuleData? = ErpPageParser.parse(payload)

    fun cachedPayload(module: StudentModule): String? = cachedPayloads[module]

    fun cachePayload(module: StudentModule, payload: String) {
        cachedPayloads[module] = payload
    }

    fun clearCachedPayload(module: StudentModule) {
        cachedPayloads.remove(module)
    }

    fun clearCache() {
        cachedPayloads.clear()
    }

    fun hasUsableData(module: StudentModule, data: ModuleData): Boolean =
        when (module) {
            StudentModule.ATTENDANCE -> data.attendanceDetail != null ||
                data.overallAttendance != null ||
                data.records.any { it.values.size >= 2 } ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.lines.any { it.contains("%") }

            StudentModule.TIMETABLE -> data.records.any(::looksLikeScheduleRecord) ||
                data.records.any { record ->
                    record.headers.any { header ->
                        header.contains("time", true) ||
                            header.contains("day", true) ||
                            header.contains("course", true)
                    } && record.values.any { value ->
                        Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(value)
                    }
                } ||
                data.lines.any { Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(it) }

            StudentModule.FEE -> data.tables.any { it.rows.isNotEmpty() } ||
                data.cards.isNotEmpty() ||
                data.records.any { it.values.size >= 2 }

            StudentModule.PROFILE -> data.cards.isNotEmpty() ||
                data.records.any { it.headers.any { header -> header.equals("Field", true) } } ||
                data.tables.any { it.rows.isNotEmpty() }

            StudentModule.RESULTS -> data.academicResults.isNotEmpty() ||
                data.semesterOptions.isNotEmpty() ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.records.any { it.values.size >= 2 } ||
                data.lines.any { it.contains("result", true) || it.contains("grade", true) }
        }

    private fun looksLikeScheduleRecord(record: ModuleRecord): Boolean {
        val joined = record.values.joinToString(" ")
        return Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""").containsMatchIn(joined) ||
            Regex(
                """\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(joined)
    }
}
