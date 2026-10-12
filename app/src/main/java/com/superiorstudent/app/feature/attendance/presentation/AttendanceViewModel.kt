package com.superiorstudent.app.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import com.superiorstudent.app.data.model.AttendanceDetailData
import com.superiorstudent.app.feature.attendance.domain.AttendanceCalculator

/**
 * Owns attendance-specific display state and forecasting decisions for the UI.
 * ERP parsing and navigation stay in the repository/Activity layers.
 */
data class AttendanceDetailUiState(
    val countsValid: Boolean,
    val attended: Int,
    val conducted: Int,
    val calculatedPercentage: Double?,
    val displayPercentage: Double?,
    val classesNeededToReachTarget: Int?,
    val targetReached: Boolean,
    val safeFutureLeaves: Int?,
    val projectedPercentage: Double?
)

class AttendanceViewModel : ViewModel() {

    fun detailUiState(detail: AttendanceDetailData): AttendanceDetailUiState {
        val attended = detail.attended
        val conducted = detail.conducted
        val countsValid = attended != null &&
            conducted != null &&
            conducted > 0 &&
            attended >= 0 &&
            attended <= conducted

        val presentCount = attended ?: 0
        val conductedCount = conducted ?: 0
        val forecast = if (countsValid) {
            AttendanceCalculator.calculate(presentCount, conductedCount)
        } else {
            null
        }

        val projected = forecast?.let {
            if (it.classesNeededToReachTarget > 0) {
                (presentCount + it.classesNeededToReachTarget).toDouble() * 100.0 /
                    (conductedCount + it.classesNeededToReachTarget)
            } else {
                it.currentPercentage
            }
        }

        return AttendanceDetailUiState(
            countsValid = countsValid,
            attended = presentCount,
            conducted = conductedCount,
            calculatedPercentage = forecast?.currentPercentage,
            displayPercentage = forecast?.currentPercentage ?: detail.percentage,
            classesNeededToReachTarget = forecast?.classesNeededToReachTarget,
            targetReached = forecast?.targetAlreadyReached == true,
            safeFutureLeaves = forecast?.safeFutureLeaves,
            projectedPercentage = projected
        )
    }
}
