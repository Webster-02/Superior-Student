package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSupportUiPolicyTest {
    @Test
    fun referenceHasExpectedHeadingAndIssueCategories() {
        assertEquals("Contact support", MarketSupportUiPolicy.TITLE)
        assertEquals("We reply in Notices within 2 days", MarketSupportUiPolicy.SUBTITLE)
        assertEquals(
            listOf("Listing problem", "Account", "App bug", "Report a person", "Other"),
            MarketSupportUiPolicy.categories.map { it.label }
        )
        assertTrue(MarketSupportUiPolicy.hasReferenceContent())
    }

    @Test
    fun defaultFormCopyAndCharacterLimitMatchReference() {
        assertEquals("Cannot rate a completed task", MarketSupportUiPolicy.INITIAL_SUBJECT)
        assertTrue(MarketSupportUiPolicy.INITIAL_DETAILS.contains("rate button does not show"))
        assertEquals(500, MarketSupportUiPolicy.MAX_DETAILS_CHARS)
        assertEquals(
            MarketSupportUiPolicy.INITIAL_DETAILS.length.toString() + "/500",
            MarketSupportUiPolicy.counterFor(MarketSupportUiPolicy.INITIAL_DETAILS.length)
        )
        assertEquals("0/500", MarketSupportUiPolicy.counterFor(-2))
        assertEquals("500/500", MarketSupportUiPolicy.counterFor(800))
    }

    @Test
    fun formValidationRequiresNonBlankSubjectAndDetails() {
        assertTrue(MarketSupportUiPolicy.canSubmit("Problem", "More detail"))
        assertFalse(MarketSupportUiPolicy.canSubmit("", "More detail"))
        assertFalse(MarketSupportUiPolicy.canSubmit("Problem", "   "))
        assertFalse(MarketSupportUiPolicy.canSubmit("  ", "  "))
    }
}
