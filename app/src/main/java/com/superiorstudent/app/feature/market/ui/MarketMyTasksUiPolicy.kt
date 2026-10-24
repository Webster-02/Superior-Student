package com.superiorstudent.app.feature.market.ui

internal enum class MarketMyTasksTab { POSTED, APPLIED }
internal enum class MarketMyTaskAction {
    OPEN_POSTED_DETAIL, OPEN_CHAT_PREVIEW, RATE_TASK, WITHDRAW_PROPOSAL, PREVIEW_INFO
}
internal enum class MarketMyTaskTone { PEACH, MINT, LAVENDER, SKY, LIME, SUCCESS, WARNING }

internal data class MarketMyTaskItem(
    val id: String,
    val category: String,
    val categoryTone: MarketMyTaskTone,
    val status: String,
    val statusTone: MarketMyTaskTone,
    val title: String,
    val detailLine: String,
    val action: MarketMyTaskAction,
    val pricePkr: Int = 0,
    val helperName: String = "",
    val conversationId: String? = null,
    val counterpartUid: String? = null
)

/**
 * Local reference fixtures for Screen 15 only. Nothing here is a live task/proposal record.
 * Applied intentionally preserves all four visible screenshot cards although the segment reads
 * "Applied 3", matching the supplied reference image exactly.
 */
internal object MarketMyTasksUiPolicy {
    const val POSTED_TAB_LABEL = "Posted 2"
    const val APPLIED_TAB_LABEL = "Applied 3"

    val postedTasks = listOf(
        MarketMyTaskItem(
            id = "posted-health-camp",
            category = "Design",
            categoryTone = MarketMyTaskTone.PEACH,
            status = "Open",
            statusTone = MarketMyTaskTone.SUCCESS,
            title = "Poster for Optometry Society health camp",
            detailLine = "Your budget Rs. 1,500 · 4 proposals",
            action = MarketMyTaskAction.OPEN_POSTED_DETAIL
        ),
        MarketMyTaskItem(
            id = "posted-awareness-banner",
            category = "Design",
            categoryTone = MarketMyTaskTone.PEACH,
            status = "Open",
            statusTone = MarketMyTaskTone.SUCCESS,
            title = "Design a campus awareness banner",
            detailLine = "Your budget Rs. 800 · 1 proposal",
            action = MarketMyTaskAction.PREVIEW_INFO,
            pricePkr = 800
        )
    )

    val appliedTasks = listOf(
        MarketMyTaskItem(
            id = "applied-accepted-design",
            category = "Design",
            categoryTone = MarketMyTaskTone.PEACH,
            status = "Accepted",
            statusTone = MarketMyTaskTone.SUCCESS,
            title = "Poster for Optometry Society health camp",
            detailLine = "Your price Rs. 1,200 · Chat is open",
            action = MarketMyTaskAction.OPEN_CHAT_PREVIEW,
            pricePkr = 1200,
            helperName = "Zain Malik"
        ),
        MarketMyTaskItem(
            id = "applied-shortlisted-coding",
            category = "Coding",
            categoryTone = MarketMyTaskTone.MINT,
            status = "Shortlisted",
            statusTone = MarketMyTaskTone.LIME,
            title = "Help fixing a Python lab program",
            detailLine = "Your price Free · Waiting for poster",
            action = MarketMyTaskAction.PREVIEW_INFO
        ),
        MarketMyTaskItem(
            id = "applied-sent-notes",
            category = "Notes",
            categoryTone = MarketMyTaskTone.LAVENDER,
            status = "Sent",
            statusTone = MarketMyTaskTone.WARNING,
            title = "Handwritten notes for Ocular Anatomy",
            detailLine = "Your price Rs. 300 · 2 days left",
            action = MarketMyTaskAction.PREVIEW_INFO,
            pricePkr = 300
        ),
        MarketMyTaskItem(
            id = "applied-declined-typing",
            category = "Typing",
            categoryTone = MarketMyTaskTone.PEACH,
            status = "Declined",
            statusTone = MarketMyTaskTone.WARNING,
            title = "Typing a 10 page report",
            detailLine = "Your price Rs. 400 · Task closed",
            action = MarketMyTaskAction.PREVIEW_INFO,
            pricePkr = 400
        )
    )

    fun items(tab: MarketMyTasksTab): List<MarketMyTaskItem> = when (tab) {
        MarketMyTasksTab.POSTED -> postedTasks
        MarketMyTasksTab.APPLIED -> appliedTasks
    }

    fun tabLabel(tab: MarketMyTasksTab): String = when (tab) {
        MarketMyTasksTab.POSTED -> POSTED_TAB_LABEL
        MarketMyTasksTab.APPLIED -> APPLIED_TAB_LABEL
    }
}
