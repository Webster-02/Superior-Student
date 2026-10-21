package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConfigCacheStoreTest {
    private var nowMillis = 1_800_000_000_000L
    private val preferences = FakePreferences()
    private val store = MarketConfigCacheStore(preferences) { nowMillis }

    @Test
    fun writesAndReadsConfigWithItsSavedAtTimestamp() {
        val config = sampleConfig()

        assertTrue(store.write(config))
        val saved = store.read()

        assertNotNull(saved)
        assertEquals(config, saved!!.config)
        assertEquals(nowMillis, saved.savedAtMillis)
    }

    @Test
    fun overwritesThePreviousEntryWithTheNewConfigAndTimestamp() {
        assertTrue(store.write(sampleConfig()))
        nowMillis += 5_000L
        val replacement = sampleConfig().copy(
            announcement = MarketAnnouncement("Updated announcement", false, null)
        )

        assertTrue(store.write(replacement))
        val saved = store.read()

        assertEquals(replacement, saved!!.config)
        assertEquals(nowMillis, saved.savedAtMillis)
    }

    @Test
    fun staleEntryRemainsReadableForPolicyControlledConnectivityFallback() {
        assertTrue(store.write(sampleConfig()))
        val savedAt = nowMillis
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L

        val saved = store.read()
        assertNotNull(saved)
        assertEquals(savedAt, saved!!.savedAtMillis)
        assertFalse(MarketConfigCachePolicy.isFresh(saved.savedAtMillis, nowMillis))
        assertTrue(MarketConfigCachePolicy.shouldFallbackToCachedConfig(
            hasCachedConfig = true,
            connectivityFailure = true
        ))
    }

    @Test
    fun clearRemovesOnlyTheMarketConfigEntry() {
        preferences.values["unrelated_preference"] = "keep"
        assertTrue(store.write(sampleConfig()))
        assertTrue(store.clear())

        assertNull(store.read())
        assertEquals(mapOf("unrelated_preference" to "keep"), preferences.values)
    }

    @Test
    fun invalidCachedPayloadIsRemovedOnRead() {
        assertTrue(store.write(sampleConfig()))
        val cacheKey = preferences.values.keys.single()
        preferences.values[cacheKey] = "{not valid json"

        assertNull(store.read())
        assertTrue(preferences.values.isEmpty())
    }

    @Test
    fun invalidConfigOrRejectedStorageWriteDoesNotReplaceExistingCache() {
        assertTrue(store.write(sampleConfig()))
        val oldPayload = preferences.values.values.single()
        val invalidConfig = sampleConfig().copy(
            rankWeights = MarketRankWeights(50.0, 30.0, 19.0)
        )

        assertFalse(store.write(invalidConfig))
        assertEquals(oldPayload, preferences.values.values.single())

        preferences.rejectWrites = true
        nowMillis += 1L
        assertFalse(store.write(sampleConfig()))
        assertEquals(oldPayload, preferences.values.values.single())
    }

    @Test
    fun handlesPreferenceFailuresWithoutPropagatingRuntimeExceptions() {
        preferences.failReads = true
        assertNull(store.read())

        preferences.failReads = false
        preferences.failRemoves = true
        assertFalse(store.clear())

        preferences.failRemoves = false
        preferences.failWrites = true
        assertFalse(store.write(sampleConfig()))
    }

    private fun sampleConfig(): MarketConfig = MarketConfig(
        announcement = MarketAnnouncement(
            text = "Market notice",
            active = true,
            until = Timestamp(1_800_000_000L, 456_000_000)
        ),
        blockedWords = listOf("scam", "spam"),
        rankWeights = MarketRankWeights(newness = 50.0, rating = 30.0, match = 20.0),
        limits = MarketLimits(
            openTasks = 3,
            proposalsPerDay = 10,
            proposalsPerTask = 15,
            activeOffers = 5,
            reportsToHide = 3
        )
    )

    private class FakePreferences : MarketConfigCachePreferences {
        val values = linkedMapOf<String, String>()
        var rejectWrites = false
        var failReads = false
        var failWrites = false
        var failRemoves = false

        override fun readString(key: String): String? {
            check(!failReads) { "Simulated preference read failure." }
            return values[key]
        }

        override fun writeString(key: String, value: String): Boolean {
            if (failWrites) error("Simulated preference write failure.")
            if (rejectWrites) return false
            values[key] = value
            return true
        }

        override fun remove(key: String): Boolean {
            check(!failRemoves) { "Simulated preference removal failure." }
            values.remove(key)
            return true
        }
    }
}
