package com.superiorstudent.app.feature.market.ui

internal data class MarketPostTaskStep2Input(
    val budgetType: String,
    val amount: String,
    val deadlineIso: String,
    val todayIso: String,
    val skills: List<String>
)

internal data class MarketPostTaskStep2Validation(
    val budgetError: String? = null,
    val amountError: String? = null,
    val deadlineError: String? = null,
    val skillsError: String? = null
) {
    val isValid: Boolean
        get() = budgetError == null && amountError == null && deadlineError == null && skillsError == null
}

internal object MarketPostTaskStep2UiPolicy {
    const val MAX_SKILLS = 5
    val budgetTypes = listOf("Free help", "Fixed", "Negotiable")
    val availableSkills = listOf("Canva", "Photoshop", "Layout", "Illustrator", "Figma")

    fun validate(input: MarketPostTaskStep2Input): MarketPostTaskStep2Validation {
        val amountText = input.amount.trim().replace(",", "")
        val amountValue = amountText.toLongOrNull()
        val amountError = when {
            input.budgetType == "Free help" -> null
            input.budgetType !in budgetTypes -> null
            input.budgetType == "Fixed" && amountText.isBlank() -> "Enter an amount."
            input.budgetType == "Fixed" && (amountValue == null || amountValue <= 0L) ->
                "Enter a whole amount greater than zero."
            input.budgetType == "Negotiable" && amountText.isNotBlank() &&
                (amountValue == null || amountValue <= 0L) -> "Enter a whole amount greater than zero."
            else -> null
        }
        val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")
        val deadlineError = when {
            !datePattern.matches(input.deadlineIso) -> "Choose a valid deadline."
            !datePattern.matches(input.todayIso) -> "Current date could not be checked."
            input.deadlineIso < input.todayIso -> "Deadline cannot be in the past."
            else -> null
        }
        val skillsError = when {
            input.skills.size > MAX_SKILLS -> "Choose no more than five skills."
            input.skills.distinct().size != input.skills.size -> "Each skill can only be selected once."
            input.skills.any { it !in availableSkills } -> "Choose from the available skills."
            else -> null
        }
        return MarketPostTaskStep2Validation(
            budgetError = if (input.budgetType in budgetTypes) null else "Choose a budget option.",
            amountError = amountError,
            deadlineError = deadlineError,
            skillsError = skillsError
        )
    }
}
