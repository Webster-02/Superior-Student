package com.superiorstudent.app.feature.market.ui

/** Copy and state ordering from the supplied Screen 21 reference. */
internal object MarketStatesUiPolicy {
    const val TITLE = "States"
    const val SUBTITLE = "Loading, empty, offline, error"
    const val OFFLINE_NOTICE = "Offline · showing saved tasks. Posting needs internet."
    const val EMPTY_TITLE = "No tasks in Design yet"
    const val EMPTY_DESCRIPTION = "Be the first to post one, or check other categories."
    const val ERROR_TITLE = "Could not refresh"
    const val ERROR_DESCRIPTION = "Showing saved tasks from 10 min ago."
    const val RETRY_EXPLANATION =
        "This is a reference preview. Live refresh is not connected yet; saved tasks remain visible."
    const val EXPIRED_TITLE = "Task expired"
    const val EXPIRED_DESCRIPTION = "This task is closed and no longer takes proposals."

    val statesInReferenceOrder = listOf("offline", "loading", "empty", "error", "expired")
}
