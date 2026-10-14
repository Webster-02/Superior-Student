package com.superiorstudent.app.data.parser

import com.superiorstudent.app.core.config.ErpConfig
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.AttendanceDetailData
import com.superiorstudent.app.data.model.AttendanceDetailLink
import com.superiorstudent.app.data.model.AttendanceSession
import com.superiorstudent.app.data.model.AcademicCourseResult
import com.superiorstudent.app.data.model.AcademicTermResult
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.InvoiceData
import com.superiorstudent.app.data.model.EnrolledCourse
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TableData
import org.json.JSONObject

/**
 * Converts the JSON extracted from the live ERP page into typed app models.
 * This class has no Activity dependency, so malformed page data cannot affect UI code.
 */
object ErpPageParser {
    private fun JSONObject.optPercent(key: String): Double? {
        val raw = opt(key)
        if (raw == null || raw === JSONObject.NULL) return null
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        } ?: return null
        return value.takeIf { !it.isNaN() && !it.isInfinite() && it in 0.0..100.0 }
    }

    fun parse(payload: String): ModuleData? =
        try {
            parseInternal(payload)
        } catch (_: Exception) {
            null
        }

    private fun parseInternal(payload: String): ModuleData {
        val json = JSONObject(payload)
        val cards = mutableListOf<Pair<String, String>>()
        json.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = ParsingUtils.cleanDisplayText(o.optString("label", ""))
                val value = ParsingUtils.cleanDisplayText(o.optString("value", ""))
                if (label.isNotBlank() && value.isNotBlank()) cards.add(label to value)
            }
        }

        val tables = mutableListOf<TableData>()
        val records = mutableListOf<ModuleRecord>()
        json.optJSONArray("tables")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val headers = mutableListOf<String>()
                t.optJSONArray("headers")?.let { h ->
                    for (j in 0 until h.length()) headers.add(ParsingUtils.cleanDisplayText(h.optString(j, "")))
                }
                val rows = mutableListOf<List<String>>()
                t.optJSONArray("rows")?.let { rs ->
                    for (j in 0 until rs.length()) {
                        val ro = rs.optJSONArray(j) ?: continue
                        val row = mutableListOf<String>()
                        for (k in 0 until ro.length()) row.add(ParsingUtils.cleanDisplayText(ro.optString(k, "")))
                        if (row.any { it.isNotBlank() }) {
                            rows.add(row)
                            records.add(ModuleRecord(headers, row))
                        }
                    }
                }
                val title = ParsingUtils.cleanDisplayText(t.optString("title", ""))
                if (headers.isNotEmpty() || rows.isNotEmpty()) tables.add(TableData(title, headers, rows))
            }
        }

        json.optJSONArray("records")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val h = mutableListOf<String>()
                val v = mutableListOf<String>()
                o.optJSONArray("headers")?.let { q ->
                    for (j in 0 until q.length()) h.add(ParsingUtils.cleanDisplayText(q.optString(j, "")))
                }
                o.optJSONArray("values")?.let { q ->
                    for (j in 0 until q.length()) v.add(ParsingUtils.cleanDisplayText(q.optString(j, "")))
                }
                if (v.any { it.isNotBlank() }) records.add(ModuleRecord(h, v))
            }
        }

        val lines = mutableListOf<String>()
        json.optJSONArray("lines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val line = ParsingUtils.cleanDisplayText(arr.optString(i, ""))
                if (ParsingUtils.isUsefulDisplayText(line)) lines.add(line)
            }
        }

        val semesterOptions = records
            .filter { it.headers.any { h -> h.equals("Semester options", true) } }
            .flatMap { it.values }
            .map(ParsingUtils::cleanDisplayText)
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

        val invoices = mutableListOf<InvoiceData>()
        json.optJSONArray("invoices")?.let { items ->
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val number = ParsingUtils.cleanDisplayText(item.optString("number", ""))
                if (number.isBlank()) continue
                val rawUrl = item.optString("downloadUrl", "")
                val safeUrl = try {
                    val uri = java.net.URI(rawUrl)
                    if (ErpConfig.belongsToErp(rawUrl) &&
                        uri.path.matches(Regex("^/student/invoices/download/[0-9]+/?$"))) rawUrl else ""
                } catch (_: Exception) { "" }

                invoices.add(
                    InvoiceData(
                        number = number,
                        invoiceDate = ParsingUtils.cleanDisplayText(item.optString("invoiceDate", "")),
                        dueDate = ParsingUtils.cleanDisplayText(item.optString("dueDate", "")),
                        term = ParsingUtils.cleanDisplayText(item.optString("term", "")),
                        semester = ParsingUtils.cleanDisplayText(item.optString("semester", "")),
                        receiptFor = ParsingUtils.cleanDisplayText(item.optString("receiptFor", "")),
                        barcode = ParsingUtils.cleanDisplayText(item.optString("barcode", "")),
                        totalAmount = ParsingUtils.cleanDisplayText(item.optString("totalAmount", "")),
                        payable = ParsingUtils.cleanDisplayText(item.optString("payable", "")),
                        status = ParsingUtils.cleanDisplayText(item.optString("status", "")).lowercase(java.util.Locale.US),
                        downloadUrl = safeUrl
                    )
                )
            }
        }

        val enrolledCourses = mutableListOf<EnrolledCourse>()
        json.optJSONArray("enrolledCourses")?.let { items ->
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val course = ParsingUtils.cleanDisplayText(item.optString("course", ""))
                val code = ParsingUtils.cleanDisplayText(item.optString("code", ""))
                if (course.isBlank() || code.isBlank()) continue

                val rawDetailUrl = item.optString("detailUrl", "")
                val safeDetailUrl = try {
                    val uri = java.net.URI(rawDetailUrl)
                    if (ErpConfig.belongsToErp(rawDetailUrl) &&
                        uri.path.matches(Regex("^/student/results/id/[0-9]+/?$"))) {
                        rawDetailUrl
                    } else {
                        ""
                    }
                } catch (_: Exception) {
                    ""
                }

                enrolledCourses.add(
                    EnrolledCourse(
                        course = course,
                        code = code,
                        credits = ParsingUtils.cleanDisplayText(item.optString("credits", "")),
                        courseType = ParsingUtils.cleanDisplayText(item.optString("courseType", "")),
                        term = ParsingUtils.cleanDisplayText(item.optString("term", "")),
                        faculty = ParsingUtils.cleanDisplayText(item.optString("faculty", "")),
                        department = ParsingUtils.cleanDisplayText(item.optString("department", "")),
                        detailUrl = safeDetailUrl
                    )
                )
            }
        }

        val academicResults = mutableListOf<AcademicTermResult>()
        json.optJSONArray("academicResults")?.let { terms ->
            for (i in 0 until terms.length()) {
                val termObject = terms.optJSONObject(i) ?: continue
                val termName = ParsingUtils.cleanDisplayText(termObject.optString("term", ""))
                if (termName.isBlank()) continue

                val courses = mutableListOf<AcademicCourseResult>()
                termObject.optJSONArray("courses")?.let { items ->
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val courseName = ParsingUtils.cleanDisplayText(item.optString("course", ""))
                        if (courseName.isBlank() || courseName.equals("course", true) ||
                            courseName.equals("subject", true)) continue
                        courses.add(
                            AcademicCourseResult(
                                course = courseName,
                                credits = ParsingUtils.cleanDisplayText(item.optString("credits", "")),
                                marksObtained = ParsingUtils.cleanDisplayText(item.optString("marksObtained", "")),
                                gradePoints = ParsingUtils.cleanDisplayText(item.optString("gradePoints", "")),
                                midGrade = ParsingUtils.cleanDisplayText(item.optString("midGrade", "")),
                                finalGrade = ParsingUtils.cleanDisplayText(item.optString("finalGrade", ""))
                            )
                        )
                    }
                }
                academicResults.add(
                    AcademicTermResult(
                        term = termName,
                        gradePoints = ParsingUtils.cleanDisplayText(termObject.optString("gradePoints", "")),
                        totalCredits = ParsingUtils.cleanDisplayText(termObject.optString("totalCredits", "")),
                        earnedCredits = ParsingUtils.cleanDisplayText(termObject.optString("earnedCredits", "")),
                        gpa = ParsingUtils.cleanDisplayText(termObject.optString("gpa", "")),
                        cgpa = ParsingUtils.cleanDisplayText(termObject.optString("cgpa", "")),
                        courses = courses.distinctBy { it.course.lowercase(java.util.Locale.US) }
                    )
                )
            }
        }

        val overall = json.optPercent("overallAttendance")

        val attendanceDetail = json.optJSONObject("attendanceDetail")?.let { detail ->
            fun intOrNull(key: String): Int? =
                if (detail.has(key) && !detail.isNull(key)) detail.optInt(key) else null
            val percentage = detail.optPercent("percentage")
            val sessions = mutableListOf<AttendanceSession>()
            detail.optJSONArray("sessions")?.let { items ->
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    sessions.add(AttendanceSession(
                        ParsingUtils.cleanDisplayText(item.optString("date", "")),
                        ParsingUtils.cleanDisplayText(item.optString("status", "")).uppercase(java.util.Locale.US)
                    ))
                }
            }
            AttendanceDetailData(
                ParsingUtils.cleanDisplayText(detail.optString("course", "")),
                ParsingUtils.cleanDisplayText(detail.optString("code", "")),
                intOrNull("conducted"),
                intOrNull("attended"),
                percentage,
                sessions
            )
        }

        val attendanceDetailLinks = mutableListOf<AttendanceDetailLink>()
        json.optJSONArray("attendanceDetailLinks")?.let { items ->
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val url = item.optString("url", "")
                if (!ErpConfig.belongsToErp(url) || !url.contains("/student/attendancedetail", ignoreCase = true)) continue
                val percentage = item.optPercent("percentage")
                attendanceDetailLinks.add(AttendanceDetailLink(
                    ParsingUtils.cleanDisplayText(item.optString("course", "")),
                    ParsingUtils.cleanDisplayText(item.optString("code", "")),
                    url,
                    percentage
                ))
            }
        }

        return ModuleData(
            cards.distinct(),
            tables,
            lines.distinct(),
            records.distinctBy { it.headers to it.values },
            overall,
            semesterOptions,
            attendanceDetail,
            attendanceDetailLinks.distinctBy { it.url },
            invoices.distinctBy { it.number },
            academicResults.distinctBy { it.term.lowercase(java.util.Locale.US) },
            enrolledCourses.distinctBy {
                it.course.lowercase(java.util.Locale.US) + "|" + it.code.lowercase(java.util.Locale.US)
            }
        )

    }

}
