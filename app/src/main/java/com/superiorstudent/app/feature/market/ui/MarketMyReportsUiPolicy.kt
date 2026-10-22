package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R

internal enum class MarketMyReportStatus(
    val label: String,
    val backgroundRes: Int,
    val textRes: Int
) {
    ACTION_TAKEN("Action taken", R.color.market_report_ok_bg, R.color.market_report_ok),
    UNDER_REVIEW("Under review", R.color.market_report_warn_bg, R.color.market_report_warn),
    DISMISSED("Dismissed", R.color.market_line, R.color.market_ink2),
    RECEIVED("Received", R.color.market_sky, R.color.market_ink)
}

internal data class MarketMyReport(
    val title: String,
    val context: String,
    val iconBackgroundRes: Int,
    val status: MarketMyReportStatus
)

/** Screen 30 reference copy and sample rows; this data is not fetched from an account. */
internal object MarketMyReportsUiPolicy {
    const val TITLE = "My reports"
    const val SUBTITLE = "What happened to your reports"
    const val PRIVACY_NOTE =
        "We never tell the other student who reported them. Thank you for keeping the market safe."
    const val SAMPLE_ROW_NOTICE_TITLE = "Reference example"
    const val SAMPLE_ROW_NOTICE =
        "This item is sample content from the design reference. Your actual report history is not connected to this screen yet."

    val reports = listOf(
        MarketMyReport("Offer: Assignment writing service", "Graded work · 19 Oct",
            R.color.market_report_ok_bg, MarketMyReportStatus.ACTION_TAKEN),
        MarketMyReport("Task: Pay first, work later", "Scam · 18 Oct",
            R.color.market_report_warn_bg, MarketMyReportStatus.UNDER_REVIEW),
        MarketMyReport("Offer: Notes bundle", "Spam · 14 Oct",
            R.color.market_line, MarketMyReportStatus.DISMISSED),
        MarketMyReport("User: Bilal Raza", "Abusive · Today",
            R.color.market_sky, MarketMyReportStatus.RECEIVED)
    )

    fun hasReferenceContent(): Boolean =
        TITLE == "My reports" &&
            SUBTITLE == "What happened to your reports" &&
            reports.map { it.title } == listOf(
                "Offer: Assignment writing service", "Task: Pay first, work later",
                "Offer: Notes bundle", "User: Bilal Raza"
            ) &&
            reports.map { it.status.label } ==
                listOf("Action taken", "Under review", "Dismissed", "Received") &&
            PRIVACY_NOTE.contains("never tell the other student")
}
