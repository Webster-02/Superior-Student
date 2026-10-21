package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPostTaskStep3UiPolicyTest {
    private val valid = MarketPostTaskStep3Input(
        title = "Poster for Optometry Society health camp",
        category = "Design",
        course = "BSOPM · Ocular Anatomy",
        description = "Need an A3 poster for our health camp on 28 Oct. Two colour options please. Text and logo will be shared on chat after selection.",
        budgetType = "Fixed",
        amount = "1,500",
        deadlineIso = "2026-10-24",
        todayIso = "2026-10-21",
        skills = listOf("Canva", "Photoshop", "Layout"),
        rulesAcknowledged = true
    )

    @Test
    fun validReferenceDraftPassesAllThreeStepValidation() {
        assertTrue(MarketPostTaskStep3UiPolicy.validate(valid).isValid)
    }

    @Test
    fun postingRulesAcknowledgementIsRequired() {
        val result = MarketPostTaskStep3UiPolicy.validate(valid.copy(rulesAcknowledged = false))
        assertFalse(result.isValid)
        assertEquals("Read and accept the posting rules to continue.", result.acknowledgementError)
        assertNull(MarketPostTaskStep3UiPolicy.validate(valid).acknowledgementError)
    }

    @Test
    fun existingDetailsAndBudgetRulesAreReappliedAtReview() {
        assertEquals("Enter a task title", MarketPostTaskStep3UiPolicy.validate(valid.copy(title = "")).titleError)
        assertEquals("Choose a category", MarketPostTaskStep3UiPolicy.validate(valid.copy(category = "Other")).categoryError)
        assertEquals("Choose a budget option.", MarketPostTaskStep3UiPolicy.validate(valid.copy(budgetType = "")).budgetError)
        assertEquals("Enter a whole amount greater than zero.", MarketPostTaskStep3UiPolicy.validate(valid.copy(amount = "0")).amountError)
        assertEquals("Deadline cannot be in the past.", MarketPostTaskStep3UiPolicy.validate(valid.copy(deadlineIso = "2026-10-20")).deadlineError)
        assertEquals("Choose no more than five skills.", MarketPostTaskStep3UiPolicy.validate(valid.copy(skills = listOf("Canva", "Photoshop", "Layout", "Illustrator", "Figma", "Canva"))).skillsError)
    }

    @Test
    fun previewUsesInputAndFormatsCourseBudgetAndDeadline() {
        val preview = MarketPostTaskStep3UiPolicy.preview(valid)
        assertEquals("Design", preview.categoryLabel)
        assertEquals("BSOPM", preview.courseLabel)
        assertEquals(valid.title, preview.title)
        assertEquals(valid.description, preview.description)
        assertEquals("Rs. 1,500", preview.budgetLabel)
        assertEquals("Due in 3 days", preview.deadlineLabel)
        assertEquals("0 proposals", preview.proposalsLabel)
        assertEquals("MA", preview.posterInitials)
    }

    @Test
    fun budgetOptionsProduceHonestLabels() {
        assertEquals("Free help", MarketPostTaskStep3UiPolicy.budgetLabel("Free help", "1,500"))
        assertEquals("Negotiable", MarketPostTaskStep3UiPolicy.budgetLabel("Negotiable", ""))
        assertEquals("Negotiable · Rs. 2,000", MarketPostTaskStep3UiPolicy.budgetLabel("Negotiable", "2000"))
    }

    @Test
    fun deadlineLabelsHandleTodayAndTomorrow() {
        assertEquals("Due today", MarketPostTaskStep3UiPolicy.deadlineLabel("2026-10-21", "2026-10-21"))
        assertEquals("Due tomorrow", MarketPostTaskStep3UiPolicy.deadlineLabel("2026-10-21", "2026-10-22"))
    }
}
