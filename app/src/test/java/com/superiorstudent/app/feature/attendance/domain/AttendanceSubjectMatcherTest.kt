package com.superiorstudent.app.feature.attendance.domain

import com.superiorstudent.app.data.model.AttendanceDetailLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceSubjectMatcherTest {
    private val english = AttendanceDetailLink(
        course = "Functional English HOQ601140-F26-PB-UNI-BSOPM-SPRING",
        code = "HOQ601140-F26-PB-UNI-BSOPM-SPRING",
        url = "https://erp.superior.edu.pk/student/attendancedetail/id/204263",
        percentage = 100.0
    )
    private val quantitative = AttendanceDetailLink(
        course = "QUANTITATIVE REASONING-I HOQ601230-F26-PB-UNI-BSOPM-SPRING",
        code = "HOQ601230-F26-PB-UNI-BSOPM-SPRING",
        url = "https://erp.superior.edu.pk/student/attendancedetail/id/204264",
        percentage = 80.0
    )

    @Test
    fun matchesByCanonicalCourseCodeAndNotByLooseSubstring() {
        val match = AttendanceSubjectMatcher.findMatchingLink(
            "QUANTITATIVE REASONING-I 2026-2030-BSOPM-S26-A Attendance",
            "HOQ601230-F26-PB-UNI-BSOPM-SPRING",
            listOf(english, quantitative)
        )
        assertEquals(quantitative.url, match?.url)
    }

    @Test
    fun neverReturnsEnglishDetailForDifferentSubjectCode() {
        val match = AttendanceSubjectMatcher.findMatchingLink(
            "QUANTITATIVE REASONING-I HOQ601230-F26-PB-UNI-BSOPM-SPRING",
            "HOQ601230",
            listOf(english)
        )
        assertNull(match)
    }

    @Test
    fun mapsUploadedErpSummaryCodesToTheirOwnDetailIds() {
        val links = listOf(
            AttendanceDetailLink("Functional English", "HOQ601140-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204263", 100.0),
            AttendanceDetailLink("QUANTITATIVE REASONING - I", "HOQ601230-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204264", 80.0),
            AttendanceDetailLink("Civics and Community Engagement", "HIM603010-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204265", 80.0),
            AttendanceDetailLink("Management of refractive errors", "HOM601350-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204266", 83.0),
            AttendanceDetailLink("VISUAL OPTICS AND IMAGE PROCESSING", "HOM602090-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204267", 90.0),
            AttendanceDetailLink("Redefining Success", "GEN601160-F26-PB-UNI-BSOPM-SPRING", "https://erp.superior.edu.pk/student/attendancedetail/id/204268", 100.0)
        )

        val expectedIds = mapOf(
            "HOQ601140" to "204263",
            "HOQ601230" to "204264",
            "HIM603010" to "204265",
            "HOM601350" to "204266",
            "HOM602090" to "204267",
            "GEN601160" to "204268"
        )

        expectedIds.forEach { (code, id) ->
            val match = AttendanceSubjectMatcher.findMatchingLink(
                course = "Current subject $code",
                code = code,
                links = links
            )
            assertEquals("Wrong detail ID for $code", id, match?.url?.substringAfterLast('/'))
        }
    }

    @Test
    fun doesNotGuessWhenCourseCodeIsMissingAndLabelsAreAmbiguous() {
        val duplicateLabels = listOf(
            AttendanceDetailLink("General Subject", "", "https://erp.superior.edu.pk/student/attendancedetail/id/1", null),
            AttendanceDetailLink("General Subject", "", "https://erp.superior.edu.pk/student/attendancedetail/id/2", null)
        )
        assertNull(AttendanceSubjectMatcher.findMatchingLink("General Subject", "", duplicateLabels))
    }
}
