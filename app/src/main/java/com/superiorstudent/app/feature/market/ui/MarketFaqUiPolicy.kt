package com.superiorstudent.app.feature.market.ui

import java.util.Locale

internal enum class MarketFaqCategory(val label: String) {
    ALL("All"),
    POSTING("Posting"),
    PROPOSALS("Proposals"),
    SAFETY("Safety"),
    ACCOUNT("Account")
}

internal data class MarketFaqQuestion(
    val title: String,
    val answer: String,
    val category: MarketFaqCategory
)

/** Screen 27 reference copy and filtering policy. This screen is a local FAQ and reads no live account data. */
internal object MarketFaqUiPolicy {
    const val TITLE = "FAQs"
    const val INITIAL_QUESTION_TITLE = "How do I get my proposal accepted?"

    const val INITIAL_QUESTION_ANSWER =
        "Be specific about what you will deliver and when. Mention similar work you have done. Fair prices and a short, clear message get chosen more often."

    val categories = listOf(
        MarketFaqCategory.ALL,
        MarketFaqCategory.POSTING,
        MarketFaqCategory.PROPOSALS,
        MarketFaqCategory.SAFETY,
        MarketFaqCategory.ACCOUNT
    )

    val questions = listOf(
        MarketFaqQuestion(
            title = INITIAL_QUESTION_TITLE,
            answer = INITIAL_QUESTION_ANSWER,
            category = MarketFaqCategory.PROPOSALS
        ),
        MarketFaqQuestion(
            title = "Does U2U handle payments?",
            answer = "No. U2U does not hold or process payments. Agree on terms directly with the other student and take care before exchanging money or goods.",
            category = MarketFaqCategory.ACCOUNT
        ),
        MarketFaqQuestion(
            title = "Why was my listing removed?",
            answer = "A listing may be removed if it does not follow the Market posting rules or raises a safety concern. Check the related notice for the stated reason and any available next steps.",
            category = MarketFaqCategory.POSTING
        ),
        MarketFaqQuestion(
            title = "How do I report someone?",
            answer = "Use the Report action on the relevant listing or user, choose the reason that best describes the concern, and submit accurate details. Reports are reviewed through the Market moderation process.",
            category = MarketFaqCategory.SAFETY
        ),
        MarketFaqQuestion(
            title = "How long does a task stay open?",
            answer = "A task can stay open for up to 14 days after it is posted, unless its status changes earlier. Check the task status and deadline for the latest details.",
            category = MarketFaqCategory.POSTING
        ),
        MarketFaqQuestion(
            title = "Can I edit a task after posting?",
            answer = "You can edit your own task while it remains open and your account is allowed to post. Keep the description, budget, and deadline accurate so other students know what to expect.",
            category = MarketFaqCategory.POSTING
        ),
        MarketFaqQuestion(
            title = "How do I appeal a decision?",
            answer = "Open the notice about the decision. If an appeal is available, use the Appeal action and explain clearly why you believe the decision should be reviewed.",
            category = MarketFaqCategory.ACCOUNT
        )
    )

    fun questionsFor(category: MarketFaqCategory): List<MarketFaqQuestion> =
        if (category == MarketFaqCategory.ALL) questions
        else questions.filter { it.category == category }

    fun questionByTitle(title: String?): MarketFaqQuestion? {
        val value = title?.trim().orEmpty()
        if (value.isEmpty()) return null
        return questions.firstOrNull { it.title.equals(value, ignoreCase = true) }
    }

    fun categoriesFor(query: String): List<MarketFaqCategory> {
        val term = query.trim().lowercase(Locale.ROOT)
        return if (term.isEmpty()) categories else categories.filter {
            it.label.lowercase(Locale.ROOT).contains(term)
        }
    }

    fun hasReferenceContent(): Boolean =
        TITLE == "FAQs" &&
            categories.map { it.label } == listOf("All", "Posting", "Proposals", "Safety", "Account") &&
            questions.size == 7 &&
            questions.firstOrNull()?.title == INITIAL_QUESTION_TITLE &&
            questions.firstOrNull()?.answer == INITIAL_QUESTION_ANSWER
}
