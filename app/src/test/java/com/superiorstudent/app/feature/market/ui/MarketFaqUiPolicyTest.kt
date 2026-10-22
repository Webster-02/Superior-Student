package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFaqUiPolicyTest {
    @Test
    fun referenceHasExpectedTitleCategoriesAndQuestions() {
        assertEquals("FAQs", MarketFaqUiPolicy.TITLE)
        assertEquals(
            listOf("All", "Posting", "Proposals", "Safety", "Account"),
            MarketFaqUiPolicy.categories.map { it.label }
        )
        assertEquals(
            listOf(
                "How do I get my proposal accepted?",
                "Does U2U handle payments?",
                "Why was my listing removed?",
                "How do I report someone?",
                "How long does a task stay open?",
                "Can I edit a task after posting?",
                "How do I appeal a decision?"
            ),
            MarketFaqUiPolicy.questions.map { it.title }
        )
        assertEquals(
            "Be specific about what you will deliver and when. Mention similar work you have done. Fair prices and a short, clear message get chosen more often.",
            MarketFaqUiPolicy.questions.first().answer
        )
        assertTrue(MarketFaqUiPolicy.hasReferenceContent())
    }

    @Test
    fun selectingCategoryOnlyShowsMatchingQuestions() {
        assertEquals(7, MarketFaqUiPolicy.questionsFor(MarketFaqCategory.ALL).size)
        assertEquals(
            listOf("Why was my listing removed?", "How long does a task stay open?", "Can I edit a task after posting?"),
            MarketFaqUiPolicy.questionsFor(MarketFaqCategory.POSTING).map { it.title }
        )
        assertEquals(
            listOf("How do I get my proposal accepted?"),
            MarketFaqUiPolicy.questionsFor(MarketFaqCategory.PROPOSALS).map { it.title }
        )
        assertEquals(
            listOf("How do I report someone?"),
            MarketFaqUiPolicy.questionsFor(MarketFaqCategory.SAFETY).map { it.title }
        )
        assertEquals(
            listOf("Does U2U handle payments?", "How do I appeal a decision?"),
            MarketFaqUiPolicy.questionsFor(MarketFaqCategory.ACCOUNT).map { it.title }
        )
    }

    @Test
    fun incomingQuestionTitleIsMatchedIgnoringCaseAndWhitespace() {
        assertEquals(
            MarketFaqUiPolicy.INITIAL_QUESTION_TITLE,
            MarketFaqUiPolicy.questionByTitle("  HOW DO I GET MY PROPOSAL ACCEPTED? ")?.title
        )
        assertEquals(
            MarketFaqCategory.SAFETY,
            MarketFaqUiPolicy.questionByTitle("How do I report someone?")?.category
        )
        assertNull(MarketFaqUiPolicy.questionByTitle(null))
        assertNull(MarketFaqUiPolicy.questionByTitle("Not a Market question"))
    }
}
