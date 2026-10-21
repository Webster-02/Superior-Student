package com.superiorstudent.app.feature.market.data

/**
 * Pure policy for the Market config cache.
 *
 * Step 4A intentionally contains no persistence or Firestore calls so cache-age
 * and fallback rules can be tested without an Android runtime or network.
 */
object MarketConfigCachePolicy {
    const val CACHE_TTL_MILLIS: Long = 6L * 60L * 60L * 1000L

    /**
     * A cache entry is fresh for less than six hours.
     * A missing/invalid timestamp or a phone clock earlier than the saved time
     * is treated as stale so the caller attempts a remote refresh.
     */
    fun isFresh(savedAtMillis: Long, nowMillis: Long): Boolean =
        savedAtMillis > 0L &&
            nowMillis >= savedAtMillis &&
            nowMillis - savedAtMillis < CACHE_TTL_MILLIS

    /**
     * Allow stale-cache fallback only for connectivity failures. Callers should
     * not hide authorization, invalid-data, or programming errors behind cache.
     */
    fun shouldFallbackToCachedConfig(
        hasCachedConfig: Boolean,
        connectivityFailure: Boolean
    ): Boolean = hasCachedConfig && connectivityFailure
}
