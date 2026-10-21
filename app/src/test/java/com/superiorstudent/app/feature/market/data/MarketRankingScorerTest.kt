package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRankingScorerTest {
    private val now = 1_800_000_000_000L
    private val weights = MarketRankWeights(newness = 60.0, rating = 30.0, match = 10.0)

    @Test
    fun scoreUsesConfiguredWeightsAndColdSellerRatingPrior() {
        val newOffer = offer("a", "seller-a", renewedAt = now)
        val signals = MarketRankingScorer.OfferSignals(newOffer, ratingSum = 0.0, ratingCount = 0)

        // Newness = 1, quality prior = 4.2 / 5 = 0.84, match = 0.
        val expected = (60.0 * 1.0 + 30.0 * 0.84) / 100.0
        assertEquals(expected, MarketRankingScorer.score(signals, weights, now), 0.000001)

        val matching = signals.copy(matchesCourse = true)
        assertEquals(expected + 0.10, MarketRankingScorer.score(matching, weights, now), 0.000001)
    }

    @Test
    fun newnessFallsToZeroAtThirtyDaysAndNeverGoesNegative() {
        val thirtyDaysAgo = now - 30L * DAY
        val old = MarketRankingScorer.OfferSignals(
            offer("old", "seller-old", renewedAt = thirtyDaysAgo),
            ratingSum = 0.0,
            ratingCount = 0
        )
        val older = old.copy(offer = old.offer.copy(renewedAt = Timestamp((now - 45L * DAY) / 1000L, 0)))

        assertEquals(0.252, MarketRankingScorer.score(old, weights, now), 0.000001)
        assertEquals(0.252, MarketRankingScorer.score(older, weights, now), 0.000001)
    }

    @Test
    fun offersStayPinnedFirstThenRankByScoreAndTime() {
        val high = signals("high", "seller-high", renewedAt = now, matched = true)
        val low = signals("low", "seller-low", renewedAt = now - 2L * DAY, matched = false)
        val pinnedOld = signals("pinned", "seller-pinned", renewedAt = now - 20L * DAY, pinned = true)
        val result = MarketRankingScorer.rankOffers(listOf(low, high, pinnedOld), weights, now)

        assertEquals("pinned", result.first().offer.id)
        assertEquals("high", result[1].offer.id)
        assertEquals("low", result[2].offer.id)
    }

    @Test
    fun pinnedOffersUsePinTimeWhenAvailable() {
        val pinEarlier = signals("pin-a", "seller-a", renewedAt = now, pinned = true, pinnedAt = now - 1000L)
        val pinLater = signals("pin-b", "seller-b", renewedAt = now - 5L * DAY, pinned = true, pinnedAt = now)
        val result = MarketRankingScorer.rankOffers(listOf(pinEarlier, pinLater), weights, now)

        assertEquals(listOf("pin-b", "pin-a"), result.map { it.offer.id })
    }

    @Test
    fun sellerRepeatIsMovedBehindNextAvailableDifferentSeller() {
        val a1 = signals("a1", "A", renewedAt = now)
        val a2 = signals("a2", "A", renewedAt = now - 1_000L)
        val b = signals("b", "B", renewedAt = now - 2_000L)

        val result = MarketRankingScorer.rankOffers(listOf(a1, a2, b), weights, now)
        assertEquals(listOf("a1", "b", "a2"), result.map { it.offer.id })
        assertFalse(result.zipWithNext().any { (first, second) ->
            first.offer.sellerId == second.offer.sellerId
        })
    }

    @Test
    fun sellerRepeatConstraintIsBestEffortWhenOnlyOneSellerExists() {
        val result = MarketRankingScorer.rankOffers(
            listOf(
                signals("a1", "A", renewedAt = now),
                signals("a2", "A", renewedAt = now - 1_000L)
            ),
            weights,
            now
        )
        assertEquals(listOf("a1", "a2"), result.map { it.offer.id })
    }

    @Test
    fun taskRankingPutsPinnedThenMatchingThenNewestAndExcludesExpired() {
        val expired = taskSignal("expired", createdAt = now, deadline = now - 1L)
        val newUnmatched = taskSignal("new", createdAt = now, matches = false)
        val olderMatched = taskSignal("matched", createdAt = now - DAY, matches = true)
        val pinned = taskSignal("pinned", createdAt = now - 5L * DAY, pinned = true)

        val result = MarketRankingScorer.rankTasks(
            listOf(newUnmatched, expired, olderMatched, pinned),
            now
        )

        assertEquals(listOf("pinned", "matched", "new"), result.map { it.task.id })
    }

    @Test
    fun invalidWeightsAreRejected() {
        val item = signals("offer", "seller", renewedAt = now)
        listOf(
            MarketRankWeights(-1.0, 101.0, 0.0),
            MarketRankWeights(Double.NaN, 50.0, 50.0),
            MarketRankWeights(30.0, 30.0, 30.0)
        ).forEach { invalid ->
            var rejected = false
            try {
                MarketRankingScorer.score(item, invalid, now)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Invalid weights should be rejected: $invalid", rejected)
        }
    }

    private fun signals(
        id: String,
        seller: String,
        renewedAt: Long,
        pinned: Boolean = false,
        pinnedAt: Long? = null,
        matched: Boolean = false
    ) = MarketRankingScorer.OfferSignals(
        offer = offer(id, seller, renewedAt, pinned),
        ratingSum = 0.0,
        ratingCount = 0,
        matchesSkillTags = matched,
        pinnedAtMillis = pinnedAt
    )

    private fun offer(id: String, seller: String, renewedAt: Long, pinned: Boolean = false) =
        MarketOffer(
            id = id,
            title = "Offer $id",
            category = "study",
            courseTag = null,
            description = "A sufficiently detailed test offer description.",
            priceType = MarketPriceType.FREE,
            priceAmount = null,
            thumbnail = null,
            sellerId = seller,
            sellerName = seller,
            sellerInitials = seller.take(1),
            status = MarketOfferStatus.ACTIVE,
            moderation = MarketModerationStatus.OK,
            pinned = pinned,
            reportCount = 0,
            createdAt = Timestamp(now / 1000L, 0),
            renewedAt = Timestamp(renewedAt / 1000L, 0),
            updatedAt = Timestamp(now / 1000L, 0),
            expiresAt = Timestamp((renewedAt + 30L * DAY) / 1000L, 0)
        )

    private fun taskSignal(
        id: String,
        createdAt: Long,
        deadline: Long = now + 10L * DAY,
        pinned: Boolean = false,
        matches: Boolean = false
    ) = MarketRankingScorer.TaskSignals(
        task = MarketTask(
            id = id,
            title = "Task $id",
            category = "study",
            courseTag = null,
            description = "A sufficiently detailed task description for ranking.",
            budgetType = MarketBudgetType.FREE,
            budgetAmount = null,
            deadline = Timestamp(deadline / 1000L, 0),
            skills = emptyList(),
            posterId = "poster",
            posterName = "Poster",
            posterInitials = "P",
            status = MarketTaskStatus.OPEN,
            moderation = MarketModerationStatus.OK,
            pinned = pinned,
            proposalCount = 0,
            reportCount = 0,
            createdAt = Timestamp(createdAt / 1000L, 0),
            updatedAt = Timestamp(now / 1000L, 0),
            expiresAt = Timestamp((now + 14L * DAY) / 1000L, 0)
        ),
        matchesSkillTags = matches
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
