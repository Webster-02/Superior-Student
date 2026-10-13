package com.superiorstudent.app.feature.profile.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.ui.Palette
import kotlin.math.roundToInt

/**
 * Profile feature presentation.
 * Shared table, section-heading and empty-state components are injected by the host;
 * profile-specific layout and field selection remain isolated in this class.
 */
class ProfileRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val tableSectionFactory: (String, TableData) -> View,
    private val emptyStateFactory: (String, String) -> View
) {
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

    private fun createProfileFieldCard(label: String, value: String): View {
        val sensitive = ParsingUtils.isProfileLabel(label, "cnic", "national id", "national identity")
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(activity), 16f)
            elevation = dp(2).toFloat()
            setPadding(dp(13), dp(11), dp(13), dp(11))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(TextView(activity).apply {
                text = label.trim().uppercase(java.util.Locale.US)
                setTextColor(Palette.primary(activity))
                textSize = 9f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.07f
            })
            val valueView = TextView(activity).apply {
                text = if (sensitive) maskIdentityValue(value) else value.trim()
                setTextColor(Palette.ink900(activity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            }
            addView(valueView)
            if (sensitive) {
                var revealed = false
                contentDescription = "$label. Tap to show or hide this sensitive value."
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    revealed = !revealed
                    valueView.text = if (revealed) value.trim() else maskIdentityValue(value)
                }
            }
        }
    }

    fun render(container: LinearLayout, data: ModuleData, cachedStudentName: String) {
        container.removeAllViews()
        val fields = extractProfileFields(data)
        container.addView(
            sectionHeadingFactory(
                "Student profile",
                "Your academic and personal information from the university ERP"
            )
        )

        if (fields.isEmpty()) {
            data.cards.take(6).forEach { (label, value) -> container.addView(createProfileFieldCard(label, value)) }
            data.tables.take(3).forEachIndexed { index, table ->
                container.addView(tableSectionFactory(
                    table.title.ifBlank { if (index == 0) "Profile details" else "Additional information" },
                    table
                ))
            }
            if (data.cards.isEmpty() && data.tables.isEmpty()) {
                container.addView(emptyStateFactory(
                    "Profile information unavailable",
                    "The ERP profile page did not return readable fields. Tap Refresh to try again."
                ))
            }
            return
        }

        container.addView(createProfileHero(fields, cachedStudentName))

        val academic = fields.filter { isAcademicField(it.first) }
        val contact = fields.filter { isContactField(it.first) && !isAcademicField(it.first) }
        val family = fields.filter { isFamilyField(it.first) }
        val personal = fields.filterNot {
            academic.contains(it) || contact.contains(it) || family.contains(it)
        }

        addFieldSection(container, "Academic information", "Programme and enrolment details", academic)
        addFieldSection(container, "Contact information", "Contact details available in your ERP", contact)
        addFieldSection(container, "Personal details", "Personal information provided by your university", personal)
        addFieldSection(container, "Family details", "Parent and guardian information", family)

        val accounted = (academic + contact + personal + family).toSet()
        val other = fields.filterNot { accounted.contains(it) }
        addFieldSection(container, "Additional information", "Other fields returned by the ERP", other)
        container.addView(
            emptyStateFactory(
                "Profile data is synced",
                "Only fields available in your own ERP account are shown. Empty ERP fields are left out."
            )
        )
    }

    private fun isAcademicField(label: String): Boolean =
        ParsingUtils.isProfileLabel(
            label,
            "student name", "full name", "registration", "roll no", "student id", "student code",
            "program", "degree", "course of study", "department", "faculty", "semester",
            "session", "campus", "career", "current semester"
        )

    private fun isContactField(label: String): Boolean =
        ParsingUtils.isProfileLabel(
            label, "email", "phone", "mobile", "emergency contact", "contact",
            "present address", "permanent address", "address"
        )

    private fun isFamilyField(label: String): Boolean =
        ParsingUtils.isProfileLabel(label, "father", "mother", "guardian", "parent", "family")

    private fun addFieldSection(
        container: LinearLayout,
        title: String,
        subtitle: String,
        fields: List<Pair<String, String>>
    ) {
        if (fields.isEmpty()) return
        container.addView(sectionHeadingFactory(title, subtitle))
        fields.chunked(2).forEach { pair ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(5), 0, dp(5), 0) }
            }
            pair.forEach { field ->
                row.addView(createProfileFieldCard(field.first, field.second).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
                })
            }
            container.addView(row)
        }
    }

    private fun extractProfileFields(data: ModuleData): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        data.records.forEach { record ->
            val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
            val fieldRecord = record.headers.any { h ->
                h.equals("Field", true) || h.equals("Label", true)
            }
            if (fieldRecord && values.size >= 2) {
                val label = values.first()
                val value = values.drop(1).joinToString(" • ")
                if (label.length <= 90 && value.length <= 180 && !label.equals(value, true)) {
                    result.add(label to value)
                }
            }
        }
        return result.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }
    }

    private fun createProfileHero(fields: List<Pair<String, String>>, cachedStudentName: String): View {
        val name = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "student name", "full name", "name") }
            ?.second
            ?.takeIf(ParsingUtils::isValidStudentName)
            ?: cachedStudentName.takeIf(ParsingUtils::isValidStudentName)
            ?: "Student"
        val program = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "program", "degree", "course of study") }?.second.orEmpty()
        val faculty = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "faculty", "department") }?.second.orEmpty()
        val reg = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "registration", "roll no", "student id", "student code") }?.second.orEmpty()
        val campus = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "campus") }?.second.orEmpty()
        val session = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "session", "semester", "current semester", "term") }?.second.orEmpty()
        val programLine = listOf(program, faculty).filter { it.isNotBlank() }.distinct().joinToString("  •  ")
        val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "S" }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(
                Palette.primaryDark(activity),
                Palette.violet(activity), 26f
            )
            elevation = dp(6).toFloat()
            setPadding(dp(18), dp(20), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(10)) }

            // Avatar with white ring + inner tinted circle
            addView(android.widget.FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                    setMargins(0, 0, dp(15), 0)
                }
                addView(View(activity).apply {
                    background = roundedBackground(Color.argb(70, 255, 255, 255), 22f)
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(64), dp(64))
                })
                addView(TextView(activity).apply {
                    text = initials
                    gravity = Gravity.CENTER
                    setTextColor(Palette.primaryDark(activity))
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Color.WHITE, 19f)
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                        gravity = Gravity.CENTER
                    }
                })
            })

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(activity).apply {
                    text = "STUDENT PROFILE"
                    setTextColor(Color.argb(190, 255, 255, 255))
                    textSize = 9f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.12f
                })
                addView(TextView(activity).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 19f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(3), 0, 0)
                })
                if (programLine.isNotBlank()) addView(TextView(activity).apply {
                    text = programLine
                    setTextColor(Color.argb(225, 255, 255, 255))
                    textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                })

                // Badge chips row for reg / session / campus
                val chips = listOf(reg, session, campus).filter(String::isNotBlank)
                if (chips.isNotEmpty()) {
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(9), 0, 0)
                        chips.forEachIndexed { i, chip ->
                            addView(TextView(activity).apply {
                                text = chip
                                setTextColor(Color.WHITE)
                                textSize = 9f
                                letterSpacing = 0.03f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                maxLines = 1
                                background = roundedBackground(Color.argb(55, 255, 255, 255), 20f)
                                setPadding(dp(10), dp(5), dp(10), dp(5))
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { if (i > 0) setMargins(dp(6), 0, 0, 0) }
                            })
                        }
                    })
                }
            })
        }
    }

    private fun maskIdentityValue(value: String): String {
        val digits = value.filter(Char::isDigit)
        if (digits.length <= 4) return "••••"
        return "••••••••" + digits.takeLast(4)
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

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
