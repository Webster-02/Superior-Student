package com.superiorstudent.app.feature.market.ui

internal enum class MarketPostOfferPriceMode(val label: String) {
    FREE("Free"),
    SWAP("Swap"),
    RUPEES("Rs.")
}

internal data class MarketPostOfferInput(
    val title: String,
    val category: String,
    val priceMode: MarketPostOfferPriceMode,
    val amount: String,
    val description: String
)

internal data class MarketPostOfferValidation(
    val titleError: String? = null,
    val categoryError: String? = null,
    val amountError: String? = null,
    val descriptionError: String? = null
) {
    val isValid: Boolean
        get() = titleError == null && categoryError == null &&
            amountError == null && descriptionError == null
}

/** Client-side validation for the real offer form; Firestore Rules remain authoritative. */
internal object MarketPostOfferUiPolicy {
    const val MAX_TITLE_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 400
    const val MAX_AMOUNT = 99999999
    const val MAX_COVER_BYTES = 100 * 1024

    const val REFERENCE_TITLE = "Handwritten Ocular Anatomy notes"
    const val REFERENCE_CATEGORY = "Notes"
    const val REFERENCE_AMOUNT = "300"
    const val REFERENCE_DESCRIPTION =
        "Clean handwriting, colour-coded diagrams. Sample page on request."

    val categories = listOf("Notes", "Tutoring", "Design", "Coding", "Typing", "Books")
    val priceModes = MarketPostOfferPriceMode.values().toList()

    fun validate(input: MarketPostOfferInput): MarketPostOfferValidation {
        val title = input.title.trim()
        val description = input.description.trim()
        val amount = input.amount.trim().toIntOrNull()
        return MarketPostOfferValidation(
            titleError = when {
                title.isBlank() -> "Enter an offer title"
                title.length < 5 -> "Title must be at least 5 characters"
                title.length > MAX_TITLE_LENGTH -> "Title must be 80 characters or fewer"
                else -> null
            },
            categoryError = if (input.category in categories) null else "Choose a category",
            amountError = when {
                input.priceMode != MarketPostOfferPriceMode.RUPEES -> null
                amount == null || amount <= 0 -> "Enter an amount greater than Rs. 0"
                amount > MAX_AMOUNT -> "Amount is too large"
                else -> null
            },
            descriptionError = when {
                description.isBlank() -> "Add a short description"
                description.length < 10 -> "Description must be at least 10 characters"
                description.length > MAX_DESCRIPTION_LENGTH ->
                    "Description must be 400 characters or fewer"
                else -> null
            }
        )
    }

    fun descriptionCounter(description: String): String =
        "${description.length.coerceAtMost(MAX_DESCRIPTION_LENGTH)}/$MAX_DESCRIPTION_LENGTH"

    fun priceSummary(mode: MarketPostOfferPriceMode, amount: String): String = when (mode) {
        MarketPostOfferPriceMode.FREE -> "Free"
        MarketPostOfferPriceMode.SWAP -> "Swap"
        MarketPostOfferPriceMode.RUPEES -> "Rs. " + amount.trim()
    }

    fun compressedCoverLabel(byteCount: Int): String {
        val safeCount = byteCount.coerceIn(0, MAX_COVER_BYTES)
        return if (safeCount < 1024) "${safeCount} bytes · ready to post"
        else "${"%.1f".format(java.util.Locale.US, safeCount / 1024.0)} KB · ready to post"
    }
}
