package com.superiorstudent.app.feature.market.ui

internal enum class MarketMyOfferStatus { ACTIVE, PAUSED, EXPIRED }

internal data class MarketMyOfferPreview(
    val id: String,
    val category: String,
    val title: String,
    val priceLabel: String,
    val detailLine: String,
    val status: MarketMyOfferStatus,
    val canRenew: Boolean,
    val description: String,
    val amount: String,
    val priceMode: MarketPostOfferPriceMode
)

internal object MarketMyOffersUiPolicy {
    const val RENEWAL_RULE = "Renew moves your offer back to the top. You can renew once every 7 days. Offers expire after 30 days."

    val referenceOffers = listOf(
        MarketMyOfferPreview(
            id = "my-offer-anatomy-notes",
            category = "Notes",
            title = "Handwritten Ocular Anatomy notes, full semester",
            priceLabel = "Rs. 300",
            detailLine = "Posted 12 Oct · Expires in 18 days",
            status = MarketMyOfferStatus.ACTIVE,
            canRenew = true,
            description = "Clean handwriting, colour-coded diagrams. Sample page on request.",
            amount = "300",
            priceMode = MarketPostOfferPriceMode.RUPEES
        ),
        MarketMyOfferPreview(
            id = "my-offer-typing-reports",
            category = "Typing",
            title = "Typing and formatting for reports",
            priceLabel = "Swap",
            detailLine = "Posted 2 Oct · Renew available in 3 days",
            status = MarketMyOfferStatus.ACTIVE,
            canRenew = false,
            description = "Fast, proper margins and headings.",
            amount = "",
            priceMode = MarketPostOfferPriceMode.SWAP
        ),
        MarketMyOfferPreview(
            id = "my-offer-calculus-tutoring",
            category = "Tutoring",
            title = "Calculus doubt solving, 1 hour sessions",
            priceLabel = "Rs. 500 / hr",
            detailLine = "Paused 18 Oct · Expires in 10 days",
            status = MarketMyOfferStatus.PAUSED,
            canRenew = false,
            description = "Explain concepts step by step, on campus or in chat.",
            amount = "500",
            priceMode = MarketPostOfferPriceMode.RUPEES
        ),
        MarketMyOfferPreview(
            id = "my-offer-labcoat",
            category = "Books",
            title = "Optometry lab coat, good condition",
            priceLabel = "Free",
            detailLine = "Expired 1 day ago",
            status = MarketMyOfferStatus.EXPIRED,
            canRenew = true,
            description = "Clean lab coat, available for a student who needs one.",
            amount = "",
            priceMode = MarketPostOfferPriceMode.FREE
        )
    )

    fun count(items: List<MarketMyOfferPreview>, status: MarketMyOfferStatus): Int =
        items.count { it.status == status }

    fun visibleItems(
        items: List<MarketMyOfferPreview>,
        status: MarketMyOfferStatus
    ): List<MarketMyOfferPreview> = items.filter { it.status == status }

    fun categoryTone(category: String): MarketMyOfferCategoryTone = when (category) {
        "Notes", "Books" -> MarketMyOfferCategoryTone.LAVENDER
        "Typing" -> MarketMyOfferCategoryTone.PEACH
        "Tutoring" -> MarketMyOfferCategoryTone.SKY
        else -> MarketMyOfferCategoryTone.MINT
    }

    fun pauseOrResume(item: MarketMyOfferPreview): MarketMyOfferPreview = when (item.status) {
        MarketMyOfferStatus.ACTIVE -> item.copy(
            status = MarketMyOfferStatus.PAUSED,
            detailLine = "Paused just now · Offer expires on its original date"
        )
        MarketMyOfferStatus.PAUSED -> item.copy(
            status = MarketMyOfferStatus.ACTIVE,
            detailLine = "Resumed just now · Offer is active"
        )
        MarketMyOfferStatus.EXPIRED -> item
    }

    fun renew(item: MarketMyOfferPreview): MarketMyOfferPreview? {
        if (!item.canRenew || item.status == MarketMyOfferStatus.PAUSED) return null
        return item.copy(
            status = MarketMyOfferStatus.ACTIVE,
            canRenew = false,
            detailLine = "Renewed just now · Expires in 30 days"
        )
    }
}

internal enum class MarketMyOfferCategoryTone { LAVENDER, PEACH, SKY, MINT }
