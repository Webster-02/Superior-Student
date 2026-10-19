package com.superiorstudent.app.feature.attendance.domain

/**
 * Selects the same percentage shown at the top of the Attendance summary:
 * prefer the ERP's overall figure, otherwise use the average of valid listed
 * subject percentages. The fallback is explicitly identified as a subject
 * average because it is not weighted by the number of classes in each subject.
 */
enum class AttendanceSummarySource { OFFICIAL_OVERALL, SUBJECT_AVERAGE }

data class AttendanceSummaryValue(
    val percentage: Double,
    val source: AttendanceSummarySource
)

object AttendanceSummaryCalculator {
    fun calculate(overall: Double?, subjectPercentages: List<Double>): AttendanceSummaryValue? {
        val official = overall?.takeIf { it.isFinite() && it in 0.0..100.0 }
        if (official != null) {
            return AttendanceSummaryValue(official, AttendanceSummarySource.OFFICIAL_OVERALL)
        }
        val valid = subjectPercentages.filter { it.isFinite() && it in 0.0..100.0 }
        val average = valid.takeIf { it.isNotEmpty() }?.average() ?: return null
        if (!average.isFinite() || average !in 0.0..100.0) return null
        return AttendanceSummaryValue(average, AttendanceSummarySource.SUBJECT_AVERAGE)
    }
}
