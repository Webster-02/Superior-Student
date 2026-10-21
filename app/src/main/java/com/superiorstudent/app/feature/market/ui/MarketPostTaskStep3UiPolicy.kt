package com.superiorstudent.app.feature.market.ui

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

internal data class MarketPostTaskStep3Input(
    val title: String,
    val category: String,
    val course: String,
    val description: String,
    val budgetType: String,
    val amount: String,
    val deadlineIso: String,
    val todayIso: String,
    val skills: List<String>,
    val rulesAcknowledged: Boolean
)

internal data class MarketPostTaskStep3Validation(
    val titleError: String? = null,
    val categoryError: String? = null,
    val descriptionError: String? = null,
    val budgetError: String? = null,
    val amountError: String? = null,
    val deadlineError: String? = null,
    val skillsError: String? = null,
    val acknowledgementError: String? = null
) {
    val isValid: Boolean
        get() = titleError == null && categoryError == null && descriptionError == null &&
            budgetError == null && amountError == null && deadlineError == null &&
            skillsError == null && acknowledgementError == null
}

internal data class MarketPostTaskStep3Preview(
    val categoryLabel: String,
    val courseLabel: String?,
    val title: String,
    val description: String,
    val budgetLabel: String,
    val deadlineLabel: String,
    val proposalsLabel: String = "0 proposals",
    val posterInitials: String = "MA",
    val posterLabel: String = "You",
    val postedTimeLabel: String = "now"
)

internal object MarketPostTaskStep3UiPolicy {
    const val ACKNOWLEDGEMENT_TEXT =
        "I have read the posting rules. I am not asking anyone to do my graded assignment, quiz or exam."

    fun validate(input: MarketPostTaskStep3Input): MarketPostTaskStep3Validation {
        val details = MarketPostTaskStep1UiPolicy.validate(
            MarketPostTaskStep1Input(input.title, input.category, input.course, input.description)
        )
        val budget = MarketPostTaskStep2UiPolicy.validate(
            MarketPostTaskStep2Input(
                budgetType = input.budgetType,
                amount = input.amount,
                deadlineIso = input.deadlineIso,
                todayIso = input.todayIso,
                skills = input.skills
            )
        )
        return MarketPostTaskStep3Validation(
            titleError = details.titleError,
            categoryError = details.categoryError,
            descriptionError = details.descriptionError,
            budgetError = budget.budgetError,
            amountError = budget.amountError,
            deadlineError = budget.deadlineError,
            skillsError = budget.skillsError,
            acknowledgementError = if (input.rulesAcknowledged) null
                else "Read and accept the posting rules to continue."
        )
    }

    fun preview(input: MarketPostTaskStep3Input) = MarketPostTaskStep3Preview(
        categoryLabel = input.category.trim(),
        courseLabel = input.course.trim().takeIf { it.isNotEmpty() }?.substringBefore("·")?.trim(),
        title = input.title.trim(),
        description = input.description.trim(),
        budgetLabel = budgetLabel(input.budgetType, input.amount),
        deadlineLabel = deadlineLabel(input.todayIso, input.deadlineIso)
    )

    fun budgetLabel(budgetType: String, amount: String): String {
        val cleaned = amount.trim().replace(",", "")
        val formatted = cleaned.toLongOrNull()?.takeIf { it > 0L }
            ?.let { String.format(Locale.US, "%,d", it) }
        return when (budgetType) {
            "Free help" -> "Free help"
            "Fixed" -> formatted?.let { "Rs. $it" } ?: "Rs. ${amount.trim()}"
            "Negotiable" -> formatted?.let { "Negotiable · Rs. $it" } ?: "Negotiable"
            else -> budgetType
        }
    }

    fun deadlineLabel(todayIso: String, deadlineIso: String): String = try {
        val today = LocalDate.parse(todayIso)
        val deadline = LocalDate.parse(deadlineIso)
        val days = ChronoUnit.DAYS.between(today, deadline)
        when {
            days < 0 -> "Deadline passed"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> "Due in $days days"
        }
    } catch (_: DateTimeParseException) {
        "Due $deadlineIso"
    }
}
