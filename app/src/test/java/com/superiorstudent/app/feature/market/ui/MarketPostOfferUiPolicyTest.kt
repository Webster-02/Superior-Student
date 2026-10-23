package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPostOfferUiPolicyTest {
    private val referenceInput = MarketPostOfferInput(
        title = MarketPostOfferUiPolicy.REFERENCE_TITLE,
        category = MarketPostOfferUiPolicy.REFERENCE_CATEGORY,
        priceMode = MarketPostOfferPriceMode.RUPEES,
        amount = MarketPostOfferUiPolicy.REFERENCE_AMOUNT,
        description = MarketPostOfferUiPolicy.REFERENCE_DESCRIPTION
    )

    @Test
    fun referenceFormDefaultsAreConsistentAndValid() {
        assertTrue(MarketPostOfferUiPolicy.validate(referenceInput).isValid)
        assertEquals("Handwritten Ocular Anatomy notes", MarketPostOfferUiPolicy.REFERENCE_TITLE)
        assertEquals("65/400",
            MarketPostOfferUiPolicy.descriptionCounter(MarketPostOfferUiPolicy.REFERENCE_DESCRIPTION))
        assertEquals("Rs. 300", MarketPostOfferUiPolicy.priceSummary(
            MarketPostOfferPriceMode.RUPEES, "300"))
    }

    @Test
    fun titleAndCategoryValidationCoverMissingAndOutOfRangeValues() {
        assertEquals("Enter an offer title",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(title = " ")).titleError)
        assertEquals("Title must be at least 5 characters",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(title = "Book")).titleError)
        assertEquals("Title must be 80 characters or fewer",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(title = "x".repeat(81))).titleError)
        assertEquals("Choose a category",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(category = "Other")).categoryError)
        assertNull(MarketPostOfferUiPolicy.validate(referenceInput.copy(category = "Books")).categoryError)
    }

    @Test
    fun amountIsRequiredOnlyWhenRupeesAreSelected() {
        assertEquals("Enter an amount greater than Rs. 0",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(amount = "")).amountError)
        assertEquals("Enter an amount greater than Rs. 0",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(amount = "0")).amountError)
        assertNull(MarketPostOfferUiPolicy.validate(referenceInput.copy(
            priceMode = MarketPostOfferPriceMode.FREE, amount = "")).amountError)
        assertNull(MarketPostOfferUiPolicy.validate(referenceInput.copy(
            priceMode = MarketPostOfferPriceMode.SWAP, amount = "")).amountError)
        assertEquals("Free", MarketPostOfferUiPolicy.priceSummary(MarketPostOfferPriceMode.FREE, "300"))
        assertEquals("Swap", MarketPostOfferUiPolicy.priceSummary(MarketPostOfferPriceMode.SWAP, "300"))
    }

    @Test
    fun descriptionCounterAndValidationUseActualCharacterCounts() {
        assertEquals("3/400", MarketPostOfferUiPolicy.descriptionCounter("abc"))
        assertEquals("400/400", MarketPostOfferUiPolicy.descriptionCounter("x".repeat(405)))
        assertEquals("Add a short description",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(description = "")).descriptionError)
        assertEquals("Description must be at least 10 characters",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(description = "short")).descriptionError)
        assertTrue(MarketPostOfferUiPolicy.validate(
            referenceInput.copy(description = "good description")).isValid)
        assertFalse(MarketPostOfferUiPolicy.validate(
            referenceInput.copy(description = "x".repeat(401))).isValid)
        assertEquals("Description must be 400 characters or fewer",
            MarketPostOfferUiPolicy.validate(referenceInput.copy(description = "x".repeat(401))).descriptionError)
    }

    @Test
    fun coverCompressionStaysWithinTheReferenceMaximum() {
        assertEquals(102400, MarketPostOfferUiPolicy.MAX_COVER_BYTES)
        assertEquals("12.0 KB · ready to post",
            MarketPostOfferUiPolicy.compressedCoverLabel(12288))
        assertEquals("100.0 KB · ready to post",
            MarketPostOfferUiPolicy.compressedCoverLabel(200000))
    }
}
