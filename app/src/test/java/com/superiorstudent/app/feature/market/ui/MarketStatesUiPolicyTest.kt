package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketStatesUiPolicyTest {
    @Test
    fun screen21UsesReferenceLabelsAndAllFiveStateExamples() {
        assertEquals("States", MarketStatesUiPolicy.TITLE)
        assertEquals("Loading, empty, offline, error", MarketStatesUiPolicy.SUBTITLE)
        assertEquals("Offline · showing saved tasks. Posting needs internet.", MarketStatesUiPolicy.OFFLINE_NOTICE)
        assertEquals("No tasks in Design yet", MarketStatesUiPolicy.EMPTY_TITLE)
        assertEquals("Be the first to post one, or check other categories.", MarketStatesUiPolicy.EMPTY_DESCRIPTION)
        assertEquals("Could not refresh", MarketStatesUiPolicy.ERROR_TITLE)
        assertEquals("Showing saved tasks from 10 min ago.", MarketStatesUiPolicy.ERROR_DESCRIPTION)
        assertEquals("Task expired", MarketStatesUiPolicy.EXPIRED_TITLE)
        assertEquals("This task is closed and no longer takes proposals.", MarketStatesUiPolicy.EXPIRED_DESCRIPTION)
        assertEquals(listOf("offline", "loading", "empty", "error", "expired"), MarketStatesUiPolicy.statesInReferenceOrder)
    }

    @Test
    fun retryCopyDoesNotPretendLiveRefreshIsAlreadyWired() {
        assertTrue(MarketStatesUiPolicy.RETRY_EXPLANATION.contains("Live refresh is not connected yet"))
        assertTrue(MarketStatesUiPolicy.RETRY_EXPLANATION.contains("saved tasks remain visible"))
    }
}
