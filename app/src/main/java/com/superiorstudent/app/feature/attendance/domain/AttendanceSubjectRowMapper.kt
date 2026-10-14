package com.superiorstudent.app.feature.attendance.domain

import com.superiorstudent.app.core.config.ErpConfig
import com.superiorstudent.app.data.model.AttendanceDetailLink
import java.net.URI

/**
 * Converts the ERP's direct per-subject links into display rows.
 * Direct hrefs are authoritative; they are not re-associated by fuzzy course text.
 */
data class AttendanceSubjectRow(
    val course: String,
    val code: String,
    val percentage: Double?,
    val url: String
)

object AttendanceSubjectRowMapper {
    fun fromLinks(links: List<AttendanceDetailLink>): List<AttendanceSubjectRow> =
        links.mapNotNull { link ->
            val url = link.url.trim()
            if (!ErpConfig.belongsToErp(url)) return@mapNotNull null

            val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
            if (!path.matches(Regex("""/student/attendancedetail(?:/id)?/[^/]+/?""", RegexOption.IGNORE_CASE))) {
                return@mapNotNull null
            }

            val course = link.course.trim()
            val code = link.code.trim()
            val percentCount = Regex("""d{1,3}(?:.d+)?s*%""").findAll(course).count()
            val codeCount = Regex("""[A-Z]{2,8}[- ]?d{4,}[A-Z0-9-]*""", RegexOption.IGNORE_CASE)
                .findAll(course).count()
            val aggregate = percentCount > 1 || codeCount > 1 ||
                Regex("""Academic Progress|Credits Completed""", RegexOption.IGNORE_CASE).containsMatchIn(course)
            if (aggregate || (course.isBlank() && code.isBlank())) return@mapNotNull null

            AttendanceSubjectRow(
                course = course.ifBlank { code },
                code = code,
                percentage = link.percentage?.takeIf { it.isFinite() && it in 0.0..100.0 },
                url = url
            )
        }.distinctBy { it.url }
}
