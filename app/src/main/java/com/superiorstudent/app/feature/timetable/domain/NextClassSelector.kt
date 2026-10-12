package com.superiorstudent.app.feature.timetable.domain

import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.TimetableRow

/**
 * Selects the next occurrence of a weekly class from the current weekday/time.
 * If today's sessions have passed, it chooses the nearest future weekday;
 * if the entire remaining week has passed, it wraps to the next week.
 */
object NextClassSelector {
    private val dayNumbers = mapOf(
        "Monday" to 1,
        "Tuesday" to 2,
        "Wednesday" to 3,
        "Thursday" to 4,
        "Friday" to 5,
        "Saturday" to 6,
        "Sunday" to 7
    )

    fun selectNext(
        rows: List<TimetableRow>,
        currentDay: String,
        currentMinutes: Int
    ): TimetableRow? {
        val currentDayNumber = dayNumbers.entries
            .firstOrNull { it.key.equals(currentDay, ignoreCase = true) }
            ?.value ?: return null

        return rows.mapNotNull { row ->
            val classDayNumber = dayNumbers.entries
                .firstOrNull { it.key.equals(row.day, ignoreCase = true) }
                ?.value ?: return@mapNotNull null
            val start = ParsingUtils.startMinutesOf(row.time) ?: return@mapNotNull null

            var daysUntil = (classDayNumber - currentDayNumber + 7) % 7
            if (daysUntil == 0 && start < currentMinutes) daysUntil = 7
            Triple(daysUntil, start, row)
        }.minWithOrNull(
            compareBy<Triple<Int, Int, TimetableRow>> { it.first }
                .thenBy { it.second }
        )?.third
    }
}
