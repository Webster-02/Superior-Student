package com.superiorstudent.app.feature.attendance.domain

import com.superiorstudent.app.data.model.AttendanceDetailLink

/**
 * Associates a subject with its ERP detail URL without fuzzy cross-subject matches.
 * When a course code is available, both sides must resolve to the same canonical code.
 */
object AttendanceSubjectMatcher {
    fun findMatchingLink(
        course: String,
        code: String,
        links: List<AttendanceDetailLink>
    ): AttendanceDetailLink? {
        val targetCode = canonicalCourseCode(code)
        if (targetCode.isNotBlank()) {
            return links.filter { canonicalCourseCode(it.code.ifBlank { it.course }) == targetCode }
                .distinctBy { it.url }
                .singleOrNull()
        }

        val targetName = normalizeCourseName(course)
        if (targetName.length < 8) return null
        return links.filter { candidate ->
            canonicalCourseCode(candidate.code.ifBlank { candidate.course }).isBlank() &&
                normalizeCourseName(candidate.course) == targetName
        }.distinctBy { it.url }.singleOrNull()
    }

    internal fun canonicalCourseCode(value: String): String {
        val match = Regex("""\b([A-Z]{2,8})[- ]?(\d{3,})\b""", RegexOption.IGNORE_CASE)
            .find(value) ?: return ""
        return (match.groupValues[1] + match.groupValues[2]).uppercase(java.util.Locale.US)
    }

    internal fun normalizeCourseName(value: String): String =
        value.lowercase(java.util.Locale.US)
            .replace(Regex("""\b(?:FALL|SPRING|SUMMER|WINTER)\s+20\d{2}\b"""), " ")
            .replace(Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:attendance|lecture|present|absent|percentage|summary)\b"""), " ")
            .replace(Regex("""[^a-z0-9]+"""), "")
}
