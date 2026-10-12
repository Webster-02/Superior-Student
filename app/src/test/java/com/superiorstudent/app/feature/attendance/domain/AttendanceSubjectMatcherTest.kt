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
        url = "https://erp.superior.edu.pk/student/attendancedetail/id/204271",
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
    fun doesNotGuessWhenCourseCodeIsMissingAndLabelsAreAmbiguous() {
        val duplicateLabels = listOf(
            AttendanceDetailLink("General Subject", "", "https://erp.superior.edu.pk/student/attendancedetail/id/1", null),
            AttendanceDetailLink("General Subject", "", "https://erp.superior.edu.pk/student/attendancedetail/id/2", null)
        )
        assertNull(AttendanceSubjectMatcher.findMatchingLink("General Subject", "", duplicateLabels))
    }
}
