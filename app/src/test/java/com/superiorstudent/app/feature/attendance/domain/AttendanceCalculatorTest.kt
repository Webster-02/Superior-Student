package com.superiorstudent.app.feature.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalculatorTest {

    @Test
    fun belowTargetCalculatesClassesNeeded() {
        val result = AttendanceCalculator.calculate(attended = 3, conducted = 5)!!

        assertEquals(60.0, result.currentPercentage, 0.001)
        assertEquals(3, result.classesNeededToReachTarget)
        assertFalse(result.targetAlreadyReached)
        assertEquals(75.0, result.projectedPercentageAfterRequiredAttendance, 0.001)
        assertEquals(0, result.safeFutureLeaves)
    }

    @Test
    fun perfectAttendanceHasOneSafeLeavePerFiveClasses() {
        val result = AttendanceCalculator.calculate(attended = 5, conducted = 5)!!

        assertEquals(100.0, result.currentPercentage, 0.001)
        assertEquals(0, result.classesNeededToReachTarget)
        assertTrue(result.targetAlreadyReached)
        assertEquals(1, result.safeFutureLeaves)
    }

    @Test
    fun computesSafeLeavesAtEightyTwoPercent() {
        val result = AttendanceCalculator.calculate(attended = 41, conducted = 50)!!

        assertEquals(82.0, result.currentPercentage, 0.001)
        assertEquals(4, result.safeFutureLeaves)
        assertTrue(result.targetAlreadyReached)
    }

    @Test
    fun exactSeventyFivePercentHasNoSafeLeave() {
        val result = AttendanceCalculator.calculate(attended = 3, conducted = 4)!!

        assertEquals(75.0, result.currentPercentage, 0.001)
        assertEquals(0, result.classesNeededToReachTarget)
        assertEquals(0, result.safeFutureLeaves)
    }

    @Test
    fun rejectsMissingOrInconsistentCounts() {
        assertNull(AttendanceCalculator.calculate(attended = 0, conducted = 0))
        assertNull(AttendanceCalculator.calculate(attended = -1, conducted = 5))
        assertNull(AttendanceCalculator.calculate(attended = 6, conducted = 5))
    }
}
