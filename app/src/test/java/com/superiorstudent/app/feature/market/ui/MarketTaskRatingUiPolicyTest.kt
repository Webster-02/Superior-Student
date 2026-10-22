package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTaskRatingUiPolicyTest {
    @Test
    fun fixtureMatchesCompletedTaskRatingReference() {
        assertEquals("Zain Malik", MarketTaskRatingUiPolicy.DEFAULT_HELPER_NAME)
        assertEquals("Poster for health camp", MarketTaskRatingUiPolicy.DEFAULT_TASK_TITLE)
        assertEquals(4, MarketTaskRatingUiPolicy.DEFAULT_RATING)
        assertEquals("Delivered a day early and made the changes quickly.",
            MarketTaskRatingUiPolicy.DEFAULT_COMMENT)
        assertEquals(51, MarketTaskRatingUiPolicy.DEFAULT_COMMENT.length)
        assertEquals("51/200",
            MarketTaskRatingUiPolicy.commentCounter(MarketTaskRatingUiPolicy.DEFAULT_COMMENT))
    }

    @Test
    fun ratingIsKeptWithinOneThroughFiveStars() {
        assertEquals(1, MarketTaskRatingUiPolicy.clampRating(-2))
        assertEquals(1, MarketTaskRatingUiPolicy.clampRating(1))
        assertEquals(4, MarketTaskRatingUiPolicy.clampRating(4))
        assertEquals(5, MarketTaskRatingUiPolicy.clampRating(7))
    }

    @Test
    fun referenceTagsAndInitialSelectionMatchScreenshot() {
        assertEquals(
            listOf("On time", "Clear work", "Polite", "Great quality", "Easy to work with"),
            MarketTaskRatingUiPolicy.tags
        )
        assertEquals(linkedSetOf("On time", "Clear work", "Polite"),
            MarketTaskRatingUiPolicy.defaultSelectedTags)
    }

    @Test
    fun togglingKnownTagsAddsAndRemovesWithoutDuplicates() {
        val start = MarketTaskRatingUiPolicy.defaultSelectedTags
        val withQuality = MarketTaskRatingUiPolicy.toggleTag(start, "Great quality")
        assertTrue("Great quality" in withQuality)
        assertEquals(4, withQuality.size)

        val withoutPolite = MarketTaskRatingUiPolicy.toggleTag(withQuality, "Polite")
        assertFalse("Polite" in withoutPolite)
        assertFalse("Great quality" in start)
        assertEquals(3, start.size)
        assertSame(start, MarketTaskRatingUiPolicy.toggleTag(start, "Unknown"))
    }

    @Test
    fun optionalCommentAcceptsEmptyAndUpToTwoHundredCharactersOnly() {
        assertEquals("", MarketTaskRatingUiPolicy.normaliseComment("   "))
        assertEquals("Helpful", MarketTaskRatingUiPolicy.normaliseComment(" Helpful "))
        assertEquals(200,
            MarketTaskRatingUiPolicy.normaliseComment("x".repeat(200))?.length)
        assertEquals(null, MarketTaskRatingUiPolicy.normaliseComment("x".repeat(201)))
        assertEquals("0/200", MarketTaskRatingUiPolicy.commentCounter(""))
    }
}
