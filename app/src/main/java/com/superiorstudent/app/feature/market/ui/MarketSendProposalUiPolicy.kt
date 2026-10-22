package com.superiorstudent.app.feature.market.ui

internal data class MarketSendProposalDraft(
    val message: String,
    val price: String,
    val deliveryDays: String
)

internal data class MarketSendProposalValidation(
    val messageError: String? = null,
    val priceError: String? = null,
    val deliveryError: String? = null
) {
    val isValid: Boolean
        get() = messageError == null && priceError == null && deliveryError == null
}

internal object MarketSendProposalUiPolicy {
    const val MAX_MESSAGE_LENGTH = 400
    const val DEFAULT_PRICE = "1,200"
    const val DEFAULT_DELIVERY_DAYS = "2"
    const val REFERENCE_MESSAGE =
        "Hi Hamza, I have designed 3 society posters before. I can send a draft within a day and make one revision."
    val QUICK_PICK_DAYS = listOf(1, 2, 3, 5, 7)

    fun defaultMessage(taskId: String, author: String): String {
        if (taskId == "reference-design") return REFERENCE_MESSAGE
        val firstName = author.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        val greeting = firstName.takeIf { it.isNotBlank() && !it.equals("Student", true) }
            ?.let { "Hi " + it + "," }
            ?: "Hi,"
        return greeting + " I can help with this task and will share a clear plan before starting. Let me know if you have any preferences."
    }

    fun taskSubtitle(taskId: String, title: String): String {
        if (taskId == "reference-design") return "Task: Poster for health camp"
        val cleaned = title.trim().ifBlank { "Task" }
        val shortTitle = if (cleaned.length > 30) cleaned.take(29).trimEnd() + "…" else cleaned
        return "Task: " + shortTitle
    }

    fun messageCounter(message: String): String =
        message.length.coerceAtMost(MAX_MESSAGE_LENGTH).toString() + "/" + MAX_MESSAGE_LENGTH

    fun validate(draft: MarketSendProposalDraft): MarketSendProposalValidation {
        val trimmedMessage = draft.message.trim()
        val rawPrice = draft.price.trim()
        val validCommaFormat = Regex("[0-9]+|[0-9]{1,3}(,[0-9]{3})+").matches(rawPrice)
        val numericPrice = if (validCommaFormat) rawPrice.replace(",", "").toLongOrNull() else null
        val days = draft.deliveryDays.trim().toIntOrNull()
        return MarketSendProposalValidation(
            messageError = when {
                trimmedMessage.isBlank() -> "Write a short message for the task poster."
                draft.message.length > MAX_MESSAGE_LENGTH -> "Message must be 400 characters or fewer."
                else -> null
            },
            priceError = when {
                numericPrice == null || numericPrice <= 0L -> "Enter a whole price greater than zero."
                else -> null
            },
            deliveryError = when {
                days == null || days !in 1..30 -> "Choose delivery within 1 to 30 days."
                else -> null
            }
        )
    }

    fun formattedPrice(price: String): String? {
        val raw = price.trim()
        val validCommaFormat = Regex("[0-9]+|[0-9]{1,3}(,[0-9]{3})+").matches(raw)
        if (!validCommaFormat) return null
        val amount = raw.replace(",", "").toLongOrNull()?.takeIf { it > 0L } ?: return null
        return "Rs. " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(amount)
    }
}
