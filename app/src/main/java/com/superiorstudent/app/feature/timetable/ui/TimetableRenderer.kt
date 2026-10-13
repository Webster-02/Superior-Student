package com.superiorstudent.app.feature.timetable.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TimetableRow
import com.superiorstudent.app.data.parser.TimetableParser
import com.superiorstudent.app.feature.timetable.domain.NextClassSelector
import com.superiorstudent.app.ui.Palette
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mobile-first timetable agenda. Calendar event records are preferred over
 * surrounding ERP text, and room/location is intentionally not shown because
 * the ERP data supplied for these students does not reliably include it.
 */
class TimetableRenderer(private val activity: Context) {

    private val typeface: Typeface
        get() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val dayOrder = linkedMapOf(
        "Monday" to 1,
        "Tuesday" to 2,
        "Wednesday" to 3,
        "Thursday" to 4,
        "Friday" to 5,
        "Saturday" to 6,
        "Sunday" to 7
    )

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val rows = TimetableParser.toRows(data.records)
            .distinctBy { it.day + "|" + it.time + "|" + normalizeCourse(it.course) + "|" + it.code }
            .take(60)
            .sortedWith(
                compareBy<TimetableRow> { dayOrder[it.day] ?: 99 }
                    .thenBy { ParsingUtils.timeSortKey(it.time) }
                    .thenBy { it.course.lowercase(Locale.US) }
            )

        container.addView(createTimetableToolbar(rows.size))
        if (rows.isEmpty()) {
            container.addView(
                createEmptyState(
                    "Timetable details unavailable",
                    "The ERP page loaded, but the app could not identify separate class events with a day and time. Tap Refresh to retry the live schedule."
                )
            )
            container.addView(createTimetableInfoCard())
            return
        }

        val now = Calendar.getInstance()
        val currentDay = dayOrder.entries.firstOrNull {
            it.value == if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
            else now.get(Calendar.DAY_OF_WEEK) - 1
        }?.key ?: "Monday"
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val nextClass = NextClassSelector.selectNext(rows, currentDay, currentMinutes)
        container.addView(
            nextClass?.let(::createNextClassCard)
                ?: createNextClassUnavailableCard()
        )

        val populatedDays = rows.map { it.day }.filter(String::isNotBlank).toSet()
        val visibleDays = dayOrder.keys.filter { day ->
            dayOrder[day]!! <= 5 || day in populatedDays
        }.toMutableList()
        if (rows.any { it.day.isBlank() }) visibleDays.add("Schedule details")

        val counts = rows.groupingBy { it.day.ifBlank { "Schedule details" } }.eachCount()
        var selectedDay = when {
            currentDay in visibleDays -> currentDay
            visibleDays.any { counts.getOrDefault(it, 0) > 0 } ->
                visibleDays.first { counts.getOrDefault(it, 0) > 0 }
            else -> visibleDays.first()
        }

        container.addView(
            createSectionHeading(
                "YOUR WEEK",
                "Choose a day to see its classes"
            )
        )

        val dayChips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dayScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(8), dp(2), dp(8), dp(2))
            addView(
                dayChips,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
            dayChips.removeAllViews()
            visibleDays.forEach { day ->
                val dayRows = rows.filter {
                    if (day == "Schedule details") it.day.isBlank() else it.day.equals(day, true)
                }
                dayChips.addView(
                    createDayChip(
                        day = day,
                        count = dayRows.size,
                        selected = day == selectedDay
                    ) {
                        selectedDay = day
                        renderSelectedDay()
                    }
                )
            }

            agenda.removeAllViews()
            val selectedRows = rows.filter {
                if (selectedDay == "Schedule details") it.day.isBlank()
                else it.day.equals(selectedDay, true)
            }.sortedBy { ParsingUtils.timeSortKey(it.time) }

            agenda.addView(createAgendaHeader(selectedDay, selectedRows.size))
            if (selectedRows.isEmpty()) {
                agenda.addView(
                    createEmptyState(
                        "No classes listed",
                        "The ERP has no classes recorded for $selectedDay."
                    )
                )
            } else {
                selectedRows.forEachIndexed { index, row ->
                    agenda.addView(createScheduleCard(row, index))
                }
            }
        }
        renderSelectedDay()
        container.addView(createTimetableInfoCard())
    }

    private fun createTimetableToolbar(count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(Palette.primary(activity), Palette.violet(activity), 24f)
            elevation = dp(4).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }

            addView(TextView(activity).apply {
                text = "🗓"
                gravity = Gravity.CENTER
                textSize = 21f
                background = roundedBackground(Color.argb(60, 255, 255, 255), 15f)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(0, 0, dp(13), 0)
                }
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "THIS WEEK"
                    setTextColor(Palette.primaryLight(activity))
                    textSize = 9f
                    letterSpacing = 0.12f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = "Your Class Schedule"
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(1))
                })
                addView(TextView(activity).apply {
                    text = if (count == 1) "1 class synced from ERP" else "$count classes synced from ERP"
                    setTextColor(Color.argb(220, 255, 255, 255))
                    textSize = 11f
                })
            })
            addView(TextView(activity).apply {
                text = "LIVE"
                setTextColor(Color.WHITE)
                textSize = 9f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(Color.argb(60, 255, 255, 255), 20f)
                setPadding(dp(11), dp(7), dp(11), dp(7))
            })
        }

    private fun createNextClassCard(row: TimetableRow): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(Palette.primary(activity), Palette.violet(activity), 22f)
            elevation = dp(4).toFloat()
            setPadding(dp(17), dp(16), dp(17), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(6), dp(12), dp(10)) }

            addView(TextView(activity).apply {
                text = "NEXT CLASS"
                setTextColor(Palette.primaryLight(activity))
                textSize = 10f
                letterSpacing = 0.15f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = row.course
                setTextColor(Color.WHITE)
                textSize = 19f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 3
                setPadding(0, dp(5), 0, dp(8))
            })
            addView(TextView(activity).apply {
                text = "▦  ${row.day}    ◷  ${compactTime(row.time)}"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, 0, 0, dp(6))
            })
            compactCourseCode(row.code).takeIf(String::isNotBlank)?.let { code ->
                addView(TextView(activity).apply {
                    text = code
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                    background = roundedBackground(Color.argb(55, 255, 255, 255), 10f)
                    setPadding(dp(9), dp(5), dp(9), dp(5))
                })
            }
        }

    private fun createNextClassUnavailableCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.infoBg(activity), 18f)
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(6), dp(12), dp(10)) }
            addView(TextView(activity).apply {
                text = "◷"
                textSize = 24f
                setTextColor(Palette.primary(activity))
                layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = "Next class unavailable"
                    setTextColor(Palette.primaryDark(activity))
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = "ERP has not provided a reliable weekday and start time for an upcoming class."
                    setTextColor(Palette.ink700(activity))
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun createSectionHeading(title: String, subtitle: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(13), dp(16), dp(6)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = subtitle
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun createDayChip(
        day: String,
        count: Int,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedBackground(
            if (selected) Palette.primary(activity) else Palette.surfaceAlt(activity),
            15f
        )
        elevation = if (selected) dp(2).toFloat() else 0f
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minWidth = dp(91)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        isClickable = true
        isFocusable = true
        addView(TextView(activity).apply {
            text = if (day == "Schedule details") "OTHER" else day.take(3).uppercase(Locale.US)
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else Palette.ink900(activity))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(activity).apply {
            text = when {
                count == 0 -> "No classes"
                count == 1 -> "1 class"
                else -> "$count classes"
            }
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.argb(225, 255, 255, 255) else Palette.ink500(activity))
            textSize = 10f
            setPadding(0, dp(4), 0, 0)
        })
        setOnClickListener { onClick() }
    }

    private fun createAgendaHeader(day: String, count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(13), dp(16), dp(5)) }
            addView(TextView(activity).apply {
                text = day + " · " + count + if (count == 1) " class" else " classes"
                setTextColor(Palette.ink900(activity))
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(activity).apply {
                text = "ERP"
                setTextColor(Palette.primaryDark(activity))
                textSize = 9f
                letterSpacing = 0.1f
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(activity), 10f)
                setPadding(dp(9), dp(5), dp(9), dp(5))
            })
        }

    private fun createScheduleCard(row: TimetableRow, index: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.surface(activity), 20f)
            elevation = dp(2).toFloat()
            setPadding(dp(10), dp(10), dp(13), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val accent = when (index % 4) {
                0 -> Palette.accent(activity)
                1 -> Palette.violet(activity)
                2 -> Palette.success(activity)
                else -> Palette.warning(activity)
            }

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBackground(tintAlpha(accent, 24), 16f)
                setPadding(dp(7), dp(12), dp(7), dp(12))
                minimumHeight = dp(82)
                layoutParams = LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, dp(12), 0)
                }
                addView(View(activity).apply {
                    background = roundedBackground(accent, 3f)
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(4)).apply {
                        setMargins(0, 0, 0, dp(7))
                    }
                })
                val formatted = ParsingUtils.formatScheduleTime(row.time)
                addView(TextView(activity).apply {
                    text = if (row.time.isBlank()) "Time unavailable" else formatted
                    gravity = Gravity.CENTER
                    setTextColor(Palette.ink900(activity))
                    textSize = if (row.time.contains("-") || row.time.contains("–")) 12f else 13f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 3
                })
            })

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = row.course
                    setTextColor(Palette.ink900(activity))
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 3
                })
                compactCourseCode(row.code).takeIf(String::isNotBlank)?.let { code ->
                    addView(TextView(activity).apply {
                        text = code
                        setTextColor(Palette.primaryDark(activity))
                        textSize = 10f
                        setTypeface(typeface, Typeface.BOLD)
                        background = roundedBackground(Palette.infoBg(activity), 10f)
                        setPadding(dp(8), dp(5), dp(8), dp(5))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, dp(7), 0, 0) }
                    })
                }
            })
        }

    private fun createTimetableInfoCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.infoBg(activity), 18f)
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(13), dp(12), dp(18)) }
            addView(TextView(activity).apply {
                text = "ⓘ"
                setTextColor(Palette.primary(activity))
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "Timetable information"
                    setTextColor(Palette.primaryDark(activity))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = "Class names, days and timings are read from your ERP account. Unavailable details are not guessed."
                    setTextColor(Palette.ink700(activity))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun createEmptyState(title: String, message: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(1).toFloat()
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.primaryDark(activity))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(activity).apply {
                text = message
                setTextColor(Palette.ink500(activity))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
        }

    private fun compactCourseCode(value: String): String {
        val prefix = Regex("""\b[A-Z]{2,8}[- ]?\d{3,}""", RegexOption.IGNORE_CASE)
            .find(value)?.value.orEmpty().replace(" ", "")
        return prefix.ifBlank { value.trim().take(30) }
    }

    private fun compactTime(value: String): String =
        ParsingUtils.formatScheduleTime(value).replace("\n–\n", " – ").replace("\n", " ")

    private fun normalizeCourse(value: String): String =
        value.lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "")

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun gradientBackground(startColor: Int, endColor: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(startColor, endColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun tintAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
