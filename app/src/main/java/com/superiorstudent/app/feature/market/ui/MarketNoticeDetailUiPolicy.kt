package com.superiorstudent.app.feature.market.ui

/** Copy and local fixture details from the supplied Screen 23 Notice reference. */
internal object MarketNoticeDetailUiPolicy {
    const val TITLE = "Notice"
    const val NOTICE_TITLE = "Your offer was removed"
    const val TIMESTAMP = "Today, 11:20 AM"
    const val OFFER_TITLE = "Assignment writing service"
    const val OFFER_META = "Offer · Rs. 500"
    const val WHY_LABEL = "Why"
    const val WHY_TEXT = "Offers to complete someone else's graded assignment, quiz or exam are not allowed (Rule 1)."
    const val WHAT_HAPPENED_LABEL = "What happened"
    const val WHAT_HAPPENED_TEXT =
        "Listing removed. This is warning 1 of the ladder: warning, 7 day pause, 30 day pause, ban."
    const val INFO_TEXT =
        "You can post a corrected offer, or appeal if you think this was a mistake. One appeal per decision."
    const val APPEAL_BUTTON = "Appeal this decision"
    const val RULES_BUTTON = "Read posting rules"

    const val APPEAL_PREVIEW_MESSAGE =
        "The appeal form is a separate reference screen and is not connected yet. No appeal has been submitted and your offer status has not changed."
    const val RULES_PREVIEW_MESSAGE =
        "Rule 1: Offers to complete someone else's graded assignment, quiz or exam are not allowed. Post permitted study help, tutoring, explanations or notes instead. This is a reference preview, not the full live rules page."

    fun hasReferenceContent(): Boolean =
        NOTICE_TITLE.isNotBlank() && OFFER_TITLE.isNotBlank() &&
            WHY_TEXT.contains("Rule 1") && WHAT_HAPPENED_TEXT.contains("warning 1") &&
            INFO_TEXT.contains("One appeal per decision")
}
