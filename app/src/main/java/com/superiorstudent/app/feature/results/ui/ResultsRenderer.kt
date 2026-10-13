package com.superiorstudent.app.feature.results.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.AcademicCourseResult
import com.superiorstudent.app.data.model.AcademicTermResult
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.ui.Palette
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Presents ERP term summaries separately from course rows.
 * Missing GPA/CGPA values are omitted rather than inferred from grade points.
 */
class ResultsRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val tableSectionFactory: (String, TableData) -> View,
    private val emptyStateFactory: (String, String) -> View,
    private val onSemesterSelected: (String) -> Unit
) {
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()

        if (data.academicResults.isNotEmpty()) {
            renderStructuredResults(container, data.academicResults)
            return
        }

        // Do not display a nested term-summary table as if each header applied
        // to every row. That was the source of "Course", "Credits" and
        // "Marks Obtained" appearing as false student grades.
        val malformedNestedResults = data.tables.any { table ->
            hasHeader(table.headers, "term") &&
                hasHeader(table.headers, "grade points") &&
                hasHeader(table.headers, "total credits") &&
                table.rows.any { row ->
                    row.firstOrNull()?.contains("SPRING", true) == true ||
                        row.firstOrNull()?.contains("FALL", true) == true
                }
        }
        if (malformedNestedResults) {
            container.addView(emptyStateFactory(
                "Results need a refresh",
                "The ERP loaded its nested semester table, but its course rows could not be read clearly. Refresh to try syncing them again."
            ))
            return
        }

        if (data.semesterOptions.size > 1) {
            container.addView(createSemesterSelector(data.semesterOptions))
        }

        val resultTables = data.tables.filter { table ->
            table.headers.any { h ->
                h.contains("subject", true) || h.contains("course", true) ||
                    h.contains("marks", true) || h.contains("obtained", true) ||
                    h.contains("grade", true) || h.contains("credit", true)
            }
        }
        if (resultTables.isNotEmpty()) {
            container.addView(sectionHeadingFactory(
                "Semester results",
                "Subject marks, credits and grades returned by the ERP"
            ))
            resultTables.take(6).forEach { table ->
                table.rows.forEachIndexed { index, row ->
                    createAcademicResultCard(table.headers, row, index + 1)?.let(container::addView)
                }
            }
        }

        val otherTables = data.tables.filterNot { resultTables.contains(it) }
        otherTables.take(6).forEachIndexed { index, table ->
            container.addView(tableSectionFactory(
                table.title.ifBlank { if (index == 0) "Academic details" else "Additional details" },
                table
            ))
        }

        val resultRecords = data.records.mapNotNull { record ->
            if (record.headers.any { it.equals("Semester options", true) }) return@mapNotNull null
            val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
            if (values.size < 2) null else values
        }.distinctBy { it.joinToString("|").lowercase(Locale.US) }

        if (resultTables.isEmpty()) {
            resultRecords.take(60).forEach { values ->
                val details = values.drop(1).joinToString(" • ")
                if (details.isNotBlank()) container.addView(createResultCard(values.first(), details))
            }
        }

        val pageMessage = data.lines.firstOrNull {
            it.contains("no result", true) ||
                it.contains("not uploaded", true) ||
                it.contains("no record", true) ||
                it.contains("result is not", true) ||
                it.contains("not available", true)
        }
        if (resultTables.isEmpty() && resultRecords.isEmpty() && data.cards.isEmpty() && otherTables.isEmpty()) {
            container.addView(emptyStateFactory(
                "Results are not available yet",
                pageMessage ?: "No published result record was returned by the ERP. Results will appear here when the ERP provides them."
            ))
        }
    }

    private fun renderStructuredResults(container: LinearLayout, terms: List<AcademicTermResult>) {
        container.addView(sectionHeadingFactory(
            "Semester results",
            "Marks, credits and grades exactly as shown in your ERP"
        ))
        terms.forEach { term ->
            val published = hasPublishedResults(term)
            container.addView(createTermSummary(term, published))
            container.addView(sectionHeadingFactory(
                "${term.term} courses",
                if (published) "${term.courses.size} course records" else
                    "${term.courses.size} courses · marks and grades not shown by ERP yet"
            ))
            if (!published) {
                container.addView(createNotice(
                    "Results not published",
                    "The ERP currently shows course credits, but no marks or grades for this term. Zero placeholders are hidden so they are not mistaken for final marks."
                ))
            }
            term.courses.forEachIndexed { index, course ->
                container.addView(createCourseCard(course, index + 1, published))
            }
        }
    }

    private fun hasPublishedResults(term: AcademicTermResult): Boolean {
        if (isPositive(term.gradePoints) || term.gpa.isNotBlank() || term.cgpa.isNotBlank()) return true
        return term.courses.any { course ->
            course.finalGrade.isNotBlank() || course.midGrade.isNotBlank() ||
                isPositive(course.marksObtained) || isPositive(course.gradePoints)
        }
    }

    private fun isPositive(value: String): Boolean =
        value.replace(",", "").trim().toDoubleOrNull()?.let { it > 0.0 } == true

    private fun createTermSummary(term: AcademicTermResult, published: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 20f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(7)) }

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(header)
            header.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(activity).apply {
                    text = "SEMESTER"
                    textSize = 9f
                    letterSpacing = 0.08f
                    setTextColor(Palette.ink500(activity))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(activity).apply {
                    text = term.term
                    textSize = 19f
                    setTextColor(Palette.ink900(activity))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(3), 0, 0)
                })
            })
            header.addView(TextView(activity).apply {
                text = if (published) "RESULTS AVAILABLE" else "NOT POSTED"
                textSize = 9f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (published) Palette.success(activity) else Palette.warning(activity))
                background = rounded(
                    if (published) Palette.successBg(activity) else Palette.warningBg(activity), 12f
                )
                setPadding(dp(10), dp(7), dp(10), dp(7))
            })

            val metrics = if (published) {
                listOf(
                    "Grade points" to term.gradePoints,
                    "Total credits" to term.totalCredits,
                    "Earned credits" to term.earnedCredits,
                    "GPA" to term.gpa,
                    "CGPA" to term.cgpa
                ).filter { it.second.isNotBlank() }
            } else {
                listOf("Total credits" to term.totalCredits).filter { it.second.isNotBlank() }
            }
            addMetricRows(this, metrics)

            if (!published) {
                addView(TextView(activity).apply {
                    text = "This term's marks and grades are not currently shown in the ERP."
                    setTextColor(Palette.ink500(activity))
                    textSize = 11f
                    setPadding(0, dp(10), 0, 0)
                })
            }
        }

    private fun addMetricRows(container: LinearLayout, metrics: List<Pair<String, String>>) {
        metrics.filter { it.second.isNotBlank() }.chunked(3).forEach { group ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(10), 0, 0) }
            }
            group.forEach { (label, value) ->
                row.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(Palette.surfaceAlt(activity), 12f)
                    setPadding(dp(10), dp(10), dp(8), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(dp(2), 0, dp(4), 0) }
                    addView(TextView(activity).apply {
                        text = label.uppercase(Locale.US)
                        setTextColor(Palette.ink500(activity))
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(activity).apply {
                        text = value
                        setTextColor(Palette.ink900(activity))
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            }
            container.addView(row)
        }
    }

    private fun createCourseCard(course: AcademicCourseResult, position: Int, published: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 17f)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            val top = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(top)
            top.addView(TextView(activity).apply {
                text = position.toString().padStart(2, '0')
                gravity = Gravity.CENTER
                minWidth = dp(34)
                minHeight = dp(32)
                background = rounded(Palette.infoBg(activity), 10f)
                setTextColor(Palette.primary(activity))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            top.addView(TextView(activity).apply {
                text = course.course
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(10), 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (course.finalGrade.isNotBlank()) {
                top.addView(TextView(activity).apply {
                    text = course.finalGrade
                    gravity = Gravity.CENTER
                    background = rounded(Palette.primaryDark(activity), 11f)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(11), dp(7), dp(11), dp(7))
                })
            }

            val metrics = if (published) {
                listOf(
                    "Credits" to course.credits,
                    "Marks obtained" to course.marksObtained,
                    "Grade points" to course.gradePoints,
                    "Mid grade" to course.midGrade
                ).filter { it.second.isNotBlank() }
            } else {
                listOf("Credits" to course.credits).filter { it.second.isNotBlank() }
            }
            addMetricRows(this, metrics)
            if (!published) {
                addView(TextView(activity).apply {
                    text = "Marks and grade will appear here when provided by the ERP."
                    setTextColor(Palette.ink500(activity))
                    textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            }
        }

    private fun createNotice(title: String, detail: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.warningBg(activity), 14f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(2), dp(12), dp(5)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.warning(activity))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = detail
                setTextColor(Palette.ink700(activity))
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
        }

    private fun createResultCard(title: String, details: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (details.isNotBlank()) addView(TextView(activity).apply {
                text = details
                setTextColor(Palette.ink500(activity))
                textSize = 11f
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun createSemesterSelector(options: List<String>): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 16f)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
        }
        card.addView(TextView(activity).apply {
            text = "Select semester"
            setTextColor(Palette.ink900(activity))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val spinner = android.widget.Spinner(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(0, dp(7), 0, 0) }
            adapter = android.widget.ArrayAdapter(
                activity, android.R.layout.simple_spinner_dropdown_item, options
            )
        }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (first) { first = false; return }
                options.getOrNull(position)?.let(onSemesterSelected)
            }
        }
        card.addView(spinner)
        return card
    }

    private fun createAcademicResultCard(headers: List<String>, values: List<String>, position: Int): View? {
        if (values.none { it.isNotBlank() }) return null
        fun valueFor(vararg names: String): String {
            val index = headers.indexOfFirst { header -> names.any { header.contains(it, true) } }
            return if (index >= 0 && index < values.size) ParsingUtils.cleanDisplayText(values[index]) else ""
        }
        val subject = valueFor("subject", "course name", "course title", "course")
            .ifBlank { values.firstOrNull().orEmpty() }
        if (subject.isBlank()) return null
        val code = valueFor("code", "course code", "subject code")
        val marks = valueFor("marks obtained", "marks", "score")
        val maxMarks = valueFor("maximum", "max marks", "out of")
        val grade = valueFor("final grade", "grade", "letter grade")
        val credits = valueFor("credit", "credit hours", "cr")
        val gradePoints = valueFor("grade points", "grade pts", "points")

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.surface(activity), 18f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(activity).apply {
                text = subject
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (code.isNotBlank()) addView(TextView(activity).apply {
                text = code
                setTextColor(Palette.ink500(activity))
                textSize = 10f
                setPadding(0, dp(3), 0, 0)
            })
            val fields = listOf(
                "Marks" to marks, "Max" to maxMarks, "Credits" to credits,
                "Grade points" to gradePoints, "Grade" to grade
            ).filter { it.second.isNotBlank() }
            addMetricRows(this, fields)
        }
    }

    private fun hasHeader(headers: List<String>, fragment: String): Boolean =
        headers.any { it.trim().equals(fragment, true) || it.contains(fragment, true) }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
