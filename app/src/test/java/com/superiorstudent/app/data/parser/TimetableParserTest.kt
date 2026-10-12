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
        assertEquals("09:00-10:00", row.time)
        assertEquals("Functional English", row.course)
        assertEquals("HOQ061140", row.code)
        assertEquals("204", row.room)
    }

    @Test
    fun skipsNonScheduleSessionNoise() {
        assertNull(TimetableParser.toRow(ModuleRecord(emptyList(), listOf("Session inactive stay online"))))
    }
}
