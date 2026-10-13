package com.superiorstudent.app.feature.attendance.domain

/**
 * Pure attendance forecasting rules for a 75% minimum.
 *
 * Counts are taken from the ERP subject detail page: classes attended and
 * classes conducted. This class does not depend on Android or UI state.
 */
data class AttendanceForecast(
    val attended: Int,
    val conducted: Int,
    val currentPercentage: Double,
    val targetPercentage: Double,
    val classesNeededToReachTarget: Int,
    val targetAlreadyReached: Boolean,
    val safeFutureLeaves: Int,
    val projectedPercentageAfterRequiredAttendance: Double
)

data class AttendanceProjection(
    val futureClasses: Int,
    val attendedIfAttendAll: Int,
    val attendedIfMissAll: Int,
    val totalAfterFutureClasses: Int,
    val percentageIfAttendAll: Double,
    val percentageIfMissAll: Double,
    val targetPercentage: Double,
    val targetMetIfAttendAll: Boolean,
    val targetMetIfMissAll: Boolean
)

object AttendanceCalculator {
    const val TARGET_PERCENTAGE = 75.0

    /**
     * Returns null for incomplete or inconsistent ERP counts so the UI cannot
     * display a fabricated prediction.
     */
    fun calculate(
        attended: Int,
        conducted: Int,
        targetPercentage: Double = TARGET_PERCENTAGE
    ): AttendanceForecast? {
        if (conducted <= 0 || attended < 0 || attended > conducted) return null
        if (targetPercentage <= 0.0 || targetPercentage >= 100.0) return null

        val current = attended.toDouble() * 100.0 / conducted
        val targetAlreadyReached = current + 1e-9 >= targetPercentage

        // Solve (attended + n) / (conducted + n) >= targetPercentage / 100.
        val target = targetPercentage / 100.0
        val deficit = target * conducted - attended
        val required = if (deficit <= 0.0) {
            0
        } else {
            kotlin.math.ceil(deficit / (1.0 - target) - 1e-9).toInt()
        }

        // Solve attended / (conducted + missed) >= targetPercentage / 100.
        // Only a currently compliant student has leaves available.
        val safeLeaves = if (targetAlreadyReached) {
            kotlin.math.floor((attended / target) - conducted + 1e-9)
                .toInt()
                .coerceAtLeast(0)
        } else {
            0
        }

        val projected = if (required == 0) {
            current
        } else {
            (attended + required).toDouble() * 100.0 / (conducted + required)
        }

        return AttendanceForecast(
            attended = attended,
            conducted = conducted,
            currentPercentage = current,
            targetPercentage = targetPercentage,
            classesNeededToReachTarget = required,
            targetAlreadyReached = targetAlreadyReached,
            safeFutureLeaves = safeLeaves,
            projectedPercentageAfterRequiredAttendance = projected
        )
    }
    /**
     * Simulates future classes under two scenarios: attend every class or miss every class.
     * This is a forecast only and does not assume that a class is scheduled or held.
     */
    fun project(
        attended: Int,
        conducted: Int,
        futureClasses: Int,
        targetPercentage: Double = TARGET_PERCENTAGE
    ): AttendanceProjection? {
        if (conducted <= 0 || attended < 0 || attended > conducted || futureClasses <= 0) return null
        if (!targetPercentage.isFinite() || targetPercentage <= 0.0 || targetPercentage >= 100.0) return null

        val futureTotal = conducted + futureClasses
        val attendCount = attended + futureClasses
        val attendPercent = attendCount.toDouble() * 100.0 / futureTotal
        val missPercent = attended.toDouble() * 100.0 / futureTotal

        return AttendanceProjection(
            futureClasses = futureClasses,
            attendedIfAttendAll = attendCount,
            attendedIfMissAll = attended,
            totalAfterFutureClasses = futureTotal,
            percentageIfAttendAll = attendPercent,
            percentageIfMissAll = missPercent,
            targetPercentage = targetPercentage,
            targetMetIfAttendAll = attendPercent + 1e-9 >= targetPercentage,
            targetMetIfMissAll = missPercent + 1e-9 >= targetPercentage
        )
    }

}
