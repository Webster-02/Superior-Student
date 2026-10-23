package com.superiorstudent.app.feature.market.ui

internal data class MarketPostTaskStep1Input(
    val title: String,
    val category: String,
    val course: String,
    val description: String
)

internal data class MarketPostTaskStep1Validation(
    val titleError: String? = null,
    val categoryError: String? = null,
    val descriptionError: String? = null
) {
    val isValid: Boolean
        get() = titleError == null && categoryError == null && descriptionError == null
}

internal object MarketPostTaskStep1UiPolicy {
    const val MAX_TITLE_LENGTH = 80
    const val MAX_COURSE_LENGTH = 60
    const val MAX_DESCRIPTION_LENGTH = 500
    val categories = listOf("Notes", "Tutoring", "Design", "Coding", "Project", "Typing")

    fun validate(input: MarketPostTaskStep1Input): MarketPostTaskStep1Validation {
        val title = input.title.trim()
        val description = input.description.trim()
        return MarketPostTaskStep1Validation(
            titleError = when {
                title.isBlank() -> "Enter a task title"
                title.length < 5 -> "Title must be at least 5 characters"
                title.length > MAX_TITLE_LENGTH -> "Title must be 80 characters or fewer"
                else -> null
            },
            categoryError = if (input.category in categories) null else "Choose a category",
            descriptionError = when {
                description.isBlank() -> "Enter a task description"
                description.length < 20 -> "Description must be at least 20 characters"
                description.length > MAX_DESCRIPTION_LENGTH -> "Description must be 500 characters or fewer"
                else -> null
            }
        )
    }

    fun descriptionCounter(description: String): String =
        "${description.length.coerceAtMost(MAX_DESCRIPTION_LENGTH)}/$MAX_DESCRIPTION_LENGTH"
}
