package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R
import java.util.Locale

internal data class MarketHelpTopic(
    val label: String,
    val icon: MarketHelpGlyph,
    val backgroundRes: Int
)

internal data class MarketHelpQuestion(
    val title: String,
    val answer: String
)

/** Screen 26 copy and local reference-preview behaviour. No live support/report data is accessed. */
internal object MarketHelpUiPolicy {
    const val TITLE = "Help and safety"
    const val SUBTITLE = "Rules, answers and support"
    const val SEARCH_HINT = "Search help"
    const val POPULAR_QUESTIONS = "Popular questions"
    const val CONTACT_TITLE = "Still stuck?"
    const val CONTACT_DESCRIPTION = "We reply in Notices within 2 days"
    const val CONTACT_BUTTON = "Contact support"
    const val EMPTY_RESULTS = "No help results found."

    const val RULES_PREVIEW = "The dedicated Rules page will contain the full posting policy. It is not connected in this reference step."
    const val SAFETY_TIPS = "Keep your account credentials private.\nRead a task or offer carefully before responding.\nUse the reporting flow for suspicious activity when it is available."
    const val FAQ_PREVIEW = "Choose one of the popular questions below for a preview answer. The dedicated FAQ page will be connected in its own reference step."
    const val REPORTS_PREVIEW = "My reports will be connected in its own reference step. This screen does not fetch, create, or update reports."
    const val SUPPORT_PREVIEW = "The support form will be connected in its own reference step. No support message has been sent or saved. Replies, when available, are shown in Notices."
    const val PREVIEW_DISCLOSURE = "This is a static reference preview. It does not read account status or actual reports, process payments, or send support requests."

    const val TOPIC_POSTING_RULES = "Posting rules"
    const val TOPIC_SAFETY_TIPS = "Safety tips"
    const val TOPIC_FAQS = "FAQs"
    const val TOPIC_MY_REPORTS = "My reports"

    val topics = listOf(
        MarketHelpTopic(TOPIC_POSTING_RULES, MarketHelpGlyph.POSTING_RULES, R.color.market_lav),
        MarketHelpTopic(TOPIC_SAFETY_TIPS, MarketHelpGlyph.SAFETY, R.color.market_mint),
        MarketHelpTopic(TOPIC_FAQS, MarketHelpGlyph.INFO, R.color.market_sky),
        MarketHelpTopic(TOPIC_MY_REPORTS, MarketHelpGlyph.FLAG, R.color.market_peach)
    )

    val questions = listOf(
        MarketHelpQuestion(
            "Does U2U handle payments?",
            "Payment guidance will be provided on the dedicated FAQ page. This preview does not process payments or confirm transaction status."
        ),
        MarketHelpQuestion(
            "Why was my listing removed?",
            "Review the notice about your listing for its stated reason. This reference page does not inspect a live listing or its moderation status."
        ),
        MarketHelpQuestion(
            "How do I report someone?",
            "The reporting flow will be connected in a separate reference step. This page does not submit or store a report."
        ),
        MarketHelpQuestion(
            "How long does a task stay open?",
            "Check the closing time and status shown on the task itself. This preview does not read a live task or change its availability."
        )
    )

    fun topicsFor(query: String): List<MarketHelpTopic> {
        val term = query.trim().lowercase(Locale.ROOT)
        return if (term.isEmpty()) topics else topics.filter {
            it.label.lowercase(Locale.ROOT).contains(term)
        }
    }

    fun questionsFor(query: String): List<MarketHelpQuestion> {
        val term = query.trim().lowercase(Locale.ROOT)
        return if (term.isEmpty()) questions else questions.filter {
            it.title.lowercase(Locale.ROOT).contains(term)
        }
    }

    fun hasReferenceContent(): Boolean =
        TITLE == "Help and safety" &&
            topics.map { it.label } == listOf(TOPIC_POSTING_RULES, TOPIC_SAFETY_TIPS, TOPIC_FAQS, TOPIC_MY_REPORTS) &&
            questions.size == 4 &&
            CONTACT_BUTTON == "Contact support"
}
