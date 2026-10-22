package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHelpUiPolicyTest {
    @Test
    fun titleTopicsAndPopularQuestionsMatchReference() {
        assertEquals("Help and safety", MarketHelpUiPolicy.TITLE)
        assertEquals("Rules, answers and support", MarketHelpUiPolicy.SUBTITLE)
        assertEquals(
            listOf("Posting rules", "Safety tips", "FAQs", "My reports"),
            MarketHelpUiPolicy.topics.map { it.label }
        )
        assertEquals(
            listOf(
                "Does U2U handle payments?",
                "Why was my listing removed?",
                "How do I report someone?",
                "How long does a task stay open?"
            ),
            MarketHelpUiPolicy.questions.map { it.title }
        )
        assertEquals("Contact support", MarketHelpUiPolicy.CONTACT_BUTTON)
    }

    @Test
    fun searchFiltersTopicAndPopularQuestionCopyCaseInsensitively() {
        assertEquals(listOf("Posting rules"),
            MarketHelpUiPolicy.topicsFor("RULES").map { it.label })
        assertEquals(listOf("FAQs"),
            MarketHelpUiPolicy.topicsFor("faq").map { it.label })
        assertEquals(listOf("Does U2U handle payments?"),
            MarketHelpUiPolicy.questionsFor("payment").map { it.title })
        assertEquals(4, MarketHelpUiPolicy.questionsFor("   ").size)
        assertEquals(4, MarketHelpUiPolicy.topicsFor("").size)
        assertTrue(MarketHelpUiPolicy.questionsFor("no matching phrase").isEmpty())
    }

    @Test
    fun supportAndReportsAreClearlyPreviewOnly() {
        assertTrue(MarketHelpUiPolicy.SUPPORT_PREVIEW.contains("No support message has been sent or saved"))
        assertTrue(MarketHelpUiPolicy.REPORTS_PREVIEW.contains("does not fetch, create, or update reports"))
        assertTrue(MarketHelpUiPolicy.PREVIEW_DISCLOSURE.contains("static reference preview"))
        assertTrue(MarketHelpUiPolicy.hasReferenceContent())
    }
}
