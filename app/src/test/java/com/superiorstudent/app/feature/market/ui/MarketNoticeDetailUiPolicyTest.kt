package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketNoticeDetailUiPolicyTest {
    @Test fun noticeDetailMatchesReferenceTitleAndOfferSummary() {
        assertEquals("Notice", MarketNoticeDetailUiPolicy.TITLE)
        assertEquals("Your offer was removed", MarketNoticeDetailUiPolicy.NOTICE_TITLE)
        assertEquals("Today, 11:20 AM", MarketNoticeDetailUiPolicy.TIMESTAMP)
        assertEquals("Assignment writing service", MarketNoticeDetailUiPolicy.OFFER_TITLE)
        assertEquals("Offer · Rs. 500", MarketNoticeDetailUiPolicy.OFFER_META)
    }

    @Test fun ruleReasonWarningLadderAndAppealReminderMatchReference() {
        assertTrue(MarketNoticeDetailUiPolicy.WHY_TEXT.contains("graded assignment"))
        assertTrue(MarketNoticeDetailUiPolicy.WHY_TEXT.contains("Rule 1"))
        assertTrue(MarketNoticeDetailUiPolicy.WHAT_HAPPENED_TEXT.contains("warning 1"))
        assertTrue(MarketNoticeDetailUiPolicy.WHAT_HAPPENED_TEXT.contains("30 day pause"))
        assertTrue(MarketNoticeDetailUiPolicy.INFO_TEXT.contains("One appeal per decision"))
    }

    @Test fun previewActionsNeverClaimAnAppealOrLiveRulesWereOpened() {
        assertTrue(MarketNoticeDetailUiPolicy.APPEAL_PREVIEW_MESSAGE.contains("No appeal has been submitted"))
        assertTrue(MarketNoticeDetailUiPolicy.RULES_PREVIEW_MESSAGE.contains("reference preview"))
        assertFalse(MarketNoticeDetailUiPolicy.APPEAL_PREVIEW_MESSAGE.contains("submitted successfully"))
    }

    @Test fun policyHasCompleteReferenceContent() {
        assertTrue(MarketNoticeDetailUiPolicy.hasReferenceContent())
    }
}
