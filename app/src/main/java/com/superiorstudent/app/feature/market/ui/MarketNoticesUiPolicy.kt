package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R

/** Screen 22 uses local screenshot fixtures until the live notices source is wired in a separate step. */
internal enum class MarketNoticesTab(val caption: String, val unreadCount: Int) {
    ACTIVITY("Activity", 3),
    TEAM("U2U team", 2);
    val label: String get() = "$caption $unreadCount"
}
internal enum class MarketNoticesGlyph { BACK, ALERT, MAIL, SPEAKER }
internal data class MarketNoticeFixture(
    val id: String, val title: String, val description: String, val timeLabel: String,
    val glyph: MarketNoticesGlyph, val iconBackground: Int, val unread: Boolean
)
internal object MarketNoticesUiPolicy {
    const val TITLE = "Notices"
    const val SUBTITLE = "Activity and team messages"
    const val RETENTION_NOTE = "Messages from the team stay for 60 days."
    const val PREVIEW_DISCLOSURE =
        "This is sample reference content. Live Market notices, read status and detail navigation are not connected yet; nothing has changed."
    const val REMOVED_OFFER_DISCLOSURE =
        "This is a reference preview. Full notice details and the appeal flow are a separate screen and are not connected yet. No appeal has been submitted and no offer was changed."
    val teamNotices = listOf(
        MarketNoticeFixture("offer-removed", "Your offer was removed",
            "\"Assignment writing service\" broke posting rule 1. Tap to see details or appeal.",
            "Today, 11:20 AM", MarketNoticesGlyph.ALERT, R.color.market_peach, true),
        MarketNoticeFixture("support-reply", "Reply from support",
            "We checked your rating problem and fixed it. You can rate again.",
            "Yesterday", MarketNoticesGlyph.MAIL, R.color.market_sky, true),
        MarketNoticeFixture("midterm-reminder", "Midterm week reminder",
            "Never pay in full before seeing the work. Report suspicious posts.",
            "18 Oct", MarketNoticesGlyph.SPEAKER, R.color.market_lav, false)
    )
    /** These are local visual fixtures, not notices from the signed-in student's account. */
    val activityNotices = listOf(
        MarketNoticeFixture("proposal-received", "New proposal received",
            "A student sent a proposal for your Design task. Review it in My tasks.",
            "Today, 10:45 AM", MarketNoticesGlyph.MAIL, R.color.market_mint, true),
        MarketNoticeFixture("task-update", "Task activity update",
            "There is an update related to one of your Market tasks.",
            "Yesterday", MarketNoticesGlyph.ALERT, R.color.market_peach, true),
        MarketNoticeFixture("offer-viewed", "Offer viewed",
            "A student viewed your Ocular Anatomy notes offer.",
            "18 Oct", MarketNoticesGlyph.SPEAKER, R.color.market_lav, true)
    )
    fun noticesFor(tab: MarketNoticesTab): List<MarketNoticeFixture> =
        when (tab) {
            MarketNoticesTab.ACTIVITY -> activityNotices
            MarketNoticesTab.TEAM -> teamNotices
        }
    fun disclosureFor(notice: MarketNoticeFixture): String =
        if (notice.id == "offer-removed") REMOVED_OFFER_DISCLOSURE else PREVIEW_DISCLOSURE
}
