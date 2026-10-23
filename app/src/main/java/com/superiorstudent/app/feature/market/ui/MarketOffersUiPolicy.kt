package com.superiorstudent.app.feature.market.ui

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
    val recencyOrder: Int,
    val courseTag: String? = null
)

internal enum class MarketOfferSort { REFERENCE_ORDER, HIGHEST_RATED, LOWEST_PRICE }


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
