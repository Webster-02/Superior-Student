package com.superiorstudent.app.feature.market.ui

internal object MarketTaskRatingUiPolicy {
    const val DEFAULT_RATING = 4
    const val MAX_RATING = 5
    const val MAX_COMMENT_LENGTH = 200
    const val DEFAULT_HELPER_NAME = "Zain Malik"
    const val DEFAULT_TASK_TITLE = "Poster for health camp"
    const val DEFAULT_COMMENT = "Delivered a day early and made the changes quickly."

    val tags = listOf(
        "On time",
        "Clear work",
        "Polite",
        "Great quality",
        "Easy to work with"
    )

    val defaultSelectedTags = linkedSetOf("On time", "Clear work", "Polite")

    fun clampRating(value: Int): Int = value.coerceIn(1, MAX_RATING)

    fun toggleTag(current: Set<String>, tag: String): Set<String> {
        if (tag !in tags) return current
        return if (tag in current) {
            current.filterNot { it == tag }.toCollection(linkedSetOf())
        } else {
            (current + tag).filter { it in tags }.toCollection(linkedSetOf())
        }
    }

    fun normaliseComment(raw: String): String? {
        val text = raw.trim()
        return text.takeIf { it.length <= MAX_COMMENT_LENGTH }
    }

    fun commentCounter(raw: String): String = "${raw.length}/$MAX_COMMENT_LENGTH"
}
