package com.superiorstudent.app.feature.timetable.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.data.model.TimetableRow
import com.superiorstudent.app.data.parser.TimetableParser
import com.superiorstudent.app.ui.Palette
import kotlin.math.roundToInt

/**
 * Timetable-specific UI. Parsing is in TimetableParser, while Activity keeps
 * navigation and ERP session/WebView management.
 */
class TimetableRenderer(private val activity: Context) {

    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val rows = data.records.mapNotNull(TimetableParser::toRow)
            .distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.room + "|" + it.code }
            .take(60)

        container.addView(createTimetableToolbar(rows.size))
        if (rows.isEmpty()) {
            data.tables.forEachIndexed { index, table ->
                container.addView(
                    createTableSection(
                        table.title.ifBlank { if (index == 0) "Class schedule" else "Schedule details" },
                        table
                    )
                )
            }
            container.addView(
                createEmptyState(
                    "Timetable is still syncing",
                    "The ERP timetable is loaded dynamically. Tap Refresh and the app will retry the live schedule."
                )
            )
            return
        }

        val dayOrder = mapOf(
            "Monday" to 1, "Tuesday" to 2, "Wednesday" to 3,
            "Thursday" to 4, "Friday" to 5, "Saturday" to 6, "Sunday" to 7
        )
        val sortedRows = rows.sortedWith(
            compareBy<TimetableRow> { row ->
                dayOrder.entries.firstOrNull { entry -> row.day.equals(entry.key, true) }?.value ?: 99
            }.thenBy { row -> ParsingUtils.timeSortKey(row.time) }
        )

        sortedRows.groupBy { it.day.ifBlank { "Scheduled classes" } }
            .toSortedMap(compareBy { dayOrder[it] ?: 99 })
            .forEach { (day, items) ->
                container.addView(createDayHeader(day, items.size))
                items.forEach { row -> container.addView(createScheduleCard(row)) }
            }

        container.addView(createTimetableInfoCard())
    }

    private fun createTimetableToolbar(count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(Palette.primary(activity), Palette.violet(activity), 24f)
            elevation = dp(5).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }

            addView(TextView(activity).apply {
                text = "🗓"
                gravity = Gravity.CENTER
                textSize = 19f
                background = roundedBackground(Color.argb(60, 255, 255, 255), 14f)
                minWidth = dp(46)
                minHeight = dp(46)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
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
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.12f
                })
                addView(TextView(activity).apply {
                    text = "Your Class Schedule"
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(1))
                })
                addView(TextView(activity).apply {
                    text = if (count == 1) "1 class synced from ERP" else "$count classes synced from ERP"
                    setTextColor(Color.argb(210, 255, 255, 255))
                    textSize = 11f
                })
            })

            addView(TextView(activity).apply {
                text = "LIVE"
                setTextColor(Color.WHITE)
                textSize = 9f
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.argb(60, 255, 255, 255), 20f)
                setPadding(dp(11), dp(6), dp(11), dp(6))
            })
        }

    private fun createTimetableInfoCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.infoBg(activity), 18f)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(18)) }

            addView(TextView(activity).apply {
                text = "ⓘ"
                setTextColor(Palette.primary(activity))
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "Timetable Information"
                    setTextColor(Palette.primaryDark(activity))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = "Timings, courses, codes and rooms are read from your ERP account."
                    setTextColor(Palette.ink700(activity))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun createDayHeader(day: String, count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(14), dp(14), dp(14), dp(4)) }

            addView(View(activity).apply {
                background = gradientBackground(Palette.primary(activity), Palette.accent(activity), 4f)
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(20)).apply {
                    setMargins(0, 0, dp(9), 0)
                }
            })
            addView(TextView(activity).apply {
                text = day.uppercase(java.util.Locale.US)
                setTextColor(Palette.ink900(activity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(activity).apply {
                text = "$count"
                setTextColor(Palette.primaryDark(activity))
                textSize = 10f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(activity), 16f)
                minWidth = dp(26)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
            addView(TextView(activity).apply {
                text = if (count == 1) "class" else "classes"
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(dp(6), 0, 0, 0)
            })
        }

    private fun createScheduleCard(row: TimetableRow): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.surface(activity), 20f)
            elevation = dp(3).toFloat()
            setPadding(dp(12), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val accent = when (ParsingUtils.timeSortKey(row.time) % 5) {
                0 -> Palette.primary(activity)
                1 -> Palette.success(activity)
                2 -> Palette.warning(activity)
                3 -> Palette.violet(activity)
                else -> Palette.danger(activity)
            }

            addView(FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(86), dp(76)).apply {
                    setMargins(0, 0, dp(11), 0)
                }
                addView(View(activity).apply {
                    background = gradientBackground(
                        tintAlpha(accent, 26),
                        tintAlpha(Palette.ink900(activity), 26),
                        18f
                    )
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
                addView(View(activity).apply {
                    background = roundedBackground(accent, 3f)
                    layoutParams = FrameLayout.LayoutParams(dp(34), dp(4)).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    }
                })
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPadding(dp(4), dp(9), dp(4), dp(4))
                    addView(TextView(activity).apply {
                        text = ParsingUtils.formatScheduleTime(row.time).ifBlank { "—" }
                        gravity = Gravity.CENTER
                        setTextColor(Palette.ink900(activity))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(activity).apply {
                        text = row.day.take(3).uppercase(java.util.Locale.US).ifBlank { "TBD" }
                        gravity = Gravity.CENTER
                        setTextColor(Palette.ink500(activity))
                        textSize = 9f
                        letterSpacing = 0.12f
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            })

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = row.course
                    setTextColor(Palette.ink900(activity))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 2
                })
                val metaRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(7), 0, 0) }
                }
                if (row.code.isNotBlank()) metaRow.addView(TextView(activity).apply {
                    text = row.code
                    setTextColor(Palette.primaryDark(activity))
                    textSize = 9f
                    letterSpacing = 0.05f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Palette.infoBg(activity), 9f)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                })
                metaRow.addView(TextView(activity).apply {
                    text = if (row.room.isNotBlank()) "📍 ${row.room}" else "📍 Room TBA"
                    setTextColor(if (row.room.isNotBlank()) Palette.success(activity) else Palette.ink400(activity))
                    textSize = 10f
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium",
                        if (row.room.isNotBlank()) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                    )
                    setPadding(if (row.code.isNotBlank()) dp(9) else 0, 0, 0, 0)
                })
                addView(metaRow)
            })
        }

    private fun createTableSection(title: String, table: TableData): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(7), dp(12), dp(7)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.ink900(activity))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            table.rows.take(80).forEach { row ->
                val lineText = row.mapIndexed { index, value ->
                    val label = table.headers.getOrNull(index).orEmpty()
                    if (label.isBlank()) value else "$label: $value"
                }.filter(String::isNotBlank).joinToString(" • ")
                if (lineText.isNotBlank()) addView(TextView(activity).apply {
                    text = lineText
                    setTextColor(Palette.ink700(activity))
                    textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            }
        }

    private fun createEmptyState(title: String, message: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(1).toFloat()
            setPadding(dp(24), dp(30), dp(24), dp(30))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.primaryDark(activity))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
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

    private fun roundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun gradientBackground(startColor: Int, endColor: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(startColor, endColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun tintAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
