package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketAppealUiPolicyTest {
    @Test fun referenceLabelsAndOfferSummaryMatch() {
        assertEquals("Appeal", MarketAppealUiPolicy.TITLE)
        assertEquals("Ask us to review again", MarketAppealUiPolicy.SUBTITLE)
        assertEquals("Assignment writing service", MarketAppealUiPolicy.OFFER_TITLE)
        assertEquals("Removed today · Rule 1", MarketAppealUiPolicy.OFFER_SUBTITLE)
        assertEquals("Removed", MarketAppealUiPolicy.REMOVED_LABEL)
    }
    @Test fun sampleReasonAndCharacterLimitMatchReference() {
        assertEquals(120, MarketAppealUiPolicy.characterCount(MarketAppealUiPolicy.DEFAULT_REASON))
        assertEquals(400, MarketAppealUiPolicy.MAX_REASON_LENGTH)
        assertTrue(MarketAppealUiPolicy.isReasonValid(MarketAppealUiPolicy.DEFAULT_REASON))
        assertTrue(MarketAppealUiPolicy.isReasonValid("x".repeat(400)))
        assertFalse(MarketAppealUiPolicy.isReasonValid("x".repeat(401)))
    }
    @Test fun emptyAndWhitespaceReasonsCannotContinue() {
        assertFalse(MarketAppealUiPolicy.isReasonValid(""))
        assertFalse(MarketAppealUiPolicy.isReasonValid("   "))
    }
    @Test fun infoAndSubmissionPreviewNeverClaimToSendAppeal() {
        assertTrue(MarketAppealUiPolicy.INFO_TEXT.contains("usually within 3 days"))
        assertTrue(MarketAppealUiPolicy.INFO_TEXT.contains("only one appeal"))
        assertTrue(MarketAppealUiPolicy.PREVIEW_MESSAGE.contains("has not been sent or saved"))
        assertFalse(MarketAppealUiPolicy.PREVIEW_MESSAGE.contains("submitted successfully"))
    }
}
