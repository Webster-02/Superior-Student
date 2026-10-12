package com.superiorstudent.app.feature.profile.ui

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
            addView(TextView(activity).apply {
                text = value.trim()
                setTextColor(Palette.ink900(activity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    fun render(container: LinearLayout, data: ModuleData, cachedStudentName: String) {
        val fields = extractProfileFields(data)

        container.addView(
            sectionHeadingFactory(
                "Student profile",
                "Live personal and academic information from your ERP account"
            )
        )

        if (fields.isNotEmpty()) {
            container.addView(createProfileHero(fields, cachedStudentName))

            val priority = fields.filter { field ->
                ParsingUtils.isProfileLabel(
                    field.first,
                    "student name", "full name", "name",
                    "registration", "roll no", "student id", "student code",
                    "program", "degree", "course of study",
                    "department", "faculty", "semester", "session", "campus"
                )
            }.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }

            if (priority.isNotEmpty()) {
                container.addView(
                    sectionHeadingFactory(
                        "Academic & identity",
                        "Key fields returned by the ERP"
                    )
                )
                priority.take(12).chunked(2).forEach { pair ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    pair.forEach { field ->
                        row.addView(createProfileFieldCard(field.first, field.second).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(dp(5), dp(4), dp(5), dp(4)) }
                        })
                    }
                    container.addView(row)
                }
            }

            val secondary = fields.filterNot { priority.contains(it) }
            if (secondary.isNotEmpty()) {
                container.addView(
                    sectionHeadingFactory(
                        "Additional details",
                        "Other readable ERP fields"
                    )
                )
                secondary.take(24).forEach { (label, value) ->
                    container.addView(createProfileFieldCard(label, value))
                }
            }
        } else {
            data.cards.take(6).forEach { (label, value) ->
                container.addView(createProfileFieldCard(label, value))
            }
            data.tables.take(3).forEachIndexed { index, table ->
                container.addView(
                    tableSectionFactory(
                        table.title.ifBlank { if (index == 0) "Profile details" else "Additional information" },
                        table
                    )
                )
            }
            if (data.cards.isEmpty() && data.tables.isEmpty()) {
                container.addView(
                    emptyStateFactory(
                        "Profile information unavailable",
                        "The ERP profile page did not expose readable fields. Tap Refresh to sync again."
                    )
                )
            }
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
        val reg = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "registration", "roll no", "student id", "student code") }?.second.orEmpty()
        val campus = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "campus") }?.second.orEmpty()
        val session = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "session") }?.second.orEmpty()
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
                if (program.isNotBlank()) addView(TextView(activity).apply {
                    text = program
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
