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
            StudentModule.ATTENDANCE -> {
                if (data.attendanceDetail != null || data.overallAttendance != null) {
                    true
                } else if (data.attendanceDetailLinks.isNotEmpty()) {
                    data.attendanceDetailLinks.any { link ->
                        isCleanAttendanceLabel(link.course) && link.code.isNotBlank() &&
                            link.url.contains("/student/attendancedetail", ignoreCase = true)
                    }
                } else {
                    data.records.any { record ->
                        val joined = record.values.joinToString(" ")
                        record.values.size >= 2 && isCleanAttendanceLabel(joined) &&
                            Regex("""\d{1,3}(?:\.\d+)?\s*%""").containsMatchIn(joined)
                    } || data.tables.any { table ->
                        table.rows.any { row -> row.size >= 2 && isCleanAttendanceLabel(row.joinToString(" ")) }
                    } || data.lines.any { it.contains("%") && isCleanAttendanceLabel(it) }
                }
            }

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

            StudentModule.FEE -> data.invoices.isNotEmpty() ||
                data.tables.any { it.rows.isNotEmpty() } ||
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

            StudentModule.ENROLLED_COURSES -> data.enrolledCourses.any {
                it.course.isNotBlank() && it.code.isNotBlank()
            } || data.records.any { record ->
                val values = record.values.joinToString(" ")
                Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(values) &&
                    (values.contains("credit", true) || values.contains("compulsory", true))
            }
        }

    private fun isCleanAttendanceLabel(value: String): Boolean {
        val text = value.trim()
        if (text.length < 3 || text.length > 220) return false
        val percentCount = Regex("""\d{1,3}(?:\.\d+)?\s*%""").findAll(text).count()
        val codeCount = Regex("""\b[A-Z]{2,8}[- ]?\d{4,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        return percentCount <= 1 && codeCount <= 1 &&
            !Regex("""Academic Progress|Credits Completed""", RegexOption.IGNORE_CASE).containsMatchIn(text)
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
