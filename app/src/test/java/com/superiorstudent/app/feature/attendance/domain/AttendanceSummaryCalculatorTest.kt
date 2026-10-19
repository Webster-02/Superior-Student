package com.superiorstudent.app.feature.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceSummaryCalculatorTest {
    @Test fun officialOverallTakesPriorityOverSubjectAverage() {
        val result = AttendanceSummaryCalculator.calculate(78.4, listOf(50.0, 100.0))!!
        assertEquals(78.4, result.percentage, 0.001)
        assertEquals(AttendanceSummarySource.OFFICIAL_OVERALL, result.source)
    }

    @Test fun usesSameUnweightedSubjectAverageAsAttendanceSummaryWhenOverallIsMissing() {
        val result = AttendanceSummaryCalculator.calculate(null, listOf(80.0, 90.0, 100.0))!!
        assertEquals(90.0, result.percentage, 0.001)
        assertEquals(AttendanceSummarySource.SUBJECT_AVERAGE, result.source)
    }

    @Test fun ignoresInvalidSubjectPercentagesButAcceptsZeroAndOneHundred() {
        val result = AttendanceSummaryCalculator.calculate(null, listOf(-1.0, 0.0, 100.0, Double.NaN, Double.POSITIVE_INFINITY))!!
        assertEquals(50.0, result.percentage, 0.001)
        assertEquals(AttendanceSummarySource.SUBJECT_AVERAGE, result.source)
    }

    @Test fun returnsNullWhenNeitherOverallNorAnySubjectPercentageIsValid() {
        assertNull(AttendanceSummaryCalculator.calculate(null, emptyList()))
        assertNull(AttendanceSummaryCalculator.calculate(Double.NaN, listOf(-2.0, 101.0)))
    }
}
