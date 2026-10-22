package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketNoticesUiPolicyTest {
    @Test fun tabsMatchReferenceLabelsAndCounts() {
        assertEquals("Activity 3", MarketNoticesTab.ACTIVITY.label)
        assertEquals("U2U team 2", MarketNoticesTab.TEAM.label)
    }
    @Test fun teamTabUsesReferenceOrderCopyAndRetentionNote() {
        assertEquals(listOf("Your offer was removed", "Reply from support", "Midterm week reminder"),
            MarketNoticesUiPolicy.teamNotices.map { it.title })
        assertEquals("\"Assignment writing service\" broke posting rule 1. Tap to see details or appeal.",
            MarketNoticesUiPolicy.teamNotices.first().description)
        assertEquals("Messages from the team stay for 60 days.", MarketNoticesUiPolicy.RETENTION_NOTE)
    }
    @Test fun unreadDotsMatchReferenceScreenshot() {
        assertEquals(listOf(true, true, false), MarketNoticesUiPolicy.teamNotices.map { it.unread })
    }
    @Test fun activityTabUsesThreeLocalFixtures() {
        assertEquals(3, MarketNoticesUiPolicy.noticesFor(MarketNoticesTab.ACTIVITY).size)
        assertTrue(MarketNoticesUiPolicy.activityNotices.all { it.title.isNotBlank() && it.description.isNotBlank() })
        assertTrue(MarketNoticesUiPolicy.PREVIEW_DISCLOSURE.contains("not connected"))
    }
    @Test fun removedOfferUsesPreviewOnlyAppealDisclosure() {
        assertTrue(MarketNoticesUiPolicy.disclosureFor(MarketNoticesUiPolicy.teamNotices.first()).contains("appeal flow"))
        assertFalse(MarketNoticesUiPolicy.disclosureFor(MarketNoticesUiPolicy.teamNotices[1]).contains("appeal flow"))
    }
}
