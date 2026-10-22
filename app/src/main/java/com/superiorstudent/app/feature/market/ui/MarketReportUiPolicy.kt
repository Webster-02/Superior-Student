package com.superiorstudent.app.feature.market.ui

/**
 * Pure copy and choice rules for the Screen 20 task-report reference.
 * Reporting and blocking remain preview-only until their separate data workflow is approved.
 */
internal object MarketReportUiPolicy {
    const val TITLE = "Report this task"
    const val DEFAULT_REASON = "Asking for graded assignment, quiz or exam work"

    val REASONS = listOf(
        DEFAULT_REASON,
        "Scam or asks for full payment first",
        "Spam or repeated posts",
        "Inappropriate or abusive",
        "Something else"
    )

    fun isSupportedReason(reason: String): Boolean = reason in REASONS

    fun previewConfirmation(authorName: String, reason: String, alsoBlock: Boolean): String {
        val safeAuthor = authorName.trim().ifBlank { "the task owner" }
        val safeReason = reason.takeIf(::isSupportedReason) ?: DEFAULT_REASON
        val blockMessage = if (alsoBlock) {
            "$safeAuthor was not blocked."
        } else {
            "No block action was taken for $safeAuthor."
        }
        return "Selected reason: $safeReason.\n\n" +
            "This is a reference preview only. No report was submitted. $blockMessage"
    }
}
