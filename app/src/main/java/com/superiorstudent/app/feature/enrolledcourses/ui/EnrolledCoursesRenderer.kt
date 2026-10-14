package com.superiorstudent.app.feature.enrolledcourses.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.data.model.EnrolledCourse
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.ui.Palette
import java.util.Locale
import kotlin.math.roundToInt

/** Renders the Enrolled Courses feature independently from other student modules. */
class EnrolledCoursesRenderer(private val context: Context) {
    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val courses = data.enrolledCourses
            .distinctBy { it.course.trim().lowercase(Locale.US) + "|" + it.code.trim().lowercase(Locale.US) }
            .take(120)
        if (courses.isEmpty()) {
            container.addView(createEmptyState())
            return
        }
        val credits = courses.map { it.credits.toDoubleOrNull() }
        val totalCredits = if (credits.all { it != null }) credits.filterNotNull().sum() else null
        container.addView(createSummaryCard(courses.size, totalCredits))
        courses.forEachIndexed { index, course ->
            container.addView(createCourseCard(course, index + 1))
        }
    }

    private fun createSummaryCard(count: Int, totalCredits: Double?): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Palette.primary(context), Palette.primaryDark(context))
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(10), dp(12), dp(10)) }

            addView(TextView(context).apply {
                text = "ACTIVE COURSES"
                setTextColor(0xFFDDE8FF.toInt())
                textSize = 11f
                letterSpacing = 0.08f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = count.toString() + " enrolled " + (if (count == 1) "course" else "courses")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 23f
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL), Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            })
            val summary = if (totalCredits != null) {
                String.format(Locale.US, "%.1f total credits • Current ERP course list", totalCredits)
            } else {
                "Course details synced from your ERP account"
            }
            addView(TextView(context).apply {
                text = summary
                setTextColor(0xFFE5EDFF.toInt())
                textSize = 12f
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun createCourseCard(course: EnrolledCourse, index: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = borderedBackground(Palette.surface(context), Palette.stroke(context), 18)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(15), dp(14), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(7)) }

            addView(TextView(context).apply {
                text = String.format(Locale.US, "%02d", index)
                gravity = Gravity.CENTER
                setTextColor(Palette.primaryDark(context))
                textSize = 12f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
                background = borderedBackground(Palette.primaryLight(context), Palette.primaryLight(context), 13)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })

            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }
            details.addView(TextView(context).apply {
                text = course.course
                setTextColor(Palette.ink900(context))
                textSize = 15f
                maxLines = 3
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
            })
            if (course.code.isNotBlank()) {
                details.addView(TextView(context).apply {
                    text = course.code
                    setTextColor(Palette.ink500(context))
                    textSize = 12f
                    setPadding(0, dp(3), 0, dp(8))
                })
            }

            val chips = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (course.credits.isNotBlank()) {
                chips.addView(createChip(course.credits + " credits", Palette.infoBg(context), Palette.primaryDark(context)))
            }
            if (course.courseType.isNotBlank()) {
                chips.addView(createChip(course.courseType, Palette.violetBg(context), Palette.violet(context)))
            }
            if (course.term.isNotBlank()) {
                chips.addView(createChip(course.term, Palette.successBg(context), Palette.success(context)))
            }
            if (chips.childCount > 0) details.addView(chips)

            if (course.faculty.isNotBlank()) {
                details.addView(TextView(context).apply {
                    text = course.faculty
                    setTextColor(Palette.ink500(context))
                    textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            }
            if (course.department.isNotBlank()) {
                details.addView(TextView(context).apply {
                    text = course.department
                    setTextColor(Palette.primaryDark(context))
                    textSize = 10f
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            addView(details)
        }

    private fun createChip(text: String, fill: Int, ink: Int): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(ink)
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
            background = borderedBackground(fill, fill, 10)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            maxLines = 1
        }

    private fun createEmptyState(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = borderedBackground(Palette.surface(context), Palette.stroke(context), 18)
            setPadding(dp(24), dp(30), dp(24), dp(30))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            addView(TextView(context).apply {
                text = "No enrolled courses found"
                setTextColor(Palette.primaryDark(context))
                textSize = 17f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "The ERP did not return any active course cards. Refresh to try loading your course list again."
                setTextColor(Palette.ink500(context))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
        }

    private fun borderedBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radiusDp).toFloat()
        }
}