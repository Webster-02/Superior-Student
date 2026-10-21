package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketOffersUiPolicyTest {
    private val offers = listOf(
        offer("notes", "Notes", "Ocular Anatomy notes", "Ayesha Khan", 4.9, 14, 300, 0),
        offer("tutoring", "Tutoring", "Calculus tutoring", "Zain Malik", 4.7, 9, 500, 1),
        offer("typing", "Typing", "Report typing", "Noor Fatima", 5.0, 6, null, 2)
    )

    @Test fun categoryFilterReturnsOnlyMatchingOffers() {
        assertEquals(
            listOf("notes"),
            MarketOffersUiPolicy.filterAndSort(offers, "Notes", "", MarketOfferSort.REFERENCE_ORDER).map { it.id }
        )
    }

    @Test fun searchMatchesSellerAndOfferTitleCaseInsensitively() {
        assertEquals(
            listOf("typing"),
            MarketOffersUiPolicy.filterAndSort(offers, "All", "Noor", MarketOfferSort.REFERENCE_ORDER).map { it.id }
        )
        assertEquals(
            listOf("notes"),
            MarketOffersUiPolicy.filterAndSort(offers, "All", "OCULAR", MarketOfferSort.REFERENCE_ORDER).map { it.id }
        )
    }

    @Test fun highestRatingSortsDescending() {
        assertEquals(
            listOf("typing", "notes", "tutoring"),
            MarketOffersUiPolicy.filterAndSort(offers, "All", "", MarketOfferSort.HIGHEST_RATED).map { it.id }
        )
    }

    @Test fun lowestPriceSortKeepsSwapOffersAfterPricedOffers() {
        assertEquals(
            listOf("notes", "tutoring", "typing"),
            MarketOffersUiPolicy.filterAndSort(offers, "All", "", MarketOfferSort.LOWEST_PRICE).map { it.id }
        )
    }

    private fun offer(
        id: String, category: String, title: String, seller: String,
        rating: Double, ratingCount: Int, price: Int?, order: Int
    ) = MarketOfferPreview(
        id = id, category = category, title = title, description = "Description",
        priceLabel = "Price", priceValue = price, sellerName = seller,
        initials = "SS", rating = rating, ratingCount = ratingCount,
        verifiedStudent = true, avatarColor = 0, recencyOrder = order
    )
}
