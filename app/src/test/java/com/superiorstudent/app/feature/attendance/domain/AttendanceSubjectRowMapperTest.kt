package com.superiorstudent.app.feature.attendance.domain

import com.superiorstudent.app.data.model.AttendanceDetailLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceSubjectRowMapperTest {
    private fun link(course: String, code: String, id: String, percentage: Double) =
        AttendanceDetailLink(
            course = course,
            code = code,
            url = "https://erp.superior.edu.pk/student/attendancedetail/id/$id",
            percentage = percentage
        )

    @Test
    fun preservesEachSubjectsOriginalErpDetailUrl() {
        val links = listOf(
            link("Functional English", "HOQ601140-F26-PB-UNI-BSOPM-SPRING", "204263", 100.0),
            link("QUANTITATIVE REASONING - I", "HOQ601230-F26-PB-UNI-BSOPM-SPRING", "204264", 80.0),
            link("Civics and Community Engagement", "HIM603010-F26-PB-UNI-BSOPM-SPRING", "204265", 80.0),
            link("Management of refractive errors", "HOM601350-F26-PB-UNI-BSOPM-SPRING", "204266", 83.0),
            link("VISUAL OPTICS AND IMAGE PROCESSING", "HOM602090-F26-PB-UNI-BSOPM-SPRING", "204267", 67.0),
            link("Redefining Success", "GEN601160-F26-PB-UNI-BSOPM-SPRING", "204268", 60.0)
        )

        val rows = AttendanceSubjectRowMapper.fromLinks(links)
        val expected = mapOf(
            "Functional English" to "204263",
            "QUANTITATIVE REASONING - I" to "204264",
            "Civics and Community Engagement" to "204265",
            "Management of refractive errors" to "204266",
            "VISUAL OPTICS AND IMAGE PROCESSING" to "204267",
            "Redefining Success" to "204268"
        )

        assertEquals(expected.size, rows.size)
        expected.forEach { (course, id) ->
            val row = rows.single { it.course == course }
            assertEquals(id, row.url.substringAfterLast('/'))
        }
    }

    @Test
    fun rejectsMergedAggregateTextAsAFalseSubject() {
        val merged = link(
            "Attendance Functional English 100.0% QUANTITATIVE REASONING 80.0% Academic Progress 13 / 145 Credits Completed",
            "HOQ601140-F26-PB-UNI-BSOPM",
            "204263",
            100.0
        )

        assertTrue(AttendanceSubjectRowMapper.fromLinks(listOf(merged)).isEmpty())
    }

    @Test
    fun rejectsNonErpOrNonAttendanceUrls() {
        val invalid = listOf(
            AttendanceDetailLink("Bad host", "HOQ601230", "https://example.com/student/attendancedetail/id/204264", 80.0),
            AttendanceDetailLink("Summary", "HOQ601230", "https://erp.superior.edu.pk/student/attendance", 80.0),
            AttendanceDetailLink("Missing id", "HOQ601230", "https://erp.superior.edu.pk/student/attendancedetail", 80.0)
        )

        assertFalse(AttendanceSubjectRowMapper.fromLinks(invalid).isNotEmpty())
    }

    @Test
    fun deduplicatesRepeatedUrlWithoutReassigningItToAnotherCourse() {
        val first = link("Functional English", "HOQ601140", "204263", 100.0)
        val duplicate = link("Wrong duplicate label", "HOQ601230", "204263", 80.0)

        val rows = AttendanceSubjectRowMapper.fromLinks(listOf(first, duplicate))

        assertEquals(1, rows.size)
        assertEquals("Functional English", rows.single().course)
        assertEquals("204263", rows.single().url.substringAfterLast('/'))
    }
}
