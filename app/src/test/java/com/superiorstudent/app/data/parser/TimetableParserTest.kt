package com.superiorstudent.app.data.parser

import com.superiorstudent.app.data.model.ModuleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableParserTest {
    @Test
    fun parsesScheduleUsingExplicitErpHeaders() {
        val row = TimetableParser.toRow(
            ModuleRecord(
                headers = listOf("Day", "Time", "Course Name", "Course Code", "Room"),
                values = listOf("Monday", "09:00 - 10:00", "Functional English", "HOQ061140", "204")
            )
        )!!

        assertEquals("Monday", row.day)
        assertEquals("09:00 - 10:00", row.time)
        assertEquals("Functional English", row.course)
        assertEquals("HOQ061140", row.code)
        assertEquals("204", row.room)
    }

    @Test
    fun parsesScheduleFromFlatTextWhenHeadersAreUnavailable() {
        val row = TimetableParser.toRow(
            ModuleRecord(
                headers = emptyList(),
                values = listOf("Functional English HOQ061140 Monday 09:00-10:00 Room 204")
            )
        )!!

        assertEquals("Monday", row.day)
        assertEquals("09:00 - 10:00", row.time)
        assertEquals("Functional English", row.course)
        assertEquals("HOQ061140", row.code)
        assertEquals("204", row.room)
    }

    @Test
    fun keepsHeaderIndexesAlignedWhenErpRowContainsBlankCells() {
        val row = TimetableParser.toRow(
            ModuleRecord(
                headers = listOf("Day", "Time", "Instructor", "Course Name", "Course Code", "Room"),
                values = listOf("Monday", "09:00 - 10:00", "", "Functional English", "HOQ061140", "204")
            )
        )!!

        assertEquals("Monday", row.day)
        assertEquals("09:00 - 10:00", row.time)
        assertEquals("Functional English", row.course)
        assertEquals("HOQ061140", row.code)
        assertEquals("204", row.room)
    }

    @Test
    fun normalizesTwelveHourTimesAndRemovesCalendarTitleNoise() {
        val row = TimetableParser.toRow(
            ModuleRecord(
                headers = listOf("Day", "Time", "Class", "Course Code"),
                values = listOf(
                    "2026-10-13",
                    "08:00 AM - 11:00 AM",
                    "Civics and Community Engagement 2.. () ()",
                    "HIM603010-F26-PB-UNI-BSOPM-SPRING"
                )
            )
        )!!

        assertEquals("Tuesday", row.day)
        assertEquals("08:00 - 11:00", row.time)
        assertEquals("Civics and Community Engagement", row.course)
        assertEquals("", row.room)
    }

    @Test
    fun prefersExplicitCalendarEventsOverNoisyGenericParentRows() {
        val records = listOf(
            ModuleRecord(
                headers = emptyList(),
                values = listOf("Management of refractive errors 2.. () VISUAL OPTICS AND IMAGE PROCESSING")
            ),
            ModuleRecord(
                headers = listOf("Day", "Time", "Class", "Course Code"),
                values = listOf("Monday", "10:00 - 13:00", "Management of refractive errors", "HOM601350-F26-PB-UNI-BSOPM-SPRING")
            ),
            ModuleRecord(
                headers = listOf("Day", "Time", "Class", "Course Code"),
                values = listOf("Monday", "13:00 - 18:00", "Visual Optics and Image Processing", "HOM602090-F26-PB-UNI-BSOPM-SPRING")
            )
        )

        val rows = TimetableParser.toRows(records)

        assertEquals(2, rows.size)
        assertEquals("Management of refractive errors", rows[0].course)
        assertEquals("Visual Optics and Image Processing", rows[1].course)
        assertEquals("10:00 - 13:00", rows[0].time)
        assertEquals("13:00 - 18:00", rows[1].time)
    }

    @Test
    fun keepsCoursesFromDifferentDepartmentsAsSeparateRows() {
        val records = listOf(
            ModuleRecord(
                headers = listOf("Day", "Time", "Class", "Course Code"),
                values = listOf("Monday", "08:00 - 10:00", "Introduction to Programming", "CSC101")
            ),
            ModuleRecord(
                headers = listOf("Day", "Time", "Class", "Course Code"),
                values = listOf("Tuesday", "10:00 - 11:30", "Financial Accounting", "ACC201")
            )
        )

        val rows = TimetableParser.toRows(records)

        assertEquals(2, rows.size)
        assertEquals("Introduction to Programming", rows[0].course)
        assertEquals("CSC101", rows[0].code)
        assertEquals("Financial Accounting", rows[1].course)
        assertEquals("ACC201", rows[1].code)
    }

    @Test
    fun skipsNonScheduleSessionNoise() {
        assertNull(TimetableParser.toRow(ModuleRecord(emptyList(), listOf("Session inactive stay online"))))
    }
}
