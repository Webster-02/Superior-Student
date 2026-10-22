package com.superiorstudent.app.feature.market.ui

internal data class MarketTaskChatMessage(
    val id: String,
    val body: String,
    val timeLabel: String,
    val fromMe: Boolean
)

/** Reference-only chat fixture; deliberately independent of Firebase and Student Connect chat. */
internal object MarketTaskChatUiPolicy {
    const val HELPER_NAME = "Zain Malik"
    const val TASK_TITLE = "Poster for health camp"
    const val PRICE_PKR = 1200
    const val DUE_LABEL = "Due 24 Oct"
    const val STATUS_LABEL = "Assigned"
    const val EVENT_LABEL = "Proposal accepted · Today"
    const val MAX_MESSAGE_LENGTH = 400

    val referenceMessages = listOf(
        MarketTaskChatMessage("incoming-1",
            "Thanks for selecting me! Please share the text and logo.", "4:10 PM", false),
        MarketTaskChatMessage("outgoing-1",
            "Sending now. Logo is a PDF so I will use Nearby share.", "4:12 PM", true),
        MarketTaskChatMessage("incoming-2",
            "Okay, I am free after 2 PM on campus.", "4:13 PM", false)
    )

    fun initials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return parts.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "ST" }
    }

    fun priceLabel(pricePkr: Int): String =
        "Rs. " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
            .format(pricePkr.coerceAtLeast(0))

    fun taskBudgetLine(pricePkr: Int): String = priceLabel(pricePkr) + " · " + DUE_LABEL

    fun normaliseMessage(raw: String): String? {
        val trimmed = raw.trim()
        return trimmed.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE_LENGTH }
    }

    fun appendPreviewMessage(
        items: List<MarketTaskChatMessage>,
        raw: String,
        timeLabel: String = "Now"
    ): List<MarketTaskChatMessage> {
        val body = normaliseMessage(raw) ?: return items
        return items + MarketTaskChatMessage(
            id = "preview-" + (items.size + 1),
            body = body,
            timeLabel = timeLabel,
            fromMe = true
        )
    }
}
