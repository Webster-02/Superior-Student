package com.superiorstudent.app.feature.market.ui

/** Screen 25 reference copy. This static state is not a live account restriction. */
internal object MarketSuspendedUiPolicy {
    const val TITLE = "Market"
    const val STATUS_TITLE = "Posting is paused"
    const val UNTIL_LABEL = "Until 27 Oct 2026 · 5 days left"
    const val REASON_LABEL = "Reason"
    const val REASON = "Second violation of Rule 1 (graded work offers)."
    const val ALLOWED_TITLE = "You can still"
    const val ALLOWED_BROWSE = "Browse offers and tasks"
    const val ALLOWED_MESSAGE = "Message people you already chat with"
    const val ALLOWED_NOTICES = "Read notices and contact support"
    const val DENIED_TITLE = "You cannot"
    const val DENIED_ACTIONS = "Post tasks or offers, or send proposals"
    const val RULES_BUTTON = "Rules"
    const val APPEAL_BUTTON = "Appeal"
    const val RULES_PREVIEW =
        "Posting rules preview: offers to complete someone else's graded assignment, quiz or exam are not allowed. The full rules page is not connected yet."
    const val PREVIEW_DISCLOSURE =
        "This is a static Screen 25 reference state. It does not check or change the signed-in student's account status and does not enforce posting restrictions."

    val allowedItems = listOf(ALLOWED_BROWSE, ALLOWED_MESSAGE, ALLOWED_NOTICES)

    fun hasReferenceContent(): Boolean =
        STATUS_TITLE.isNotBlank() && UNTIL_LABEL.contains("27 Oct 2026") &&
            REASON.contains("Second violation of Rule 1") && allowedItems.size == 3 &&
            DENIED_ACTIONS.contains("send proposals")
}
