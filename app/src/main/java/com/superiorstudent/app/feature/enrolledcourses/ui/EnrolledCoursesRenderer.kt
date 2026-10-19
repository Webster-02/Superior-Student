package com.superiorstudent.app.feature.enrolledcourses.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.data.model.AttendanceDetailLink
import com.superiorstudent.app.data.model.EnrolledCourse
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.ui.Palette
import com.superiorstudent.app.ui.components.Chip
import com.superiorstudent.app.ui.components.ChipTone
import com.superiorstudent.app.ui.components.EmptyState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** ERP course list styled with live attendance data only when the course code matches exactly. */
class EnrolledCoursesRenderer(
    private val context: Context,
    private val attendanceLinksProvider: () -> List<AttendanceDetailLink> = { emptyList() },
    private val onRetry: () -> Unit = {}
) {
    private val regularTypeface: Typeface
        get() = ResourcesCompat.getFont(context, R.font.poppins)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)

    private val mediumTypeface: Typeface
        get() = ResourcesCompat.getFont(context, R.font.poppins_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val tintPalette: List<Int>
        get() = listOf(Palette.lav(context), Palette.peach(context), Palette.sky(context), Palette.mint(context))

    /** Used by the existing activity header. Credits are summarized only if every row has valid credits. */
    fun headerSubtitle(data: ModuleData): String? {
        val courses = distinctCourses(data)
        if (courses.isEmpty()) return null
        val parts = mutableListOf<String>()
        val terms = courses.map { it.term.trim() }.filter(String::isNotBlank).distinct()
        if (terms.size == 1) parts.add(terms.first())
        parts.add("${courses.size} ${if (courses.size == 1) "course" else "courses"}")
        totalCredits(courses)?.let { parts.add("$it credit hrs") }
        return parts.joinToString(" · ")
    }

    fun render(container: LinearLayout, data: ModuleData) {
        container.removeAllViews()
        val courses = distinctCourses(data)
        if (courses.isEmpty()) {
            container.addView(EmptyState.create(
                context,
                "No enrolled courses found",
                "The ERP did not return any active course cards. Refresh to try loading your course list again."
            ))
            return
        }
        courses.forEach { course ->
            container.addView(createCourseCard(course, resolveAttendancePercentage(course)))
        }
        container.addView(createInformationNote())
        applyTypography(container)
    }

    private fun distinctCourses(data: ModuleData): List<EnrolledCourse> =
        data.enrolledCourses
            .distinctBy { it.course.trim().lowercase(Locale.US) + "|" + normalizeCode(it.code) }
            .take(120)

    private fun totalCredits(courses: List<EnrolledCourse>): String? {
        if (courses.isEmpty()) return null
        val credits = courses.map { it.credits.trim().replace(",", "").toDoubleOrNull() }
        if (!credits.all { it != null && it.isFinite() && it >= 0.0 }) return null
        val sum = credits.filterNotNull().sum()
        if (sum <= 0.0) return null
        return formatNumber(sum)
    }

    private fun resolveAttendancePercentage(course: EnrolledCourse): Double? {
        val code = normalizeCode(course.code)
        if (code.isBlank()) return null
        val links = try { attendanceLinksProvider() } catch (_: Exception) { emptyList() }
        val matching = links.filter { normalizeCode(it.code) == code }
            .mapNotNull { it.percentage?.takeIf { pct -> pct.isFinite() && pct in 0.0..100.0 } }
        if (matching.isEmpty()) return null
        val first = matching.first()
        if (matching.any { abs(it - first) > 0.15 }) return null
        return first
    }

    private fun createCourseCard(course: EnrolledCourse, attendance: Double?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedCard(Palette.card(context), Palette.stroke(context), 22f)
            setPadding(dp(14), dp(15), dp(14), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(5), dp(16), dp(6)) }

            val prefix = codePrefix(course.code)
            val tintIndex = if (prefix.isBlank()) 0 else (abs(prefix.hashCode()) % tintPalette.size)
            if (prefix.isNotBlank()) addView(TextView(context).apply {
                text = prefix
                gravity = Gravity.CENTER
                setTextColor(Palette.ink(context))
                textSize = 15f
                typeface = mediumTypeface
                includeFontPadding = false
                background = roundedCard(tintPalette[tintIndex], tintPalette[tintIndex], 16f)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) }
                contentDescription = "Course code prefix $prefix"
            })

            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = course.course.trim()
                    setTextColor(Palette.ink(context))
                    textSize = 14f
                    typeface = mediumTypeface
                    includeFontPadding = false
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val secondaryParts = listOfNotNull(
                    course.code.trim().takeIf(String::isNotBlank),
                    course.credits.trim().takeIf(String::isNotBlank)?.let { "$it cr" }
                )
                if (secondaryParts.isNotEmpty()) addView(TextView(context).apply {
                    text = secondaryParts.joinToString(" · ")
                    setTextColor(Palette.ink2(context))
                    textSize = 12f
                    typeface = regularTypeface
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })

                val extras = listOfNotNull(
                    course.courseType.trim().takeIf(String::isNotBlank),
                    course.term.trim().takeIf(String::isNotBlank),
                    course.department.trim().takeIf(String::isNotBlank)
                ).distinct()
                extras.forEachIndexed { index, value ->
                    addView(TextView(context).apply {
                        text = value
                        setTextColor(Palette.ink2(context))
                        textSize = 11f
                        typeface = regularTypeface
                        includeFontPadding = false
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, if (index == 0) dp(5) else dp(3), 0, 0)
                    })
                }
                // The ERP card-head title maps to a person/instructor name; it is intentionally omitted.
            }
            addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            attendance?.let { percentage ->
                val tone = when {
                    percentage >= 80.0 -> ChipTone.OK
                    percentage >= 75.0 -> ChipTone.WARN
                    else -> ChipTone.BAD
                }
                val label = if (abs(percentage - percentage.toInt()) < 0.05) {
                    "${percentage.toInt()}%"
                } else {
                    String.format(Locale.US, "%.1f%%", percentage)
                }
                addView(Chip.create(context, label, tone).apply {
                    textSize = 12f
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    minHeight = dp(34)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8) }
                    contentDescription = "Attendance $label"
                })
            }
        }

    private fun createInformationNote(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedCard(Palette.card(context), Palette.stroke(context), 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(4)) }
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_pt_book)
                imageTintList = android.content.res.ColorStateList.valueOf(Palette.ink2(context))
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) }
            })
            addView(TextView(context).apply {
                text = "Course details are shown as provided by your ERP account. Attendance appears only when the course code matches."
                textSize = 11f
                setTextColor(Palette.ink2(context))
                typeface = regularTypeface
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

    private fun codePrefix(code: String): String =
        Regex("""^\s*([A-Za-z]{2,8})[-\s]?\d""").find(code)?.groupValues?.getOrNull(1)
            ?.uppercase(Locale.US).orEmpty()

    private fun normalizeCode(code: String): String =
        code.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")

    private fun formatNumber(value: Double): String =
        if (kotlin.math.abs(value - value.roundToInt()) < 0.001) value.roundToInt().toString()
        else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

    private fun roundedCard(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radiusDp.roundToInt()).toFloat()
        }

    private fun applyTypography(view: View) {
        when (view) {
            is TextView -> {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = if (style and Typeface.BOLD != 0) Typeface.create(mediumTypeface, Typeface.BOLD) else regularTypeface
                view.includeFontPadding = false
            }
            is ViewGroup -> for (index in 0 until view.childCount) applyTypography(view.getChildAt(index))
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
