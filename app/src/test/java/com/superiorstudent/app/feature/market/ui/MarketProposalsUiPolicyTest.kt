package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketProposalsUiPolicyTest {
    @Test
    fun referenceProposalsMatchTheVisibleCards() {
        val items = MarketProposalsUiPolicy.referenceProposals
        assertEquals(listOf("Zain Malik", "Noor Fatima", "Ayesha Khan"), items.map { it.name })
        assertEquals(listOf(1200, 1500, 1000), items.map { it.pricePkr })
        assertEquals(listOf("ZM", "NF", "AK"), items.map { it.initials })
        assertEquals(1, MarketProposalsUiPolicy.shortlistedCount(items))
        assertEquals("4 of 15", MarketProposalsUiPolicy.capacityLabel())
    }

    @Test
    fun allFilterKeepsReferenceOrder() {
        assertEquals(
            listOf("zain", "noor", "ayesha"),
            MarketProposalsUiPolicy.filterAndSort(
                MarketProposalsUiPolicy.referenceProposals, MarketProposalsFilter.ALL
            ).map { it.id }
        )
    }

    @Test
    fun shortlistedFilterReturnsOnlyShortlistedPreviews() {
        val items = MarketProposalsUiPolicy.referenceProposals
        assertEquals(
            listOf("zain"),
            MarketProposalsUiPolicy.filterAndSort(items, MarketProposalsFilter.SHORTLISTED).map { it.id }
        )
        assertEquals("1 shortlisted", MarketProposalsUiPolicy.shortlistedLabel(items))
    }

    @Test
    fun cheapestFilterSortsByPkrAscending() {
        assertEquals(
            listOf("ayesha", "zain", "noor"),
            MarketProposalsUiPolicy.filterAndSort(
                MarketProposalsUiPolicy.referenceProposals, MarketProposalsFilter.CHEAPEST
            ).map { it.id }
        )
    }

    @Test
    fun topRatedFilterSortsByRatingDescending() {
        assertEquals(
            listOf("noor", "ayesha", "zain"),
            MarketProposalsUiPolicy.filterAndSort(
                MarketProposalsUiPolicy.referenceProposals, MarketProposalsFilter.TOP_RATED
            ).map { it.id }
        )
    }

    @Test
    fun shortlistChangesAreInMemoryAndReversible() {
        val original = MarketProposalsUiPolicy.referenceProposals
        val added = MarketProposalsUiPolicy.toggleShortlisted(original, "ayesha")
        assertTrue(added.first { it.id == "ayesha" }.shortlisted)
        assertEquals(2, MarketProposalsUiPolicy.shortlistedCount(added))
        val removed = MarketProposalsUiPolicy.toggleShortlisted(added, "zain")
        assertFalse(removed.first { it.id == "zain" }.shortlisted)
        assertEquals(1, MarketProposalsUiPolicy.shortlistedCount(removed))
        assertTrue(original.first { it.id == "zain" }.shortlisted)
    }

    @Test
    fun proposalPricesAreFormattedAsWholePkrAmounts() {
        assertEquals("Rs. 1,200", MarketProposalsUiPolicy.priceLabel(1200))
        assertEquals("Rs. 1,500", MarketProposalsUiPolicy.priceLabel(1500))
    }
}
