package com.superiorstudent.app.feature.attendance.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.superiorstudent.app.core.config.ErpConfig
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.AttendanceDetailData
import com.superiorstudent.app.feature.attendance.presentation.AttendanceViewModel
import com.superiorstudent.app.feature.attendance.domain.AttendanceSubjectMatcher
import com.superiorstudent.app.feature.attendance.domain.AttendanceCalculator
import com.superiorstudent.app.feature.attendance.domain.AttendanceProjection
import com.superiorstudent.app.feature.attendance.domain.AttendanceSubjectRowMapper
import com.superiorstudent.app.data.model.AttendanceDetailLink
import com.superiorstudent.app.data.model.AttendanceSession
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.ui.Palette
import kotlin.math.roundToInt

/**
 * Attendance-specific presentation. It owns attendance cards, detail history,
 * and planner UI; navigation/state remain in MainActivity via callbacks.
 */
class AttendanceRenderer(
    private val activity: Context,
    private val viewModel: AttendanceViewModel,
    private val onOpenDetail: (String, String, String) -> Unit,
    private val onShowSummary: () -> Unit
) {
    private lateinit var content: LinearLayout
    private val typeface: Typeface
        get() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun render(container: LinearLayout, data: ModuleData) {
        content = container
        renderAttendance(data)
    }

    private enum class HistoryFilter(val label: String) {
        ALL("All"), PRESENT("Present"), ABSENT("Absent")
    }

    private data class AttendanceRow(
         val course: String,
        val code: String,
        val percent: Double?,
        val present: String,
        val total: String,
        val detailUrl: String = ""
    )

    private fun renderAttendance(data: ModuleData) {
        if (data.attendanceDetail != null) {
            renderAttendanceDetail(data.attendanceDetail)
            return
        }

        // The ERP summary already provides a subject-specific href for each card.
        // Use those links as the source of truth so a parsed summary row can never
        // accidentally inherit another subject's URL. Use matcher-based parsing only
        // for ERP variants which do not expose any direct detail links.
        val linkedRows = AttendanceSubjectRowMapper.fromLinks(data.attendanceDetailLinks)
            .map { link ->
                AttendanceRow(
                    course = link.course,
                    code = link.code,
                    percent = link.percentage,
                    present = "",
                    total = "",
                    detailUrl = link.url
                )
            }

        val rows = if (data.attendanceDetailLinks.isNotEmpty()) {
            // When direct links exist, they are authoritative. Do not silently
            // replace contaminated per-subject links with one merged raw record.
            linkedRows.take(20)
        } else {
            data.records.mapNotNull(::toAttendanceRow)
                .distinctBy { it.course + "|" + it.code }
                .take(20)
                .map { row ->
                    val link = AttendanceSubjectMatcher.findMatchingLink(
                        row.course,
                        row.code,
                        data.attendanceDetailLinks
                    )
                    row.copy(detailUrl = link?.url.orEmpty())
                }
        }

        if (rows.isNotEmpty()) {
            val percentages = rows.mapNotNull { it.percent }
            content.addView(createAttendanceSummary(data.overallAttendance, percentages, rows.size))
            val belowTarget = rows.filter { it.percent != null && it.percent < AttendanceCalculator.TARGET_PERCENTAGE }
            if (belowTarget.isNotEmpty()) {
                val names = belowTarget.take(3).joinToString(", ") { it.course }
                val suffix = if (belowTarget.size > 3) " and " + (belowTarget.size - 3) + " more" else ""
                content.addView(createAttendancePlannerNotice(
                    "${belowTarget.size} subject(s) need attention",
                    names + suffix + " are below the 75% target. Open a subject to see the exact recovery plan.",
                    Palette.danger(activity),
                    Palette.dangerBg(activity)
                ))
            }
            rows.forEachIndexed { index, row -> content.addView(createAttendanceCard(row, index + 1)) }
        } else if (data.attendanceDetailLinks.isNotEmpty()) {
            content.addView(createEmptyState(
                "Attendance subjects need a refresh",
                "The ERP returned subject links without separate course details. Refresh to fetch clean subject rows."
            ))
        } else {
            data.tables.forEachIndexed { index, table ->
                content.addView(createTableSection(
                    table.title.ifBlank { if (index == 0) "Attendance records" else "Attendance details" }, table
                ))
            }
            if (data.tables.isEmpty()) content.addView(createEmptyState(
                "Attendance data is unavailable",
                "The ERP summary did not expose subject detail links or readable attendance records. Refresh and try again."
            ))
        }
    }

    private fun normalizeAttendanceMatch(value: String): String =
        value.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]+"), "")

    private fun createAttendanceLinkCard(link: AttendanceDetailLink, position: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenDetail(link.url, link.course, link.code) }
            addView(TextView(activity).apply {
                text = link.course.ifBlank { "Subject " + position }
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (link.code.isNotBlank()) addView(TextView(activity).apply {
                text = link.code
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(0, dp(3), 0, 0)
            })
            addView(TextView(activity).apply {
                text = "Open class records and 75% planner  ›"
                setTextColor(Palette.primary(activity))
                textSize = 11f
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun renderAttendanceDetail(detail: AttendanceDetailData) {
        val state = viewModel.detailUiState(detail)
        val countsValid = state.countsValid
        val present = state.attended
        val conducted = state.conducted
        val calculatedPercent = state.calculatedPercentage
        val percent = state.displayPercentage
        val needed = state.classesNeededToReachTarget
        val reached = state.targetReached
        val safeLeaves = state.safeFutureLeaves

        val back = TextView(activity).apply {
            text = "‹  All subjects"
            setTextColor(Palette.primary(activity))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onShowSummary() }
        }
        content.addView(back)

        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(Palette.primaryDark(activity), Palette.primary(activity), 22f)
            elevation = dp(3).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(10)) }
            addView(TextView(activity).apply {
                text = detail.course.ifBlank { "Subject attendance" }
                setTextColor(Color.WHITE)
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (detail.code.isNotBlank()) addView(TextView(activity).apply {
                text = detail.code
                setTextColor(Color.argb(215, 255, 255, 255))
                textSize = 10f
                setPadding(0, dp(4), 0, 0)
            })
            addView(TextView(activity).apply {
                text = percent?.let { String.format(java.util.Locale.US, "%.1f%% attendance", it) }
                    ?: "Attendance percentage unavailable"
                setTextColor(Color.WHITE)
                textSize = 25f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(3))
            })
            if (countsValid) addView(TextView(activity).apply {
                text = present.toString() + " of " + conducted + " classes attended"
                setTextColor(Color.argb(225, 255, 255, 255))
                textSize = 12f
            })
        }
        content.addView(hero)

        if (countsValid) {
            val stats = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(8), 0, dp(8), dp(8)) }
            }
            stats.addView(createAttendancePlannerMetric("Conducted", conducted.toString(), "Classes held", Palette.primary(activity)))
            stats.addView(createAttendancePlannerMetric("Attended", present.toString(), "Present classes", Palette.success(activity)))
            stats.addView(createAttendancePlannerMetric("Missed", (conducted - present).toString(), "Conducted − attended", Palette.danger(activity)))
            content.addView(stats)

            state.projections.firstOrNull()?.let { next ->
                content.addView(createSectionHeading(
                    "Next class impact",
                    "See the result if your next scheduled class is attended or missed"
                ))
                val impactRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dp(8), 0, dp(8), dp(4)) }
                }
                impactRow.addView(createAttendanceProjectionCard(
                    title = "If you attend",
                    percentage = next.percentageIfAttendAll,
                    attended = next.attendedIfAttendAll,
                    total = next.totalAfterFutureClasses,
                    targetMet = next.targetMetIfAttendAll
                ))
                impactRow.addView(createAttendanceProjectionCard(
                    title = "If you miss",
                    percentage = next.percentageIfMissAll,
                    attended = next.attendedIfMissAll,
                    total = next.totalAfterFutureClasses,
                    targetMet = next.targetMetIfMissAll
                ))
                content.addView(impactRow)
                content.addView(createSectionHeading(
                    "Plan the next 4 classes",
                    "Compare attending every class with missing every class"
                ))
                state.projections.forEach { projection ->
                    content.addView(createProjectionComparisonRow(projection))
                }
                content.addView(TextView(activity).apply {
                    text = "Forecasts assume each future class is conducted. They are calculations, not confirmed class dates."
                    setTextColor(Palette.ink500(activity))
                    textSize = 10f
                    setPadding(dp(14), dp(3), dp(14), dp(8))
                })
            }

            val neededCount = needed ?: 0
            val nextPercent = state.projectedPercentage ?: calculatedPercent ?: 0.0
            val targetMessage = if (reached) {
                "Your current attendance is at or above 75%. Keep attending to protect your margin."
            } else {
                "Attend " + neededCount + " more consecutive classes without missing any. Your attendance will become " +
                    String.format(java.util.Locale.US, "%.1f%%", nextPercent) + "."
            }
            content.addView(createAttendancePlannerNotice(
                if (reached) "75% target reached" else "Classes needed to reach 75%",
                targetMessage,
                if (reached) Palette.success(activity) else Palette.primary(activity),
                if (reached) Palette.successBg(activity) else Palette.infoBg(activity)
            ))

            val leaveText = if (reached && safeLeaves!! > 0) {
                "You can miss up to " + safeLeaves + " more " +
                    (if (safeLeaves == 1) "class" else "classes") + " and stay at or above 75%."
            } else if (reached) {
                "There is no spare leave available if you want to maintain at least 75%."
            } else {
                "No additional leaves are safe right now. Attend the required classes first."
            }
            content.addView(createAttendancePlannerNotice(
                if (reached) "Safe future leaves" else "Leaves available right now",
                leaveText,
                if (reached && safeLeaves!! > 0) Palette.success(activity) else Palette.danger(activity),
                if (reached && safeLeaves!! > 0) Palette.successBg(activity) else Palette.dangerBg(activity)
            ))
        } else {
            content.addView(createEmptyState(
                "Attendance planner needs class counts",
                "This ERP page did not return both 'Number of classes Conducted' and 'Number of classes Attended'. The ERP percentage is shown above, but class predictions are withheld to avoid guessing."
            ))
        }

        if (detail.sessions.isNotEmpty()) {
            val presentSessions = detail.sessions.count(::isPresentSession)
            val absentSessions = detail.sessions.count(::isAbsentSession)
            val historyStats = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(8), 0, dp(8), dp(6)) }
            }
            historyStats.addView(createAttendancePlannerMetric(
                "Present", presentSessions.toString(), "Recorded sessions", Palette.success(activity)
            ))
            historyStats.addView(createAttendancePlannerMetric(
                "Absent", absentSessions.toString(), "Recorded sessions", Palette.danger(activity)
            ))
            historyStats.addView(createAttendancePlannerMetric(
                "Records", detail.sessions.size.toString(), "From ERP", Palette.primary(activity)
            ))
            content.addView(createSectionHeading(
                "History snapshot", "Recorded class outcomes from your ERP account"
            ))
            content.addView(historyStats)
        }

        content.addView(createSectionHeading(
            "Class-by-class history", "Filter recorded dates by attendance status"
        ))
        if (detail.sessions.isNotEmpty()) {
            val filterRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(10), dp(2), dp(10), dp(4)) }
            }
            val historyContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            content.addView(filterRow)
            content.addView(historyContainer)

            fun renderHistory(filter: HistoryFilter) {
                filterRow.removeAllViews()
                HistoryFilter.values().forEach { option ->
                    filterRow.addView(createHistoryFilterChip(option.label, option == filter) {
                        renderHistory(option)
                    })
                }
                historyContainer.removeAllViews()
                val filtered = detail.sessions.filter { session ->
                    when (filter) {
                        HistoryFilter.ALL -> true
                        HistoryFilter.PRESENT -> isPresentSession(session)
                        HistoryFilter.ABSENT -> isAbsentSession(session)
                    }
                }
                historyContainer.addView(TextView(activity).apply {
                    text = "Showing " + filtered.size + " of " + detail.sessions.size + " recorded sessions"
                    setTextColor(Palette.ink500(activity))
                    textSize = 10f
                    setPadding(dp(14), dp(4), dp(14), dp(4))
                })
                if (filtered.isEmpty()) {
                    historyContainer.addView(createEmptyState(
                        "No " + filter.label.lowercase(java.util.Locale.US) + " records",
                        "The ERP detail page does not contain any sessions in this category."
                    ))
                } else {
                    filtered.forEachIndexed { index, session ->
                        historyContainer.addView(createAttendanceSessionCard(session, index + 1))
                    }
                }
            }
            renderHistory(HistoryFilter.ALL)
        } else {
            content.addView(createEmptyState(
                "Class history unavailable",
                "The class totals were read, but the individual date/status rows could not be extracted."
            ))
        }
    }

    private fun createAttendanceProjectionCard(
        title: String,
        percentage: Double,
        attended: Int,
        total: Int,
        targetMet: Boolean
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(
            if (targetMet) Palette.successBg(activity) else Palette.dangerBg(activity), 16f
        )
        setPadding(dp(12), dp(13), dp(10), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }

        addView(TextView(activity).apply {
            text = title
            setTextColor(if (targetMet) Palette.success(activity) else Palette.danger(activity))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(activity).apply {
            text = String.format(java.util.Locale.US, "%.1f%%", percentage)
            setTextColor(if (targetMet) Palette.success(activity) else Palette.danger(activity))
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(2))
        })
        addView(TextView(activity).apply {
            text = "$attended of $total classes"
            setTextColor(Palette.ink700(activity))
            textSize = 10f
        })
        addView(TextView(activity).apply {
            text = if (targetMet) "75% target maintained" else "Below 75% target"
            setTextColor(if (targetMet) Palette.success(activity) else Palette.danger(activity))
            textSize = 9f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
        })
    }

    private fun createProjectionComparisonRow(projection: AttendanceProjection): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 15f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(3), dp(12), dp(4)) }
            setPadding(dp(10), dp(9), dp(10), dp(8))

            addView(TextView(activity).apply {
                text = "Next " + projection.futureClasses +
                    (if (projection.futureClasses == 1) " class" else " classes")
                setTextColor(Palette.ink900(activity))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(3), 0, 0, dp(4))
            })
            val scenarios = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            scenarios.addView(createProjectionCompactCell(
                "Attend all",
                projection.percentageIfAttendAll,
                projection.attendedIfAttendAll,
                projection.totalAfterFutureClasses,
                projection.targetMetIfAttendAll
            ))
            scenarios.addView(createProjectionCompactCell(
                "Miss all",
                projection.percentageIfMissAll,
                projection.attendedIfMissAll,
                projection.totalAfterFutureClasses,
                projection.targetMetIfMissAll
            ))
            addView(scenarios)
        }

    private fun createProjectionCompactCell(
        title: String,
        percentage: Double,
        attended: Int,
        total: Int,
        targetMet: Boolean
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(dp(3), 0, dp(3), 0) }
        background = roundedBackground(
            if (targetMet) Palette.successBg(activity) else Palette.dangerBg(activity), 10f
        )
        setPadding(dp(9), dp(8), dp(7), dp(8))
        addView(TextView(activity).apply {
            text = title
            setTextColor(Palette.ink700(activity))
            textSize = 10f
        })
        addView(TextView(activity).apply {
            text = String.format(java.util.Locale.US, "%.1f%%", percentage)
            setTextColor(if (targetMet) Palette.success(activity) else Palette.danger(activity))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        addView(TextView(activity).apply {
            text = "$attended / $total attended"
            setTextColor(Palette.ink500(activity))
            textSize = 9f
        })
        addView(TextView(activity).apply {
            text = if (targetMet) "Target safe" else "Below 75%"
            setTextColor(if (targetMet) Palette.success(activity) else Palette.danger(activity))
            textSize = 9f
            setPadding(0, dp(3), 0, 0)
        })
    }

    private fun createHistoryFilterChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View = TextView(activity).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.WHITE else Palette.ink700(activity))
        textSize = 11f
        setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        setPadding(dp(6), dp(9), dp(6), dp(9))
        background = roundedBackground(
            if (selected) Palette.primary(activity) else Palette.surfaceAlt(activity), 18f
        )
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun isPresentSession(session: AttendanceSession): Boolean {
        val status = session.status.trim().uppercase(java.util.Locale.US)
        return status == "P" || status == "PRESENT"
    }

    private fun isAbsentSession(session: AttendanceSession): Boolean {
        val status = session.status.trim().uppercase(java.util.Locale.US)
        return status == "A" || status == "AB" || status == "ABSENT" || status.contains("ABSENT")
    }

    private fun createAttendancePlannerMetric(label: String, value: String, subtitle: String, accent: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            addView(TextView(activity).apply {
                text = label.uppercase(java.util.Locale.US)
                setTextColor(Palette.ink500(activity))
                textSize = 9f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = value
                setTextColor(accent)
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, 0)
            })
            addView(TextView(activity).apply {
                text = subtitle
                setTextColor(Palette.ink500(activity))
                textSize = 9f
                setPadding(0, dp(3), 0, 0)
            })
        }

    private fun createAttendancePlannerNotice(title: String, message: String, accent: Int, fill: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(fill, 16f)
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(accent)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = message
                setTextColor(Palette.ink900(activity))
                textSize = 11f
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun createAttendanceSessionCard(session: AttendanceSession, position: Int): View {
        val status = session.status.trim().uppercase(java.util.Locale.US)
        val isPresent = status == "P" || status == "PRESENT"
        val isAbsent = status == "A" || status == "AB" || status.contains("ABSENT")
        val color = when {
            isPresent -> Palette.success(activity)
            isAbsent -> Palette.danger(activity)
            else -> Palette.ink500(activity)
        }
        val statusLabel = when {
            isPresent -> "Present (" + status + ")"
            isAbsent -> "Absent (" + status + ")"
            else -> if (status.isBlank()) "Unknown" else status
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.surface(activity), 13f)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(3), dp(12), dp(3)) }
            addView(TextView(activity).apply {
                text = position.toString() + ".  " + session.date
                setTextColor(Palette.ink900(activity))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(activity).apply {
                text = statusLabel
                setTextColor(color)
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun toAttendanceRow(record: ModuleRecord): AttendanceRow? {
        val values = record.values.map(ParsingUtils::cleanDisplayText)
        if (values.none(String::isNotBlank)) return null
        val headers = record.headers.map(ParsingUtils::cleanDisplayText)
        val joined = values.filter(String::isNotBlank).joinToString(" ")
        if (joined.contains("session", true) || joined.contains("inactive", true) || joined.contains("stay online", true)) return null
        val percentCount = Regex("""d{1,3}(?:.d+)?s*%""").findAll(joined).count()
        val codeCount = Regex("""[A-Z]{2,8}[- ]?d{3,}[A-Z0-9-]*""", RegexOption.IGNORE_CASE)
            .findAll(joined).count()
        if (percentCount > 1 || codeCount > 1 ||
            joined.contains("Academic Progress", true) || joined.contains("Credits Completed", true)) return null

        fun valueFor(vararg keys: String): String {
            val index = headers.indexOfFirst { header -> keys.any { key -> header.contains(key, true) } }
            return if (index >= 0 && index < values.size) values[index] else ""
        }

        val courseFromHeader = valueFor("subject", "course name", "course", "class name")
        val codeFromHeader = valueFor("code", "course code", "subject code")
        val percentFromHeader = valueFor("percentage", "attendance %", "attendance percentage", "percent")
        val presentFromHeader = valueFor("present", "attended", "classes attended")
        val totalFromHeader = valueFor("total", "total classes", "classes held")

        val percent = ParsingUtils.parsePercent(percentFromHeader)
            ?: Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""").find(joined)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)

        val code = codeFromHeader.ifBlank {
            Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(joined)?.value.orEmpty()
        }

        val course = courseFromHeader.ifBlank {
            val withoutPercent = joined.replace(Regex("""\d{1,3}(?:\.\d{1,2})?\s*%"""), "")
            withoutPercent.substringBefore(code).trim().ifBlank { withoutPercent.trim() }
        }

        val present = presentFromHeader.ifBlank { ParsingUtils.extractCountNearLabel(joined, "present", "attended", "attendance") }
        val total = totalFromHeader.ifBlank { ParsingUtils.extractCountNearLabel(joined, "total", "classes") }

        val meaningfulCourse = course
            .replace(Regex("""(?i)\b(?:attendance|present|absent|percentage|overall|summary|classes|class)\b"""), " ")
            .replace(Regex("""\d+(?:\.\d+)?%?"""), " ")
            .trim()

        if (course.length < 3 || (percent == null && code.isBlank())) return null
        if (code.isBlank() && meaningfulCourse.length < 4) return null
        return AttendanceRow(course, code, percent, present, total)
    }

    
    

    private fun attendanceStatus(percent: Double): Pair<Int, String> {
        return when {
            percent >= 85.0 -> Palette.success(activity) to "Good standing"
            percent >= 75.0 -> Palette.warning(activity) to "Needs attention"
            else -> Palette.danger(activity) to "Low attendance"
        }
    }

    private fun createSectionHeading(title: String, subtitle: String): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.ink900(activity))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = subtitle
                setTextColor(Palette.ink500(activity))
                textSize = 11f
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun createAttendanceSummary(overall: Double?, percentages: List<Double>, count: Int): View {
        val activityContext = activity
        return LinearLayout(activityContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(Palette.primary(activity), Palette.violet(activity), 22f)
            elevation = dp(4).toFloat()
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(6), dp(12), dp(12)) }

            val validPercentages = percentages.filter { it.isFinite() && it in 0.0..100.0 }
            val display = overall?.takeIf { it.isFinite() && it in 0.0..100.0 }
                ?: validPercentages.takeIf { it.isNotEmpty() }?.average()
            addView(LinearLayout(activityContext).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(activityContext).apply {
                    text = when {
                        overall != null && display != null -> "OVERALL ATTENDANCE"
                        display != null -> "SUBJECT AVERAGE"
                        else -> "ATTENDANCE SUMMARY"
                    }
                    setTextColor(Color.argb(230, 255, 255, 255))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.10f
                })
                addView(TextView(activityContext).apply {
                    text = display?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "—"
                    setTextColor(Color.WHITE)
                    textSize = 34f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                })
                addView(TextView(activityContext).apply {
                    text = when {
                        overall != null && display != null -> "$count subjects • official overall percentage"
                        display != null -> "$count subjects • simple average of listed subjects; not weighted by class counts"
                        else -> "$count subjects • percentage shown per subject"
                    }
                    setTextColor(Color.argb(210, 255, 255, 255))
                    textSize = 11f
                })
            })

            if (display != null) {
                // Animated-feel progress ring with centered label on the gradient card.
                addView(LinearLayout(activityContext).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, 0, 0)
                    addView(createProgressRing(display, 74, 8, Color.WHITE))
                    addView(TextView(activityContext).apply {
                        text = attendanceStatus(display).second.uppercase(java.util.Locale.US)
                        setTextColor(Color.argb(220, 255, 255, 255))
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        letterSpacing = 0.08f
                        setPadding(0, dp(6), 0, 0)
                        gravity = Gravity.CENTER
                    })
                })
            }
        }
    }

    private fun createAttendanceCard(row: AttendanceRow, position: Int): View {
        val percent = row.percent
        val statusColor = when {
            percent == null -> Palette.ink500(activity)
            percent >= 85.0 -> Palette.success(activity)
            percent >= 75.0 -> Palette.warning(activity)
            else -> Palette.danger(activity)
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(activity).apply {
                text = position.toString().padStart(2, '0')
                gravity = Gravity.CENTER
                setTextColor(Palette.primary(activity))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(activity), 11f)
                minWidth = dp(38)
                minHeight = dp(34)
            })
            header.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(12), 0, dp(8), 0) }
                addView(TextView(activity).apply {
                    text = row.course
                    setTextColor(Palette.ink900(activity))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (row.code.isNotBlank()) addView(TextView(activity).apply {
                    text = row.code
                    setTextColor(Palette.ink500(activity))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            header.addView(TextView(activity).apply {
                text = percent?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "N/A"
                setTextColor(statusColor)
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(64)
            })
            addView(header)

            if (percent != null) addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent.roundToInt()
                progressTintList = ColorStateList.valueOf(statusColor)
                progressBackgroundTintList = ColorStateList.valueOf(Palette.ringTrack(activity))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(12) }
            })

            val stats = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            if (row.present.isNotBlank()) addStat(stats, "Present", row.present)
            if (row.total.isNotBlank()) addStat(stats, "Total", row.total)
            if (row.present.isNotBlank() && row.total.isNotBlank()) {
                val totalNumber = row.total.toDoubleOrNull()
                val presentNumber = row.present.toDoubleOrNull()
                if (totalNumber != null && presentNumber != null && totalNumber >= presentNumber) {
                    addStat(stats, "Absent", (totalNumber - presentNumber).toString().removeSuffix(".0"))
                }
            }
            if (stats.childCount > 0) addView(stats)
            addView(TextView(activity).apply {
                text = if (row.detailUrl.isNotBlank()) {
                    "Tap to view class history and 75% planner  ›"
                } else {
                    "Tap to find this subject's class history and 75% planner  ›"
                }
                setTextColor(Palette.primary(activity))
                textSize = 11f
                setPadding(0, dp(9), 0, 0)
            })
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenDetail(row.detailUrl, row.course, row.code) }
        }
    }

    private fun addStat(container: LinearLayout, label: String, value: String) {
        container.addView(TextView(activity).apply {
            text = label + "  " + value
            setTextColor(Palette.ink700(activity))
            textSize = 11f
            setPadding(0, 0, dp(18), 0)
        })
    }

    private fun createEmptyState(title: String, message: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            setPadding(dp(24), dp(30), dp(24), dp(30))
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

    private fun createTableSection(title: String, table: TableData): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(7), dp(12), dp(7)) }
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(TextView(activity).apply {
                text = title.ifBlank { "Attendance records" }
                setTextColor(Palette.ink900(activity))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            table.rows.take(80).forEach { row ->
                val line = row.mapIndexed { index, value ->
                    val label = table.headers.getOrNull(index).orEmpty()
                    if (label.isBlank()) value else "$label: $value"
                }.filter(String::isNotBlank).joinToString("  •  ")
                if (line.isNotBlank()) addView(TextView(activity).apply {
                    text = line
                    setTextColor(Palette.ink700(activity))
                    textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            }
        }

    private fun createProgressRing(percent: Double, sizeDp: Int, strokeWidthDp: Int, ringColor: Int): View {
        val density = activity.resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt()
        val strokePx = (strokeWidthDp * density).roundToInt()
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            color = Palette.ringTrack(activity)
        }
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            strokeCap = Paint.Cap.ROUND
            color = ringColor
        }
        val label = TextView(activity).apply {
            text = String.format(java.util.Locale.US, "%.0f%%", percent)
            setTextColor(Palette.ink900(activity))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        return object : FrameLayout(activity) {
            init {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                setWillNotDraw(false)
                addView(label, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val inset = strokePx / 2f + 1f
                val rect = RectF(inset, inset, width - inset, height - inset)
                canvas.drawArc(rect, 0f, 360f, false, trackPaint)
                val sweep = (percent.coerceIn(0.0, 100.0) / 100.0 * 360.0).toFloat()
                canvas.drawArc(rect, -90f, sweep, false, progressPaint)
            }
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun gradientBackground(
        startColor: Int,
        endColor: Int,
        radiusDp: Float,
        angleDeg: Float = 135f
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            orientation = when (angleDeg.toInt()) {
                90 -> android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                270 -> android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT
                else -> android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            colors = intArrayOf(startColor, endColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
