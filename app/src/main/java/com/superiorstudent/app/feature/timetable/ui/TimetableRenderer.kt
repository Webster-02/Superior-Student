package com.superiorstudent.app.feature.timetable.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TimetableRow
import com.superiorstudent.app.data.parser.TimetableParser
import com.superiorstudent.app.feature.timetable.domain.NextClassSelector
import com.superiorstudent.app.ui.Palette
import com.superiorstudent.app.ui.components.Chip
import com.superiorstudent.app.ui.components.EmptyState
import com.superiorstudent.app.ui.components.ErrorState
import com.superiorstudent.app.ui.components.HeroCard
import com.superiorstudent.app.ui.components.Skeleton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Timetable presentation only. ERP extraction and TimetableParser stay unchanged.
 * Lecturer and room fields are intentionally not presented because those details
 * are not reliable/available in the student data supplied for this screen.
 */
class TimetableRenderer(
    private val activity: Context,
    private val onRetry: () -> Unit = {}
) {
    private val regularTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)

    private val mediumTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val dayOrder = linkedMapOf(
        "Monday" to Calendar.MONDAY,
        "Tuesday" to Calendar.TUESDAY,
        "Wednesday" to Calendar.WEDNESDAY,
        "Thursday" to Calendar.THURSDAY,
        "Friday" to Calendar.FRIDAY,
        "Saturday" to Calendar.SATURDAY,
        "Sunday" to Calendar.SUNDAY
    )

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        applyTypography(container)

        val rows = TimetableParser.toRows(data.records)
            .filter(::hasCourseLabel)
            .distinctBy { it.day + "|" + it.time + "|" + normalizeCourse(it.course) + "|" + it.code }
            .take(60)
            .sortedWith(
                compareBy<TimetableRow> { dayOrder[it.day] ?: 99 }
                    .thenBy { ParsingUtils.timeSortKey(it.time) }
                    .thenBy { it.course.lowercase(Locale.US) }
            )

        if (rows.isEmpty()) {
            val noClassesText = data.lines.firstOrNull {
                it.contains("no classes", true) ||
                    it.contains("no timetable", true) ||
                    it.contains("no schedule", true) ||
                    it.contains("no records", true)
            }
            if (noClassesText != null) {
                container.addView(EmptyState.create(activity, "No classes today", noClassesText))
            } else {
                container.addView(ErrorState.create(
                    activity,
                    "Timetable unavailable",
                    "The ERP did not provide readable class events with a day and time. Refresh to try again.",
                    onRetry
                ))
            }
            container.addView(createTimetableInfoCard())
            applyTypography(container)
            return
        }

        val now = Calendar.getInstance()
        val currentDay = dayName(now.get(Calendar.DAY_OF_WEEK))
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val runningNow = rows.firstOrNull { isRunningNow(it, currentDay, currentMinutes) }
        val nextClass = NextClassSelector.selectNext(rows, currentDay, currentMinutes)

        if (nextClass != null && hasCourseLabel(nextClass)) {
            val start = parseTimes(nextClass.time).first
            val minutesUntil = if (nextClass.day.equals(currentDay, true) && start != null && start >= currentMinutes) {
                start - currentMinutes
            } else null
            val nextClassTime = buildString {
                if (!nextClass.day.equals(currentDay, true)) append(nextClass.day + " · ")
                append(compactTime(nextClass.time))
            }.ifBlank { nextClass.day }
            container.addView(HeroCard.nextClass(
                activity,
                displayCourse(nextClass),
                nextClassTime,
                minutesUntil
            ))
        }

        val dayDates = weekDays(now, rows)
        val populatedDays = rows.mapNotNull { canonicalDay(it.day) }.toSet()
        var selectedDay = canonicalDay(currentDay)
            ?.takeIf { dayDates.containsKey(it) }
            ?: dayDates.keys.firstOrNull { it in populatedDays }
            ?: dayDates.keys.firstOrNull()
            ?: "Monday"

        val dayStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dayScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(16), dp(2), dp(16), dp(2))
            addView(dayStrip, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(7)
            }
        }
        container.addView(dayScroll)

        val agenda = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(agenda)

        fun renderSelectedDay() {
            dayStrip.removeAllViews()
            dayDates.forEach { (day, date) ->
                val hasClasses = rows.any { canonicalDay(it.day) == day }
                dayStrip.addView(createDayChip(
                    day = day,
                    date = date,
                    hasClasses = hasClasses,
                    selected = day == selectedDay
                ) {
                    selectedDay = day
                    renderSelectedDay()
                })
            }

            agenda.removeAllViews()
            val selectedRows = rows.filter { canonicalDay(it.day) == selectedDay }
                .sortedBy { ParsingUtils.timeSortKey(it.time) }
            agenda.addView(createAgendaHeader(
                selectedDay,
                selectedRows.size,
                selectedDay.equals(currentDay, true)
            ))

            if (selectedRows.isEmpty()) {
                val message = if (selectedDay.equals(currentDay, true)) {
                    "There are no class events for today in the current ERP data."
                } else {
                    "The ERP has no classes recorded for $selectedDay."
                }
                agenda.addView(EmptyState.create(activity, "No classes today", message))
            } else {
                selectedRows.forEach { row ->
                    val running = isRunningNow(row, currentDay, currentMinutes)
                    val startEnd = parseTimes(row.time)
                    val endingMinute = startEnd.second
                    val isPast = !running && (
                        dayRank(selectedDay) < dayRank(currentDay) ||
                            (selectedDay.equals(currentDay, true) && endingMinute != null && endingMinute <= currentMinutes)
                    )
                    val isNext = !isPast && !running && nextClass != null && sameClass(row, nextClass)
                    agenda.addView(createScheduleRow(
                        row = row,
                        isPast = isPast,
                        isNext = isNext,
                        isRunning = running
                    ))
                }
            }
        }

        renderSelectedDay()
        container.addView(createTimetableInfoCard())
        applyTypography(container)
    }

    private fun createDayChip(
        day: String,
        date: Calendar,
        hasClasses: Boolean,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundRect(
            if (selected) Palette.segment(activity) else Palette.card(activity),
            18f,
            if (selected) null else Palette.stroke(activity)
        )
        minimumWidth = dp(48)
        minimumHeight = dp(60)
        setPadding(dp(2), dp(6), dp(2), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            dp(48),
            dp(60)
        ).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
        isClickable = true
        isFocusable = true
        contentDescription = buildString {
            append(day)
            append(", ")
            append(date.get(Calendar.DAY_OF_MONTH))
            if (hasClasses) append(", classes scheduled")
            if (selected) append(", selected")
        }

        addView(text(day.take(3), 12f, if (selected) Palette.segmentInk(activity) else Palette.ink2(activity), false)
            .apply { gravity = Gravity.CENTER })
        addView(text(date.get(Calendar.DAY_OF_MONTH).toString(), 16f, if (selected) Palette.segmentInk(activity) else Palette.ink(activity), true)
            .apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        addView(View(activity).apply {
            background = roundRect(
                if (selected) Palette.segmentInk(activity) else Palette.ink3(activity),
                999f,
                null
            )
            visibility = if (hasClasses) View.VISIBLE else View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(3) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        setOnClickListener { onClick() }
    }

    private fun createAgendaHeader(day: String, count: Int, isToday: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(11), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(text(if (isToday) "Today" else day, 20f, Palette.ink(activity), true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(
                if (count == 1) "1 class" else "$count classes",
                13f,
                Palette.ink2(activity),
                false
            ))
        }

    private fun createScheduleRow(
        row: TimetableRow,
        isPast: Boolean,
        isNext: Boolean,
        isRunning: Boolean
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), dp(4), dp(16), dp(4)) }
        val timeParts = parseTimes(row.time)
        if (timeParts.first != null) {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumWidth = dp(56)
                layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = dp(8) }
                addView(text(formatClock(timeParts.first!!), 13f, Palette.ink(activity), true))
                if (timeParts.second != null) {
                    addView(text(formatClock(timeParts.second!!), 12f, Palette.ink2(activity), false)
                        .apply { setPadding(0, dp(3), 0, 0) })
                }
            })
        }

        val cardBackground = when {
            isRunning -> Palette.hero(activity)
            isNext -> Palette.peach(activity)
            else -> Palette.card(activity)
        }
        val cardText = if (isRunning) Palette.heroInk(activity) else Palette.ink(activity)
        val secondaryText = if (isRunning) Palette.heroInk(activity) else Palette.ink2(activity)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(
                cardBackground,
                22f,
                if (isRunning || isNext) null else Palette.stroke(activity)
            )
            minimumHeight = dp(78)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (isPast) alpha = 0.6f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text(
            displayCourse(row),
            14f,
            cardText,
            true
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        when {
            isRunning -> header.addView(Chip.createCustom(
                activity, "Now", Palette.lime(activity), Palette.limeInk(activity)
            ).apply {
                minHeight = dp(30)
                setPadding(dp(10), dp(5), dp(10), dp(5))
            })
            isNext -> {
                val minutes = timeParts.first?.takeIf { selectedDayIsToday(row.day) }
                    ?.let { start -> start - currentMinuteOfDay() }
                    ?.takeIf { it >= 0 }
                header.addView(Chip.createCustom(
                    activity,
                    if (minutes != null) "Next · $minutes min" else "Next",
                    Palette.lime(activity),
                    Palette.limeInk(activity)
                ).apply {
                    minHeight = dp(30)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                })
            }
            isPast -> {
                header.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_pt_check)
                    imageTintList = ColorStateList.valueOf(Palette.success(activity))
                    contentDescription = "Class finished"
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                })
            }
        }
        card.addView(header)
        compactCourseCode(row.code).takeIf(String::isNotBlank)?.let { code ->
            card.addView(text(code, 10f, secondaryText, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
        }
        if (isRunning) this@TimetableRenderer.applyTypography(card)
        addView(card)
        contentDescription = buildString {
            append(displayCourse(row))
            append(". ")
            if (timeParts.first != null) append("Starts at ${formatClock(timeParts.first!!)}. ")
            if (timeParts.second != null) append("Ends at ${formatClock(timeParts.second!!)}. ")
            append(when {
                isRunning -> "Now in progress"
                isPast -> "Class finished"
                isNext -> "Next class"
                else -> "Upcoming class"
            })
        }
    }

    private fun weekDays(now: Calendar, rows: List<TimetableRow>): LinkedHashMap<String, Calendar> {
        val monday = (now.clone() as Calendar)
        val daysSinceMonday = when (monday.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 6
            else -> monday.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        }
        monday.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        monday.set(Calendar.HOUR_OF_DAY, 12)
        monday.set(Calendar.MINUTE, 0)
        monday.set(Calendar.SECOND, 0)
        monday.set(Calendar.MILLISECOND, 0)

        val hasSunday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
            rows.any { canonicalDay(it.day) == "Sunday" }
        val offsets = if (hasSunday) (0..6) else (0..5)
        val result = linkedMapOf<String, Calendar>()
        offsets.forEach { offset ->
            val date = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
            result[dayName(date.get(Calendar.DAY_OF_WEEK))] = date
        }
        return result
    }

    private fun parseTimes(value: String): Pair<Int?, Int?> {
        if (value.isBlank()) return null to null
        val matches = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE)
            .findAll(value)
            .take(2)
            .toList()
        if (matches.isEmpty()) return null to null
        fun minuteOf(match: MatchResult): Int? {
            val rawHour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (minute !in 0..59) return null
            val marker = match.groupValues[3].uppercase(Locale.US)
            val hour = when {
                marker == "AM" && rawHour == 12 -> 0
                marker == "PM" && rawHour < 12 -> rawHour + 12
                marker.isNotBlank() && rawHour !in 1..12 -> return null
                marker.isBlank() && rawHour !in 0..23 -> return null
                else -> rawHour
            }
            return hour * 60 + minute
        }
        return minuteOf(matches[0]) to matches.getOrNull(1)?.let(::minuteOf)
    }

    private fun formatClock(minutes: Int): String {
        val hour = (minutes / 60).coerceIn(0, 23)
        return String.format(Locale.US, "%02d:%02d", hour, minutes % 60)
    }

    private fun compactTime(value: String): String {
        val (start, end) = parseTimes(value)
        return when {
            start != null && end != null -> "${formatClock(start)}–${formatClock(end)}"
            start != null -> formatClock(start)
            else -> ""
        }
    }

    private fun displayCourse(row: TimetableRow): String =
        row.course.trim().takeIf(String::isNotBlank) ?: row.code.trim().ifBlank { "Class" }

    private fun hasCourseLabel(row: TimetableRow): Boolean =
        row.course.isNotBlank() || row.code.isNotBlank()

    private fun compactCourseCode(value: String): String {
        val prefix = Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
            .find(value)?.value.orEmpty().replace(" ", "")
        return prefix.ifBlank { value.trim().take(30) }
    }

    private fun sameClass(first: TimetableRow, second: TimetableRow): Boolean =
        first.day.equals(second.day, true) &&
            first.time.equals(second.time, true) &&
            normalizeCourse(first.course) == normalizeCourse(second.course) &&
            first.code.equals(second.code, true)

    private fun isRunningNow(row: TimetableRow, today: String, currentMinutes: Int): Boolean {
        if (!row.day.equals(today, true)) return false
        val (start, end) = parseTimes(row.time)
        return start != null && end != null && start <= currentMinutes && currentMinutes < end
    }

    private fun selectedDayIsToday(day: String): Boolean {
        val now = Calendar.getInstance()
        return day.equals(dayName(now.get(Calendar.DAY_OF_WEEK)), true)
    }

    private fun currentMinuteOfDay(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    private fun canonicalDay(value: String): String? =
        dayOrder.keys.firstOrNull { it.equals(value.trim(), true) }

    private fun dayRank(day: String): Int = when (canonicalDay(day)) {
        "Monday" -> 1
        "Tuesday" -> 2
        "Wednesday" -> 3
        "Thursday" -> 4
        "Friday" -> 5
        "Saturday" -> 6
        "Sunday" -> 7
        else -> 99
    }

    private fun dayName(dayOfWeek: Int): String =
        dayOrder.entries.firstOrNull { it.value == dayOfWeek }?.key ?: "Monday"

    private fun normalizeCourse(value: String): String =
        value.lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "")

    private fun createTimetableInfoCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Palette.card(activity), 18f, Palette.stroke(activity))
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(12), dp(16), dp(4)) }
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_pt_clock)
                imageTintList = ColorStateList.valueOf(Palette.ink2(activity))
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) }
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Timetable information", 12f, Palette.ink(activity), true))
                addView(text(
                    "Class names, weekdays and timings come from your ERP account. Missing details are not guessed.",
                    11f,
                    Palette.ink2(activity),
                    false
                ).apply { setPadding(0, dp(4), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun applyTypography(view: View) {
        when (view) {
            is TextView -> {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = if (style and Typeface.BOLD != 0) {
                    Typeface.create(mediumTypeface, Typeface.BOLD)
                } else regularTypeface
                view.includeFontPadding = false
            }
            is ViewGroup -> for (index in 0 until view.childCount) applyTypography(view.getChildAt(index))
        }
    }

    private fun text(value: String, size: Float, color: Int, medium: Boolean): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (medium) mediumTypeface else regularTypeface
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    private fun roundRect(color: Int, radiusDp: Float, strokeColor: Int?): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun roundRect(
        context: Context,
        color: Int,
        radiusDp: Float,
        strokeColor: Int?
    ): GradientDrawable = roundRect(color, radiusDp, strokeColor)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
