package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSendProposalUiPolicyTest {
    private val valid = MarketSendProposalDraft(
        message = MarketSendProposalUiPolicy.REFERENCE_MESSAGE,
        price = "1,200",
        deliveryDays = "2"
    )

    @Test
    fun referenceDraftAndCounterMatchVisibleMessageLength() {
        assertTrue(MarketSendProposalUiPolicy.validate(valid).isValid)
        assertEquals("106/400", MarketSendProposalUiPolicy.messageCounter(valid.message))
    }

    @Test
    fun blankAndOverlongMessagesAreRejected() {
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(message = "  ")).messageError)
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(message = "x".repeat(401))).messageError)
        assertNull(MarketSendProposalUiPolicy.validate(valid.copy(message = "A helpful proposal")).messageError)
    }

    @Test
    fun priceAcceptsWholePositiveAmountsWithOptionalThousandsSeparators() {
        assertNull(MarketSendProposalUiPolicy.validate(valid.copy(price = "1200")).priceError)
        assertNull(MarketSendProposalUiPolicy.validate(valid.copy(price = "1,200")).priceError)
        assertEquals("Rs. 1,200", MarketSendProposalUiPolicy.formattedPrice("1200"))
        assertEquals("Rs. 12,500", MarketSendProposalUiPolicy.formattedPrice("12,500"))
    }

    @Test
    fun priceRejectsZeroDecimalsAndMalformedSeparators() {
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(price = "0")).priceError)
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(price = "12.50")).priceError)
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(price = "1,,200")).priceError)
        assertNull(MarketSendProposalUiPolicy.formattedPrice("0"))
    }

    @Test
    fun deliveryAcceptsOneToThirtyDaysAndRejectsInvalidValues() {
        MarketSendProposalUiPolicy.QUICK_PICK_DAYS.forEach { days ->
            assertTrue(MarketSendProposalUiPolicy.validate(valid.copy(deliveryDays = days.toString())).isValid)
        }
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(deliveryDays = "0")).deliveryError)
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(deliveryDays = "31")).deliveryError)
        assertNotNull(MarketSendProposalUiPolicy.validate(valid.copy(deliveryDays = "two")).deliveryError)
    }

    @Test
    fun taskSubtitleKeepsReferenceCopyAndTruncatesLongGenericTitles() {
        assertEquals("Task: Poster for health camp",
            MarketSendProposalUiPolicy.taskSubtitle("reference-design", "Poster for Optometry Society health camp"))
        assertTrue(MarketSendProposalUiPolicy.taskSubtitle("other", "A very long task title that needs truncation").length < 40)
    }

    @Test
    fun defaultMessageUsesOwnerGreetingOutsideReferenceFixture() {
        assertTrue(MarketSendProposalUiPolicy.defaultMessage("reference-design", "Someone").startsWith("Hi Hamza,"))
        assertTrue(MarketSendProposalUiPolicy.defaultMessage("other", "Ayesha Khan").startsWith("Hi Ayesha,"))
    }
}
