package com.superiorstudent.app.feature.market.ui

/** Screen 24 appeal form reference data and validation. This screen submits no live appeal. */
internal object MarketAppealUiPolicy {
    const val TITLE = "Appeal"
    const val SUBTITLE = "Ask us to review again"
    const val OFFER_TITLE = "Assignment writing service"
    const val OFFER_SUBTITLE = "Removed today · Rule 1"
    const val REMOVED_LABEL = "Removed"
    const val REASON_PROMPT = "Why should we review this?"
    const val MAX_REASON_LENGTH = 400
    const val DEFAULT_REASON =
        "This was a tutoring offer for explaining assignment topics, not writing them. I can change the title to make this clear."
    const val INFO_TEXT =
        "We reply in Notices, usually within 3 days. You can send only one appeal for this decision."
    const val SEND_BUTTON = "Send appeal"
    const val EMPTY_REASON_TITLE = "Add a reason"
    const val EMPTY_REASON_MESSAGE = "Explain why you think this decision should be reviewed before continuing."
    const val PREVIEW_TITLE = "Appeal preview"
    const val PREVIEW_MESSAGE =
        "This is a preview only. Your appeal has not been sent or saved, and your offer status has not changed."

    fun characterCount(reason: String): Int = reason.length
    fun isReasonValid(reason: String): Boolean = reason.trim().isNotEmpty() && reason.length <= MAX_REASON_LENGTH
}
