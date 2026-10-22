package com.superiorstudent.app.feature.market.ui

/** A static, reference-matched policy for the student-facing Market rules screen. */
internal object MarketRulesUiPolicy {
    const val TITLE = "Rules and safety"
    const val SUBTITLE = "Keep the market useful"
    const val ALLOWED_HEADING = "Allowed"
    const val NOT_ALLOWED_HEADING = "Not allowed"
    const val STAY_SAFE_HEADING = "Stay safe"
    const val BROKEN_RULES_HEADING = "If rules are broken"

    const val STAY_SAFE_COPY =
        "Ask for a sample first. Pay in parts. Meet on campus. U2U never handles money, so keep proof of agreement in chat."

    val allowedItems = listOf(
        "Notes, tutoring, explaining a topic",
        "Project help, design, typing, formatting",
        "Proofreading and coding help (explain, review)"
    )

    val prohibitedItems = listOf(
        "Doing someone else's graded assignment, quiz or exam",
        "Using someone's ERP login or sharing your password",
        "Asking for full payment before work is shown",
        "Spam, fake offers or abusive messages"
    )

    val enforcementSteps = listOf(
        MarketRulesEnforcementStep(number = "1", label = "Warning", tone = MarketRulesPenaltyTone.INFO),
        MarketRulesEnforcementStep(number = "2", label = "7 days", tone = MarketRulesPenaltyTone.WARNING),
        MarketRulesEnforcementStep(number = "3", label = "30 days", tone = MarketRulesPenaltyTone.PEACH),
        MarketRulesEnforcementStep(number = "4", label = "Ban", tone = MarketRulesPenaltyTone.DANGER)
    )

    fun hasReferenceContent(): Boolean =
        TITLE == "Rules and safety" &&
            allowedItems.size == 3 &&
            prohibitedItems.size == 4 &&
            STAY_SAFE_COPY.contains("U2U never handles money") &&
            enforcementSteps.map { it.label } == listOf("Warning", "7 days", "30 days", "Ban")
}

internal data class MarketRulesEnforcementStep(
    val number: String,
    val label: String,
    val tone: MarketRulesPenaltyTone
)

internal enum class MarketRulesPenaltyTone {
    INFO, WARNING, PEACH, DANGER
}
