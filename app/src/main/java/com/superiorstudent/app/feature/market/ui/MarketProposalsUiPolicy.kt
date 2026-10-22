package com.superiorstudent.app.feature.market.ui

internal enum class MarketProposalsFilter { ALL, SHORTLISTED, CHEAPEST, TOP_RATED }

internal data class MarketProposalPreview(
    val id: String,
    val name: String,
    val initials: String,
    val ratingLabel: String,
    val ratingScore: Double,
    val ageLabel: String,
    val pricePkr: Int,
    val message: String,
    val avatarTone: String,
    val shortlisted: Boolean = false
)

internal object MarketProposalsUiPolicy {
    const val TOTAL_PROPOSALS = 4
    const val MAX_PROPOSALS = 15
    const val TASK_LABEL = "Poster for health camp"

    val referenceProposals = listOf(
        MarketProposalPreview(
            id = "zain",
            name = "Zain Malik",
            initials = "ZM",
            ratingLabel = "4.7",
            ratingScore = 4.7,
            ageLabel = "2 days",
            pricePkr = 1200,
            message = "Hi, I have designed 3 society posters before. I can send a draft within a day and make one revision.",
            avatarTone = "sky",
            shortlisted = true
        ),
        MarketProposalPreview(
            id = "noor",
            name = "Noor Fatima",
            initials = "NF",
            ratingLabel = "5.0",
            ratingScore = 5.0,
            ageLabel = "3 days",
            pricePkr = 1500,
            message = "I use Illustrator and can share my portfolio on chat. Two colour options included.",
            avatarTone = "peach"
        ),
        MarketProposalPreview(
            id = "ayesha",
            name = "Ayesha Khan",
            initials = "AK",
            ratingLabel = "4.9",
            ratingScore = 4.9,
            ageLabel = "4 days",
            pricePkr = 1000,
            message = "Can do it, Canva template based, one revision.",
            avatarTone = "lav"
        )
    )

    fun capacityLabel(): String = "$TOTAL_PROPOSALS of $MAX_PROPOSALS"

    fun shortlistedLabel(items: List<MarketProposalPreview>): String =
        "${shortlistedCount(items)} shortlisted"

    fun shortlistedCount(items: List<MarketProposalPreview>): Int =
        items.count { it.shortlisted }.coerceIn(0, TOTAL_PROPOSALS)

    fun filterAndSort(
        items: List<MarketProposalPreview>,
        filter: MarketProposalsFilter
    ): List<MarketProposalPreview> = when (filter) {
        MarketProposalsFilter.ALL -> items.toList()
        MarketProposalsFilter.SHORTLISTED -> items.filter { it.shortlisted }
        MarketProposalsFilter.CHEAPEST -> items.sortedWith(
            compareBy<MarketProposalPreview> { it.pricePkr }.thenBy { it.name }
        )
        MarketProposalsFilter.TOP_RATED -> items.sortedWith(
            compareByDescending<MarketProposalPreview> { it.ratingScore }.thenBy { it.name }
        )
    }

    fun toggleShortlisted(items: List<MarketProposalPreview>, proposalId: String): List<MarketProposalPreview> =
        items.map { item ->
            if (item.id == proposalId) item.copy(shortlisted = !item.shortlisted) else item
        }

    val ACCEPTANCE_CHECKLIST = listOf(
        "Other proposals are declined and new ones stop",
        "You can mark the task complete when done"
    )

    const val ACCEPTANCE_SAFETY_NOTE =
        "The app does not handle money. Agree on a safe split in chat, for example half after seeing a draft, half on delivery."

    fun deliveryLabel(proposalId: String): String =
        if (proposalId == "zain") "2 days" else "Confirm in chat"

    fun priceLabel(pricePkr: Int): String =
        "Rs. " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
            .format(pricePkr.coerceAtLeast(0))
}
