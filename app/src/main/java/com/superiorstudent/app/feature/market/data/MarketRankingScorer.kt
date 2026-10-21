package com.superiorstudent.app.feature.market.data

/**
 * Pure, on-device ranking for an already-loaded Market page.
 *
 * This scorer never reads Firestore and never changes visibility or authorization.
 * Callers should apply the Market visibility filter before invoking it. Configured
 * weights are supplied from the cached config; only the documented 60/30/10 defaults
 * are used when a caller explicitly chooses [DEFAULT_WEIGHTS].
 */
object MarketRankingScorer {
    val DEFAULT_WEIGHTS = MarketRankWeights(newness = 60.0, rating = 30.0, match = 10.0)

    data class OfferSignals(
        val offer: MarketOffer,
        val ratingSum: Double,
        val ratingCount: Int,
        val matchesSkillTags: Boolean = false,
        val matchesCourse: Boolean = false,
        val pinnedAtMillis: Long? = null
    )

    data class TaskSignals(
        val task: MarketTask,
        val matchesSkillTags: Boolean = false,
        val matchesCourse: Boolean = false
    )

    /**
     * Pinned offers remain first; then blended score descending; then renewal time
     * and creation time descending. Among otherwise ranked offers, greedily choose
     * the next different seller to avoid consecutive repeats where possible.
     */
    fun rankOffers(
        items: List<OfferSignals>,
        weights: MarketRankWeights,
        nowMillis: Long
    ): List<OfferSignals> {
        requireValidWeights(weights)
        if (items.size < 2) return items.toList()

        val ranked = items.withIndex().sortedWith(
            compareByDescending<IndexedValue<OfferSignals>> { it.value.offer.pinned }
                .thenByDescending {
                    if (it.value.offer.pinned) {
                        it.value.pinnedAtMillis ?: it.value.offer.renewedAt.toMillis()
                    } else {
                        Long.MIN_VALUE
                    }
                }
                .thenByDescending { score(it.value, weights, nowMillis) }
                .thenByDescending { it.value.offer.renewedAt.toMillis() }
                .thenByDescending { it.value.offer.createdAt.toMillis() }
                .thenBy { it.index }
        ).map { it.value }

        return avoidConsecutiveSellers(ranked)
    }

    /** Tasks are pinned first, then matching tasks, then newest. Expired tasks are omitted. */
    fun rankTasks(
        items: List<TaskSignals>,
        nowMillis: Long
    ): List<TaskSignals> =
        items.withIndex()
            .filter { (_, item) ->
                item.task.deadline.toMillis() > nowMillis &&
                    item.task.expiresAt.toMillis() > nowMillis
            }
            .sortedWith(
                compareByDescending<IndexedValue<TaskSignals>> { it.value.task.pinned }
                    .thenByDescending { it.value.matchesSkillTags || it.value.matchesCourse }
                    .thenByDescending { it.value.task.createdAt.toMillis() }
                    .thenBy { it.index }
            )
            .map { it.value }

    fun score(
        signals: OfferSignals,
        weights: MarketRankWeights,
        nowMillis: Long
    ): Double {
        requireValidWeights(weights)
        val renewedAtMillis = signals.offer.renewedAt.toMillis()
        val ageMillis = (nowMillis - renewedAtMillis).coerceAtLeast(0L)
        val ageDays = ageMillis.toDouble() / MILLIS_PER_DAY
        val newness = (1.0 - ageDays / OFFER_LIFETIME_DAYS).coerceIn(0.0, 1.0)

        val ratingCount = signals.ratingCount.coerceAtLeast(0)
        val ratingSum = signals.ratingSum.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val quality = (
            (PRIOR_RATING * PRIOR_RATING_COUNT + ratingSum) /
                (PRIOR_RATING_COUNT + ratingCount).toDouble() / 5.0
            ).coerceIn(0.0, 1.0)
        val match = if (signals.matchesSkillTags || signals.matchesCourse) 1.0 else 0.0

        return (
            weights.newness * newness +
                weights.rating * quality +
                weights.match * match
            ) / 100.0
    }

    private fun avoidConsecutiveSellers(ranked: List<OfferSignals>): List<OfferSignals> {
        if (ranked.size < 2) return ranked
        val remaining = ranked.toMutableList()
        val result = ArrayList<OfferSignals>(ranked.size)

        while (remaining.isNotEmpty()) {
            val previousSeller = result.lastOrNull()?.offer?.sellerId
            val nextIndex = if (previousSeller == null) {
                0
            } else {
                remaining.indexOfFirst { it.offer.sellerId != previousSeller }
                    .takeIf { it >= 0 } ?: 0
            }
            result += remaining.removeAt(nextIndex)
        }
        return result
    }

    private fun requireValidWeights(weights: MarketRankWeights) {
        require(
            weights.newness.isFinite() && weights.rating.isFinite() && weights.match.isFinite() &&
                weights.newness >= 0.0 && weights.rating >= 0.0 && weights.match >= 0.0 &&
                kotlin.math.abs(weights.newness + weights.rating + weights.match - 100.0) < WEIGHT_EPSILON
        ) { "Market ranking weights must be finite, non-negative, and sum to 100." }
    }

    private const val OFFER_LIFETIME_DAYS = 30.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val PRIOR_RATING = 4.2
    private const val PRIOR_RATING_COUNT = 5
    private const val WEIGHT_EPSILON = 0.000001
}
