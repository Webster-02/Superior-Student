package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketMyOffersUiPolicyTest {
    @Test
    fun fixturesMatchReferenceTabCountsAndPrimaryCards() {
        val items = MarketMyOffersUiPolicy.referenceOffers
        assertEquals(2, MarketMyOffersUiPolicy.count(items, MarketMyOfferStatus.ACTIVE))
        assertEquals(1, MarketMyOffersUiPolicy.count(items, MarketMyOfferStatus.PAUSED))
        assertEquals(1, MarketMyOffersUiPolicy.count(items, MarketMyOfferStatus.EXPIRED))
        assertEquals("Handwritten Ocular Anatomy notes, full semester",
            MarketMyOffersUiPolicy.visibleItems(items, MarketMyOfferStatus.ACTIVE).first().title)
        assertEquals("Rs. 300",
            MarketMyOffersUiPolicy.visibleItems(items, MarketMyOfferStatus.ACTIVE).first().priceLabel)
        assertEquals("Typing and formatting for reports",
            MarketMyOffersUiPolicy.visibleItems(items, MarketMyOfferStatus.ACTIVE)[1].title)
    }

    @Test
    fun pauseAndResumeChangeLocalStatusOnly() {
        val active = MarketMyOffersUiPolicy.referenceOffers.first()
        val paused = MarketMyOffersUiPolicy.pauseOrResume(active)
        assertEquals(MarketMyOfferStatus.PAUSED, paused.status)
        assertEquals("Paused just now · Offer expires on its original date", paused.detailLine)
        val resumed = MarketMyOffersUiPolicy.pauseOrResume(paused)
        assertEquals(MarketMyOfferStatus.ACTIVE, resumed.status)
        assertEquals("Resumed just now · Offer is active", resumed.detailLine)
        val expired = MarketMyOffersUiPolicy.referenceOffers.last()
        assertEquals(expired, MarketMyOffersUiPolicy.pauseOrResume(expired))
    }

    @Test
    fun renewOnlyWorksWhenRenewalIsAvailableAndOfferIsNotPaused() {
        val notes = MarketMyOffersUiPolicy.referenceOffers.first()
        val renewed = MarketMyOffersUiPolicy.renew(notes)
        assertNotNull(renewed)
        assertEquals(MarketMyOfferStatus.ACTIVE, renewed?.status)
        assertFalse(renewed?.canRenew ?: true)
        assertEquals("Renewed just now · Expires in 30 days", renewed?.detailLine)
        val typing = MarketMyOffersUiPolicy.referenceOffers[1]
        assertNull(MarketMyOffersUiPolicy.renew(typing))
        val paused = MarketMyOffersUiPolicy.referenceOffers[2]
        assertNull(MarketMyOffersUiPolicy.renew(paused))
    }

    @Test
    fun categoryToneUsesReferencePaletteFamilies() {
        assertEquals(MarketMyOfferCategoryTone.LAVENDER, MarketMyOffersUiPolicy.categoryTone("Notes"))
        assertEquals(MarketMyOfferCategoryTone.PEACH, MarketMyOffersUiPolicy.categoryTone("Typing"))
        assertEquals(MarketMyOfferCategoryTone.SKY, MarketMyOffersUiPolicy.categoryTone("Tutoring"))
        assertTrue(MarketMyOffersUiPolicy.RENEWAL_RULE.contains("once every 7 days"))
        assertTrue(MarketMyOffersUiPolicy.RENEWAL_RULE.contains("30 days"))
    }
}
