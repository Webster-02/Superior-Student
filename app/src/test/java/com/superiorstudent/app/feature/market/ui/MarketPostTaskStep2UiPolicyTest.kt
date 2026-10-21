package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPostTaskStep2UiPolicyTest {
    private val validInput = MarketPostTaskStep2Input(
        budgetType = "Fixed",
        amount = "1,500",
        deadlineIso = "2026-10-24",
        todayIso = "2026-10-21",
        skills = listOf("Canva", "Photoshop", "Layout")
    )

    @Test
    fun referenceBudgetDateAndSkillsPassValidation() {
        assertTrue(MarketPostTaskStep2UiPolicy.validate(validInput).isValid)
    }

    @Test
    fun budgetChoiceIsRequiredAndFixedAmountMustBePositive() {
        assertEquals(
            "Choose a budget option.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(budgetType = "")).budgetError
        )
        assertEquals(
            "Enter an amount.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(amount = "")).amountError
        )
        assertEquals(
            "Enter a whole amount greater than zero.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(amount = "0")).amountError
        )
        assertTrue(MarketPostTaskStep2UiPolicy.validate(validInput.copy(amount = "1,500")).isValid)
    }

    @Test
    fun freeHelpAndNegotiableMayOmitAmount() {
        assertTrue(
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(budgetType = "Free help", amount = "")).isValid
        )
        assertTrue(
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(budgetType = "Negotiable", amount = "")).isValid
        )
    }

    @Test
    fun deadlineMustBeAnIsoDateNotEarlierThanToday() {
        assertEquals(
            "Deadline cannot be in the past.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(deadlineIso = "2026-10-20")).deadlineError
        )
        assertEquals(
            "Choose a valid deadline.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(deadlineIso = "")).deadlineError
        )
        assertTrue(
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(deadlineIso = validInput.todayIso)).isValid
        )
    }

    @Test
    fun skillsMustBeUniqueKnownAndNoMoreThanFive() {
        assertTrue(MarketPostTaskStep2UiPolicy.validate(validInput.copy(skills = MarketPostTaskStep2UiPolicy.availableSkills)).isValid)
        assertEquals(
            "Each skill can only be selected once.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(skills = listOf("Canva", "Canva"))).skillsError
        )
        assertEquals(
            "Choose no more than five skills.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(skills = listOf("Canva", "Photoshop", "Layout", "Illustrator", "Figma", "Other"))).skillsError
        )
    }

    @Test
    fun unknownBudgetAndUnknownSkillAreRejected() {
        assertFalse(MarketPostTaskStep2UiPolicy.validate(validInput.copy(budgetType = "Custom")).isValid)
        assertEquals(
            "Choose from the available skills.",
            MarketPostTaskStep2UiPolicy.validate(validInput.copy(skills = listOf("Canva", "Other"))).skillsError
        )
    }
}
