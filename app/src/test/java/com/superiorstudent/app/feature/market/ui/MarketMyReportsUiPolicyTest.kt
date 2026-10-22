package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketMyReportsUiPolicyTest {
    @Test
    fun referenceHasExpectedHeadingAndPrivacyNotice() {
        assertEquals("My reports", MarketMyReportsUiPolicy.TITLE)
        assertEquals("What happened to your reports", MarketMyReportsUiPolicy.SUBTITLE)
        assertEquals(
            "We never tell the other student who reported them. Thank you for keeping the market safe.",
            MarketMyReportsUiPolicy.PRIVACY_NOTE
        )
        assertTrue(MarketMyReportsUiPolicy.hasReferenceContent())
    }

    @Test
    fun sampleRowsAndStatusOrderMatchReference() {
        assertEquals(
            listOf("Offer: Assignment writing service", "Task: Pay first, work later",
                "Offer: Notes bundle", "User: Bilal Raza"),
            MarketMyReportsUiPolicy.reports.map { it.title }
        )
        assertEquals(
            listOf("Graded work · 19 Oct", "Scam · 18 Oct", "Spam · 14 Oct", "Abusive · Today"),
            MarketMyReportsUiPolicy.reports.map { it.context }
        )
        assertEquals(
            listOf("Action taken", "Under review", "Dismissed", "Received"),
            MarketMyReportsUiPolicy.reports.map { it.status.label }
        )
        assertEquals(4, MarketMyReportsUiPolicy.reports.size)
    }

    @Test
    fun sampleRowsAreExplicitlyNotLiveAccountData() {
        assertTrue(MarketMyReportsUiPolicy.SAMPLE_ROW_NOTICE.contains("sample content"))
        assertTrue(MarketMyReportsUiPolicy.SAMPLE_ROW_NOTICE.contains("not connected"))
    }
}
