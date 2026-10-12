package com.superiorstudent.app.feature.results.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.ui.Palette
import kotlin.math.roundToInt

/**
 * Results feature UI. ERP semester selection and network/DOM refresh are delegated
 * to the host through a callback to keep WebView/session concerns outside this class.
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
            container.addView(
                sectionHeadingFactory(
                    "Semester results",
                    "Subject wise marks, grades and credits from your ERP account"
                )
            )
            resultTables.take(6).forEach { table ->
                table.rows.forEachIndexed { index, row ->
                    createAcademicResultCard(table.headers, row, index + 1)?.let {
                        container.addView(it)
                    }
                }
            }
        }

        val otherTables = data.tables.filterNot { resultTables.contains(it) }
        otherTables.take(6).forEachIndexed { index, table ->
            container.addView(
                tableSectionFactory(
                    table.title.ifBlank { if (index == 0) "Academic details" else "Additional details" },
                    table
                )
            )
        }

        val resultRecords = data.records.mapNotNull { record ->
            if (record.headers.any { it.equals("Semester options", true) }) return@mapNotNull null
            val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
            if (values.size < 2) null else values
        }.distinctBy { it.joinToString("|").lowercase() }

        if (resultTables.isEmpty()) {
            resultRecords.take(60).forEach { values ->
                val title = values.first()
                val details = values.drop(1).joinToString(" • ")
                if (details.isNotBlank()) container.addView(createResultCard(title, details))
            }
        }

        val pageMessage = data.lines.firstOrNull {
            it.contains("no result", true) ||
            it.contains("not uploaded", true) ||
            it.contains("no record", true) ||
            it.contains("result is not", true) ||
            it.contains("not available", true)
        }

        if (resultTables.isEmpty() && resultRecords.isEmpty() && data.cards.isEmpty()) {
            container.addView(
                emptyStateFactory(
                    "Current semester results are not uploaded yet",
                    pageMessage ?: "No published result record was returned by the ERP. Previous semester records will appear here when the ERP provides them."
                )
            )
        }
    }

    private fun createResultCard(title: String, details: String): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(activity).apply {
                text = title
                setTextColor(Palette.ink900(activity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            if (details.isNotBlank()) {
                addView(TextView(activity).apply {
                    text = details
                    setTextColor(Palette.ink500(activity))
                    textSize = 11f
                    setPadding(0, dp(5), 0, 0)
                })
            }
        }
    }

    private fun createSemesterSelector(options: List<String>): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { setMargins(0, dp(7), 0, 0) }
            adapter = android.widget.ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
        }

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var first = true

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit

            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (first) {
                    first = false
                    return
                }
                val label = options.getOrNull(position) ?: return
                onSemesterSelected(label)
            }
        }
        card.addView(spinner)
        return card
    }

    private fun createAcademicResultCard(headers: List<String>, values: List<String>, position: Int): View? {
        if (values.none { it.isNotBlank() }) return null

        fun valueFor(vararg names: String): String {
            val index = headers.indexOfFirst { header ->
                names.any { key -> header.contains(key, true) }
            }
            return if (index >= 0 && index < values.size) ParsingUtils.cleanDisplayText(values[index]) else ""
        }

        val subject = valueFor("subject", "course name", "course title", "course")
            .ifBlank { values.firstOrNull().orEmpty() }
        if (subject.isBlank()) return null

        val code = valueFor("code", "course code", "subject code")
        val marks = valueFor("marks", "obtained", "score", "total marks", "marks obtained")
        val maxMarks = valueFor("maximum", "max marks", "out of")
        val grade = valueFor("grade", "letter grade")
        val credits = valueFor("credit", "credit hours", "cr")
        val gpa = valueFor("gpa", "grade point", "points")

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val top = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(top)
            top.addView(TextView(activity).apply {
                text = position.toString().padStart(2, '0')
                gravity = Gravity.CENTER
                setTextColor(Palette.primary(activity))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(activity), 10f)
                minWidth = dp(36)
                minHeight = dp(32)
            })
            top.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(12), 0, dp(8), 0) }
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
            })
            if (grade.isNotBlank()) addView(TextView(activity).apply {
                text = grade
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.primaryDark(activity), 10f)
                setPadding(dp(9), dp(6), dp(9), dp(6))
            })

            val metrics = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(48), dp(11), 0, 0) }
            }
            fun addMetric(label: String, value: String) {
                if (value.isBlank()) return
                metrics.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(dp(4), 0, dp(4), 0) }
                    addView(TextView(activity).apply {
                        text = label.uppercase(java.util.Locale.US)
                        setTextColor(Palette.ink500(activity))
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(activity).apply {
                        text = value
                        setTextColor(Palette.ink900(activity))
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(3), 0, 0)
                    })
                })
            }
            addMetric("Marks", marks)
            addMetric("Max", maxMarks)
            addMetric("Credits", credits)
            addMetric("GPA", gpa)
            if (metrics.childCount > 0) addView(metrics)
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
