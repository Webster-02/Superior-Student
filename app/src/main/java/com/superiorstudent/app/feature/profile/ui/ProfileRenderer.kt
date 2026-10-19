package com.superiorstudent.app.feature.profile.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.ui.Palette
import com.superiorstudent.app.ui.components.Avatar
import com.superiorstudent.app.ui.components.Chip
import com.superiorstudent.app.ui.components.ChipTone
import com.superiorstudent.app.ui.components.ErrorState
import com.superiorstudent.app.ui.components.ListCard
import kotlin.math.roundToInt

/** Profile presentation over the unchanged ERP field records. */
class ProfileRenderer(
    private val activity: Context,
    private val sectionHeadingFactory: (String, String) -> View,
    private val tableSectionFactory: (String, TableData) -> View,
    private val emptyStateFactory: (String, String) -> View,
    private val onRetry: () -> Unit = {}
) {
    private val regularTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)

    private val mediumTypeface: Typeface
        get() = ResourcesCompat.getFont(activity, R.font.poppins_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun render(container: LinearLayout, data: ModuleData, cachedStudentName: String) {
        container.removeAllViews()
        val fields = extractProfileFields(data)
        if (fields.isEmpty()) {
            val rawCards = data.cards.filter { it.first.isNotBlank() && it.second.isNotBlank() }
            if (rawCards.isNotEmpty()) {
                container.addView(createGroupHeading("Additional information"))
                val list = ListCard.container(activity)
                rawCards.forEachIndexed { index, pair ->
                    if (index > 0) list.addView(ListCard.divider(activity))
                    list.addView(ListCard.keyValueRow(activity, pair.first, pair.second))
                }
                container.addView(list)
            }
            data.tables.take(3).forEachIndexed { index, table ->
                container.addView(tableSectionFactory(
                    table.title.ifBlank { if (index == 0) "Profile details" else "Additional information" },
                    table
                ))
            }
            if (rawCards.isEmpty() && data.tables.isEmpty()) {
                container.addView(ErrorState.create(
                    activity,
                    "Profile information unavailable",
                    "The ERP profile page did not return readable fields. Refresh to try again.",
                    onRetry
                ))
            }
            return
        }

        val name = fields.firstValue("student name", "full name", "name")
            ?.takeIf(ParsingUtils::isValidStudentName)
            ?: cachedStudentName.takeIf(ParsingUtils::isValidStudentName).orEmpty()
        val program = fields.firstValue("program", "degree", "course of study", "programme")
        val semester = fields.firstValue("semester", "current semester", "term")
        val registration = fields.firstValue(
            "registration", "registration no", "registration number", "roll no",
            "roll number", "student id", "student code"
        ).orEmpty()

        if (name.isNotBlank()) container.addView(createProfileHero(name, program, semester, registration))

        val nameFields = fields.filter { isStudentNameLabel(it.first) }
        val registrationFields = fields.filter { isRegistrationLabel(it.first) }
        val academic = fields.filter { isAcademicField(it.first) && !isStudentNameLabel(it.first) }
        val contact = fields.filter { isContactField(it.first) && !isAcademicField(it.first) }
        val family = fields.filter { isFamilyField(it.first) }
        val accounted = (nameFields + registrationFields + academic + contact + family).toSet()
        val personal = fields.filter {
            !accounted.contains(it) && isPersonalField(it.first)
        }
        val other = fields.filterNot { accounted.contains(it) || personal.contains(it) }

        // Registration and name remain visible when no valid identity summary can be built.
        val academicWithFallback = if (name.isBlank() || registration.isBlank()) {
            (academic + nameFields.filterNot { name.isNotBlank() } + registrationFields.filterNot { registration.isNotBlank() })
                .distinct()
        } else academic

        addFieldSection(container, "Academic", academicWithFallback)
        addFieldSection(container, "Contact", contact, withContactIcons = true)
        addFieldSection(container, "Personal details", personal)
        addFieldSection(container, "Family details", family)
        addFieldSection(container, "Additional information", other)

        // If non-field records were also supplied, retain their information in the familiar table view.
        data.tables.take(3).forEachIndexed { index, table ->
            container.addView(tableSectionFactory(
                table.title.ifBlank { if (index == 0) "Additional profile details" else "Further information" },
                table
            ))
        }
        data.cards.filter { card ->
            card.first.isNotBlank() && card.second.isNotBlank() &&
                fields.none { it.first.equals(card.first, true) && it.second.equals(card.second, true) }
        }.take(8).takeIf { it.isNotEmpty() }?.let { extraCards ->
            addFieldSection(container, "Other ERP information", extraCards)
        }
    }

    private fun List<Pair<String, String>>.firstValue(vararg labels: String): String? =
        firstOrNull { (label, value) ->
            value.isNotBlank() && labels.any { ParsingUtils.isProfileLabel(label, it) }
        }?.second?.trim()

    private fun createProfileHero(name: String, program: String?, semester: String?, registration: String?): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(6)) }

            val avatar = TextView(activity)
            Avatar.bind(activity, avatar, name)
            avatar.textSize = 32f
            avatar.typeface = mediumTypeface
            avatar.includeFontPadding = false
            avatar.layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply {
                bottomMargin = dp(16)
            }
            addView(avatar)

            addView(TextView(activity).apply {
                text = name
                textSize = 22f
                setTextColor(Palette.ink(activity))
                typeface = mediumTypeface
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val secondary = listOfNotNull(
                program?.trim()?.takeIf(String::isNotBlank),
                semester?.trim()?.takeIf(String::isNotBlank)?.let { value ->
                    if (value.contains("semester", true)) value else "Semester $value"
                }
            ).distinct().joinToString(" · ")
            if (secondary.isNotBlank()) addView(TextView(activity).apply {
                text = secondary
                textSize = 14f
                setTextColor(Palette.ink2(activity))
                typeface = regularTypeface
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            })

            registration?.trim()?.takeIf(String::isNotBlank)?.let { id ->
                addView(Chip.create(activity, id, ChipTone.LIME).apply {
                    textSize = 12f
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    minHeight = dp(32)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12) }
                })
            }
        }

    private fun addFieldSection(
        container: LinearLayout,
        title: String,
        fields: List<Pair<String, String>>,
        withContactIcons: Boolean = false
    ) {
        val rows = fields.filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .distinctBy { it.first.trim().lowercase() + "|" + it.second.trim().lowercase() }
        if (rows.isEmpty()) return
        container.addView(createGroupHeading(title))
        val list = ListCard.container(activity)
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) list.addView(ListCard.divider(activity))
            list.addView(ListCard.keyValueRow(
                activity, displayLabel(label), value.trim(), if (withContactIcons) contactIcon(label) else null
            ))
        }
        container.addView(list)
    }

    private fun createGroupHeading(title: String): View =
        TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(Palette.ink(activity))
            typeface = mediumTypeface
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(16), dp(16), dp(16), dp(4))
            }
        }

    private fun contactIcon(label: String): Int = when {
        ParsingUtils.isProfileLabel(label, "email", "e-mail") -> R.drawable.ic_pt_mail
        ParsingUtils.isProfileLabel(label, "phone", "mobile", "telephone", "contact number") -> R.drawable.ic_pt_phone
        else -> R.drawable.ic_pt_map_pin
    }

    private fun isStudentNameLabel(label: String): Boolean =
        ParsingUtils.isProfileLabel(label, "student name", "full name", "name")

    private fun isRegistrationLabel(label: String): Boolean =
        ParsingUtils.isProfileLabel(label, "registration", "roll no", "roll number", "student id", "student code")

    private fun isAcademicField(label: String): Boolean =
        ParsingUtils.isProfileLabel(
            label,
            "program", "programme", "degree", "course of study", "department", "faculty",
            "semester", "current semester", "session", "term", "campus", "career",
            "registration", "roll no", "roll number", "student id", "student code"
        )

    private fun isContactField(label: String): Boolean =
        ParsingUtils.isProfileLabel(
            label, "email", "e-mail", "phone", "mobile", "telephone", "emergency contact",
            "contact number", "contact", "present address", "permanent address", "address"
        )

    private fun isFamilyField(label: String): Boolean =
        ParsingUtils.isProfileLabel(label, "father", "mother", "guardian", "parent", "family")

    private fun isPersonalField(label: String): Boolean =
        ParsingUtils.isProfileLabel(
            label, "cnic", "national id", "national identity", "gender", "date of birth",
            "birth date", "nationality", "marital status", "blood group", "religion"
        )

    private fun displayLabel(raw: String): String =
        raw.trim().trimEnd(':').replace(Regex("\\s+"), " ")

    private fun extractProfileFields(data: ModuleData): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        data.records.forEach { record ->
            val fieldRecord = record.headers.any { it.equals("Field", true) || it.equals("Label", true) }
            if (!fieldRecord || record.values.size < 2) return@forEach
            val label = ParsingUtils.cleanDisplayText(record.values.firstOrNull().orEmpty())
            val value = record.values.drop(1).map(ParsingUtils::cleanDisplayText)
                .filter(String::isNotBlank).joinToString(" • ")
            if (label.isNotBlank() && value.isNotBlank() && label.length <= 90 && value.length <= 260 &&
                !label.equals(value, true)) {
                result.add(label to value)
            }
        }

        // Retain real label/value cards if the ERP page used a non-tabular layout.
        data.cards.forEach { (label, value) ->
            val cleanLabel = ParsingUtils.cleanDisplayText(label)
            val cleanValue = ParsingUtils.cleanDisplayText(value)
            if (cleanLabel.isNotBlank() && cleanValue.isNotBlank() &&
                cleanLabel.length <= 90 && cleanValue.length <= 260 && !cleanLabel.equals(cleanValue, true)) {
                result.add(cleanLabel to cleanValue)
            }
        }
        return result.distinctBy { it.first.trim().lowercase() + "|" + it.second.trim().lowercase() }
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

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
