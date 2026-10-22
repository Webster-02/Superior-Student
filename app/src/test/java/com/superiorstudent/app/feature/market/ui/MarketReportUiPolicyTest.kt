package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketReportUiPolicyTest {
    @Test
    fun screen20ReasonsAndDefaultMatchReferenceOrder() {
        assertEquals(
            listOf(
                "Asking for graded assignment, quiz or exam work",
                "Scam or asks for full payment first",
                "Spam or repeated posts",
                "Inappropriate or abusive",
                "Something else"
            ),
            MarketReportUiPolicy.REASONS
        )
        assertEquals(MarketReportUiPolicy.REASONS.first(), MarketReportUiPolicy.DEFAULT_REASON)
        assertEquals("Report this task", MarketReportUiPolicy.TITLE)
    }

    @Test
    fun rejectsReasonsOutsideTheReferenceChoices() {
        assertTrue(MarketReportUiPolicy.isSupportedReason("Spam or repeated posts"))
        assertFalse(MarketReportUiPolicy.isSupportedReason("Just because"))
    }

    @Test
    fun submitConfirmationNeverClaimsReportOrBlockWasPersisted() {
        val withBlock = MarketReportUiPolicy.previewConfirmation(
            "Hamza Ali", MarketReportUiPolicy.DEFAULT_REASON, true
        )
        assertTrue(withBlock.contains("No report was submitted"))
        assertTrue(withBlock.contains("Hamza Ali was not blocked"))

        val withoutBlock = MarketReportUiPolicy.previewConfirmation(
            "Hamza Ali", "Spam or repeated posts", false
        )
        assertTrue(withoutBlock.contains("No block action was taken"))
    }

    @Test
    fun blankOwnerAndUnknownReasonReceiveSafeFallbacks() {
        val message = MarketReportUiPolicy.previewConfirmation("", "not a reason", false)
        assertTrue(message.contains("the task owner"))
        assertTrue(message.contains(MarketReportUiPolicy.DEFAULT_REASON))
    }
}
