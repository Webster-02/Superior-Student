package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSuspendedUiPolicyTest {
    @Test fun referenceStatusAndReasonMatch() {
        assertEquals("Market", MarketSuspendedUiPolicy.TITLE)
        assertEquals("Posting is paused", MarketSuspendedUiPolicy.STATUS_TITLE)
        assertEquals("Until 27 Oct 2026 · 5 days left", MarketSuspendedUiPolicy.UNTIL_LABEL)
        assertEquals("Second violation of Rule 1 (graded work offers).", MarketSuspendedUiPolicy.REASON)
    }

    @Test fun allowedAndBlockedActionsMatchReference() {
        assertEquals(
            listOf("Browse offers and tasks", "Message people you already chat with", "Read notices and contact support"),
            MarketSuspendedUiPolicy.allowedItems
        )
        assertEquals("Post tasks or offers, or send proposals", MarketSuspendedUiPolicy.DENIED_ACTIONS)
        assertEquals("Rules", MarketSuspendedUiPolicy.RULES_BUTTON)
        assertEquals("Appeal", MarketSuspendedUiPolicy.APPEAL_BUTTON)
    }

    @Test fun staticReferenceDoesNotPretendToEnforceOrReadAccountState() {
        assertTrue(MarketSuspendedUiPolicy.PREVIEW_DISCLOSURE.contains("does not check"))
        assertTrue(MarketSuspendedUiPolicy.hasReferenceContent())
        assertFalse(MarketSuspendedUiPolicy.RULES_PREVIEW.contains("opened successfully"))
    }
}
