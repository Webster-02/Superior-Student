package com.superiorstudent.app.feature.timetable.domain

import com.superiorstudent.app.data.model.TimetableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextClassSelectorTest {
    @Test
    fun choosesNextDayInsteadOfAnEarlierClassThatAlreadyPassed() {
        val rows = listOf(
            TimetableRow("Monday", "09:00 - 10:00", "Monday class", "", ""),
            TimetableRow("Wednesday", "09:00 - 10:00", "Earlier class", "", ""),
            TimetableRow("Thursday", "08:00 - 09:00", "Thursday class", "", "")
        )

        val result = NextClassSelector.selectNext(rows, "Wednesday", 10 * 60)

        assertEquals("Thursday", result?.day)
        assertEquals("Thursday class", result?.course)
    }

    @Test
    fun selectsEarliestClassOnNearestFutureDay() {
        val rows = listOf(
            TimetableRow("Tuesday", "13:00 - 14:00", "Afternoon", "", ""),
            TimetableRow("Tuesday", "08:00 - 09:00", "Morning", "", ""),
            TimetableRow("Thursday", "07:00 - 08:00", "Later week", "", "")
        )

        val result = NextClassSelector.selectNext(rows, "Monday", 16 * 60)

        assertEquals("Tuesday", result?.day)
        assertEquals("Morning", result?.course)
    }

    @Test
    fun wrapsToNextWeekIfOnlyTodaysClassHasPassed() {
        val rows = listOf(TimetableRow("Monday", "09:00 - 10:00", "Weekly class", "", ""))

        val result = NextClassSelector.selectNext(rows, "Monday", 10 * 60)

        assertEquals("Weekly class", result?.course)
    }

    @Test
    fun returnsNullWhenNoValidDayOrTimeExists() {
        val rows = listOf(TimetableRow("TBD", "time unavailable", "Unknown class", "", ""))

        assertNull(NextClassSelector.selectNext(rows, "Monday", 9 * 60))
    }
}
