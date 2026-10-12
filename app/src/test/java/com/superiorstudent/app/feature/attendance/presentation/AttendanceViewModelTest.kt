package com.superiorstudent.app.feature.attendance.presentation

import com.superiorstudent.app.data.model.AttendanceDetailData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceViewModelTest {
    private val viewModel = AttendanceViewModel()

    @Test
    fun belowTargetReturnsRequiredClassesAndProjectedPercentage() {
        val state = viewModel.detailUiState(
            AttendanceDetailData(
                course = "Functional English",
                code = "HOQ061140",
                conducted = 5,
                attended = 3,
                percentage = null,
                sessions = emptyList()
            )
        )

        assertTrue(state.countsValid)
        assertEquals(60.0, state.displayPercentage!!, 0.001)
        assertEquals(3, state.classesNeededToReachTarget)
        assertFalse(state.targetReached)
        assertEquals(75.0, state.projectedPercentage!!, 0.001)
        assertEquals(0, state.safeFutureLeaves)
    }

    @Test
    fun perfectAttendanceProvidesOneSafeLeaveAtFiveOfFive() {
        val state = viewModel.detailUiState(
            AttendanceDetailData("Functional English", "", 5, 5, null, emptyList())
        )

        assertTrue(state.targetReached)
        assertEquals(100.0, state.calculatedPercentage!!, 0.001)
        assertEquals(0, state.classesNeededToReachTarget)
        assertEquals(1, state.safeFutureLeaves)
    }

    @Test
    fun exactSeventyFivePercentHasNoSafeLeaves() {
        val state = viewModel.detailUiState(
            AttendanceDetailData("Functional English", "", 4, 3, null, emptyList())
        )

        assertTrue(state.targetReached)
        assertEquals(75.0, state.displayPercentage!!, 0.001)
        assertEquals(0, state.safeFutureLeaves)
    }

    @Test
    fun incompleteErpCountsUseOnlyTheReportedPercentage() {
        val state = viewModel.detailUiState(
            AttendanceDetailData("Functional English", "", null, null, 62.5, emptyList())
        )

        assertFalse(state.countsValid)
        assertEquals(62.5, state.displayPercentage!!, 0.001)
        assertNull(state.calculatedPercentage)
        assertNull(state.classesNeededToReachTarget)
        assertNull(state.safeFutureLeaves)
        assertNull(state.projectedPercentage)
    }

    @Test
    fun inconsistentErpCountsDoNotCreatePredictions() {
        val state = viewModel.detailUiState(
            AttendanceDetailData("Functional English", "", 5, 6, 99.0, emptyList())
        )

        assertFalse(state.countsValid)
        assertEquals(99.0, state.displayPercentage!!, 0.001)
        assertNull(state.classesNeededToReachTarget)
        assertNull(state.safeFutureLeaves)
    }
}
