package com.superiorstudent.app.feature.market.ui

/** Screen 29 copy, selectable categories, and input validation policy. */
internal enum class MarketSupportCategory(val label: String) {
    LISTING_PROBLEM("Listing problem"),
    ACCOUNT("Account"),
    APP_BUG("App bug"),
    REPORT_PERSON("Report a person"),
    OTHER("Other")
}

internal object MarketSupportUiPolicy {
    const val TITLE = "Contact support"
    const val SUBTITLE = "We reply in Notices within 2 days"
    const val CATEGORY_PROMPT = "What is this about?"
    const val SUBJECT_LABEL = "Subject"
    const val DETAILS_LABEL = "Details"
    const val ATTACH_TITLE = "Related listing"
    const val SAMPLE_LISTING_TITLE = "Poster for health camp"
    const val SAFETY_MESSAGE = "Never send passwords. We will never ask for your ERP password."
    const val SEND_BUTTON = "Send message"
    const val INITIAL_SUBJECT = "Cannot rate a completed task"
    const val INITIAL_DETAILS =
        "I completed a task with Zain but the rate button does not show on my side."
    const val MAX_DETAILS_CHARS = 500
    const val SUBJECT_REQUIRED = "Add a subject before continuing."
    const val DETAILS_REQUIRED = "Add details before continuing."
    const val SUPPORT_NOT_CONNECTED_TITLE = "Not sent yet"
    const val SUPPORT_NOT_CONNECTED_MESSAGE =
        "Support submission is not connected yet. Your message has not been sent or saved."
    const val ATTACHMENT_NOT_CONNECTED_TITLE = "Sample listing only"
    const val ATTACHMENT_NOT_CONNECTED_MESSAGE =
        "This is example reference content, not a listing from your account. Live listing selection is not connected yet."

    val categories = MarketSupportCategory.values().toList()

    fun counterFor(length: Int): String =
        length.coerceIn(0, MAX_DETAILS_CHARS).toString() + "/" + MAX_DETAILS_CHARS

    fun canSubmit(subject: String, details: String): Boolean =
        subject.isNotBlank() && details.isNotBlank()

    fun hasReferenceContent(): Boolean =
        TITLE == "Contact support" &&
            SUBTITLE == "We reply in Notices within 2 days" &&
            categories.map { it.label } == listOf(
                "Listing problem", "Account", "App bug", "Report a person", "Other"
            ) &&
            INITIAL_SUBJECT == "Cannot rate a completed task" &&
            INITIAL_DETAILS.endsWith("on my side.") &&
            MAX_DETAILS_CHARS == 500 &&
            SAFETY_MESSAGE.contains("ERP password")
}
