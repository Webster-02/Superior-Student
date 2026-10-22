package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R

internal object MarketOfferDetailUiPolicy {
    const val REFERENCE_OFFER_ID = "reference-notes-offer"
    const val REFERENCE_CATEGORY = "Notes"
    const val REFERENCE_COURSE = "Ocular Anatomy"
    const val REFERENCE_TITLE = "Handwritten Ocular Anatomy notes, full semester"
    const val REFERENCE_DESCRIPTION =
        "Clean handwriting with colour-coded diagrams for Chapters 1 to 12. Scanned PDF is sent in chat, or we can meet on campus and use Nearby share. Sample page available before you decide."
    const val REFERENCE_PRICE = "Rs. 300"
    const val REFERENCE_SELLER = "Ayesha Khan"
    const val REFERENCE_INITIALS = "AK"
    const val REFERENCE_RATING = 4.9
    const val REFERENCE_REVIEW_COUNT = 14
    const val DELIVERY_LABEL = "Chat or Nearby share"
    const val SAFETY_NOTE = "The app does not handle money. Ask for a sample first."

    fun courseLabel(id: String, category: String): String = when (id) {
        REFERENCE_OFFER_ID -> REFERENCE_COURSE
        "reference-tutoring-offer" -> "Calculus"
        "reference-typing-offer" -> "Reports"
        else -> category
    }

    fun descriptionFor(id: String, fallback: String): String = when (id) {
        REFERENCE_OFFER_ID -> REFERENCE_DESCRIPTION
        "reference-tutoring-offer" ->
            "Step-by-step sessions on campus or in chat. The first 15 minutes are free so you can see whether the teaching style suits you."
        "reference-typing-offer" ->
            "Neat typing and report formatting with proper margins and headings. Message first to agree on the scope and exchange needs."
        else -> fallback.ifBlank { "Message the student to discuss the details before deciding." }
    }

    fun avatarColorFor(id: String): Int = when (id) {
        REFERENCE_OFFER_ID -> R.color.market_lav
        "reference-tutoring-offer" -> R.color.market_sky
        "reference-typing-offer" -> R.color.market_peach
        else -> R.color.market_mint
    }

    fun ratingLine(rating: Double, count: Int, verified: Boolean): String =
        "%.1f".format(java.util.Locale.US, rating) + " ($count reviews) · " +
            if (verified) "Verified student" else "Student"

    fun priceValueForChat(priceValue: Int): Int = priceValue.coerceAtLeast(0)
}
