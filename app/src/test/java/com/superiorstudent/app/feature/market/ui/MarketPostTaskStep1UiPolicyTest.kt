package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPostTaskStep1UiPolicyTest {
    private val referenceInput = MarketPostTaskStep1Input(
        title = "Poster for Optometry Society health camp",
        category = "Design",
        course = "BSOPM · Ocular Anatomy",
        description = "Need an A3 poster for our health camp on 28 Oct. Two colour options please. Text and logo will be shared on chat after selection."
    )

    @Test
    fun suppliedReferenceDetailsPassValidation() {
        assertTrue(MarketPostTaskStep1UiPolicy.validate(referenceInput).isValid)
    }

    @Test
    fun titleIsRequiredAndMustHaveAtLeastFiveCharacters() {
        assertEquals(
            "Enter a task title",
            MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(title = " ")).titleError
        )
        assertEquals(
            "Title must be at least 5 characters",
            MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(title = "Help")).titleError
        )
        assertEquals(
            "Title must be 80 characters or fewer",
            MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(title = "x".repeat(81))).titleError
        )
    }

    @Test
    fun categoryMustBeOneOfReferenceOptions() {
        assertEquals(null, MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(category = "Typing")).categoryError)
        assertEquals("Choose a category", MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(category = "")).categoryError)
    }

    @Test
    fun descriptionMustBeTwentyToFiveHundredCharacters() {
        assertEquals(
            "Description must be at least 20 characters",
            MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(description = "Too short")).descriptionError
        )
        assertEquals(
            "Description must be 500 characters or fewer",
            MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(description = "x".repeat(501))).descriptionError
        )
    }

    @Test
    fun optionalCourseCanBeEmptyAndCounterShowsActualContentLength() {
        assertTrue(MarketPostTaskStep1UiPolicy.validate(referenceInput.copy(course = "")).isValid)
        assertEquals("3/500", MarketPostTaskStep1UiPolicy.descriptionCounter("abc"))
        assertFalse(MarketPostTaskStep1UiPolicy.descriptionCounter("x".repeat(505)) == "505/500")
    }
}
