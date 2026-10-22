package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketOfferDetailUiPolicyTest {
    @Test
    fun referenceOfferMatchesScreen17Details() {
        assertEquals("reference-notes-offer", MarketOfferDetailUiPolicy.REFERENCE_OFFER_ID)
        assertEquals("Notes", MarketOfferDetailUiPolicy.REFERENCE_CATEGORY)
        assertEquals("Ocular Anatomy", MarketOfferDetailUiPolicy.courseLabel(
            MarketOfferDetailUiPolicy.REFERENCE_OFFER_ID, "Notes"))
        assertEquals("Handwritten Ocular Anatomy notes, full semester",
            MarketOfferDetailUiPolicy.REFERENCE_TITLE)
        assertEquals("Rs. 300", MarketOfferDetailUiPolicy.REFERENCE_PRICE)
        assertEquals("Ayesha Khan", MarketOfferDetailUiPolicy.REFERENCE_SELLER)
        assertEquals(4.9, MarketOfferDetailUiPolicy.REFERENCE_RATING, 0.001)
        assertEquals(14, MarketOfferDetailUiPolicy.REFERENCE_REVIEW_COUNT)
        assertEquals("Chat or Nearby share", MarketOfferDetailUiPolicy.DELIVERY_LABEL)
    }

    @Test
    fun aboutCopyAndSafetyNoticeMatchReference() {
        val details = MarketOfferDetailUiPolicy.descriptionFor(
            MarketOfferDetailUiPolicy.REFERENCE_OFFER_ID, "fallback")
        assertTrue(details.contains("colour-coded diagrams for Chapters 1 to 12"))
        assertTrue(details.contains("Sample page available before you decide"))
        assertEquals("The app does not handle money. Ask for a sample first.",
            MarketOfferDetailUiPolicy.SAFETY_NOTE)
    }

    @Test
    fun ratingLineAndChatPricesAreSafe() {
        assertEquals(0, MarketOfferDetailUiPolicy.priceValueForChat(-1))
        assertEquals(300, MarketOfferDetailUiPolicy.priceValueForChat(300))
        assertEquals("4.9 (14 reviews) · Verified student",
            MarketOfferDetailUiPolicy.ratingLine(4.9, 14, true))
        assertEquals("4.7 (9 reviews) · Student",
            MarketOfferDetailUiPolicy.ratingLine(4.7, 9, false))
    }

    @Test
    fun otherOfferFixturesGetMatchingCourseAndAboutCopy() {
        assertEquals("Calculus", MarketOfferDetailUiPolicy.courseLabel(
            "reference-tutoring-offer", "Tutoring"))
        assertEquals("Reports", MarketOfferDetailUiPolicy.courseLabel(
            "reference-typing-offer", "Typing"))
        assertTrue(MarketOfferDetailUiPolicy.descriptionFor(
            "reference-tutoring-offer", "fallback").contains("first 15 minutes are free"))
        assertTrue(MarketOfferDetailUiPolicy.descriptionFor(
            "reference-typing-offer", "fallback").contains("proper margins and headings"))
    }
}
