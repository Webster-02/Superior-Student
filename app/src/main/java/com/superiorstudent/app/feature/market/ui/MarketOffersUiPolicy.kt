package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R

internal data class MarketOfferPreview(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val priceLabel: String,
    val priceValue: Int?,
    val sellerName: String,
    val initials: String,
    val rating: Double,
    val ratingCount: Int,
    val verifiedStudent: Boolean,
    val avatarColor: Int,
    val recencyOrder: Int
)

internal enum class MarketOfferSort { REFERENCE_ORDER, HIGHEST_RATED, LOWEST_PRICE }

internal object MarketOfferFixtures {
    val all = listOf(
        MarketOfferPreview(
            id = "reference-notes-offer", category = "Notes",
            title = "Handwritten Ocular Anatomy notes, full semester",
            description = "Clean handwriting, colour-coded diagrams. Scan sent on chat or Nearby share.",
            priceLabel = "Rs. 300", priceValue = 300,
            sellerName = "Ayesha Khan", initials = "AK",
            rating = 4.9, ratingCount = 14, verifiedStudent = true,
            avatarColor = R.color.market_lav, recencyOrder = 0
        ),
        MarketOfferPreview(
            id = "reference-tutoring-offer", category = "Tutoring",
            title = "Calculus doubt solving, 1 hour sessions",
            description = "Explain step by step on campus or chat. First 15 minutes free.",
            priceLabel = "Rs. 500 / hr", priceValue = 500,
            sellerName = "Zain Malik", initials = "ZM",
            rating = 4.7, ratingCount = 9, verifiedStudent = true,
            avatarColor = R.color.market_sky, recencyOrder = 1
        ),
        MarketOfferPreview(
            id = "reference-typing-offer", category = "Typing",
            title = "Typing and formatting for reports",
            description = "Fast, proper margins and headings.",
            priceLabel = "Swap", priceValue = null,
            sellerName = "Noor Fatima", initials = "NF",
            rating = 5.0, ratingCount = 6, verifiedStudent = true,
            avatarColor = R.color.market_peach, recencyOrder = 2
        )
    )
}

/** Deterministic local policy for reference preview search/filter/sorting. */
internal object MarketOffersUiPolicy {
    fun filterAndSort(
        items: List<MarketOfferPreview>,
        category: String,
        query: String,
        sort: MarketOfferSort
    ): List<MarketOfferPreview> {
        val q = query.trim()
        val filtered = items.filter { item ->
            (category == "All" || item.category.equals(category, ignoreCase = true)) &&
                (q.isBlank() || item.title.contains(q, ignoreCase = true) ||
                    item.description.contains(q, ignoreCase = true) ||
                    item.category.contains(q, ignoreCase = true) ||
                    item.sellerName.contains(q, ignoreCase = true))
        }
        return when (sort) {
            MarketOfferSort.REFERENCE_ORDER -> filtered.sortedBy { it.recencyOrder }
            MarketOfferSort.HIGHEST_RATED -> filtered.sortedByDescending { it.rating }
            MarketOfferSort.LOWEST_PRICE -> filtered.sortedWith(
                compareBy<MarketOfferPreview> { it.priceValue == null }
                    .thenBy { it.priceValue ?: Int.MAX_VALUE }
            )
        }
    }
}
