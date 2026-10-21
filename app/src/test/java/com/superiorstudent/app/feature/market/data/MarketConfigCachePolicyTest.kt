package com.superiorstudent.app.feature.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConfigCachePolicyTest {
    private val savedAt = 1_800_000_000_000L

    @Test
    fun cacheIsFreshJustBeforeSixHourBoundary() {
        assertTrue(
            MarketConfigCachePolicy.isFresh(
                savedAtMillis = savedAt,
                nowMillis = savedAt + MarketConfigCachePolicy.CACHE_TTL_MILLIS - 1L
            )
        )
    }

    @Test
    fun cacheIsStaleAtExactlySixHours() {
        assertFalse(
            MarketConfigCachePolicy.isFresh(
                savedAtMillis = savedAt,
                nowMillis = savedAt + MarketConfigCachePolicy.CACHE_TTL_MILLIS
            )
        )
    }

    @Test
    fun cacheIsStaleWhenOlderThanSixHours() {
        assertFalse(
            MarketConfigCachePolicy.isFresh(
                savedAtMillis = savedAt,
                nowMillis = savedAt + MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
            )
        )
    }

    @Test
    fun invalidOrFutureCacheTimestampsAreNeverFresh() {
        assertFalse(MarketConfigCachePolicy.isFresh(savedAtMillis = 0L, nowMillis = savedAt))
        assertFalse(MarketConfigCachePolicy.isFresh(savedAtMillis = -1L, nowMillis = savedAt))
        assertFalse(MarketConfigCachePolicy.isFresh(savedAtMillis = savedAt + 1L, nowMillis = savedAt))
    }

    @Test
    fun staleCacheFallbackRequiresBothCachedValueAndConnectivityFailure() {
        assertTrue(MarketConfigCachePolicy.shouldFallbackToCachedConfig(
            hasCachedConfig = true,
            connectivityFailure = true
        ))
        assertFalse(MarketConfigCachePolicy.shouldFallbackToCachedConfig(
            hasCachedConfig = false,
            connectivityFailure = true
        ))
        assertFalse(MarketConfigCachePolicy.shouldFallbackToCachedConfig(
            hasCachedConfig = true,
            connectivityFailure = false
        ))
        assertFalse(MarketConfigCachePolicy.shouldFallbackToCachedConfig(
            hasCachedConfig = false,
            connectivityFailure = false
        ))
    }

    @Test
    fun cacheTtlIsExactlySixHours() {
        assertEquals(21_600_000L, MarketConfigCachePolicy.CACHE_TTL_MILLIS)
    }
}
