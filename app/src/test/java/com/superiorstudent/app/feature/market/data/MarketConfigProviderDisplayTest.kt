package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConfigProviderDisplayTest {
    private var nowMillis = 1_800_000_000_000L
    private val preferences = FakePreferences()
    private val cacheStore = MarketConfigCacheStore(preferences) { nowMillis }

    @Test
    fun permissionFailurePreservesDisplayedAndPersistedConfigAndHidesRetry() {
        val persisted = sampleConfig()
        assertTrue(cacheStore.write(persisted))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val displayed = sampleConfig().copy(
            announcement = MarketAnnouncement("Already rendered", true, null)
        )
        val provider = provider { complete -> complete(null, SecurityException("internal details")) }

        val result = loadForDisplay(provider, displayed)

        assertEquals(displayed, result.config)
        assertEquals(MarketErrorKind.PERMISSION_DENIED, result.error?.kind)
        assertFalse(result.error!!.retryable)
        assertEquals(persisted, cacheStore.read()!!.config)
    }

    @Test
    fun offlineFailureWithoutCachePreservesDisplayedConfigAndAllowsRetry() {
        val displayed = sampleConfig()
        val provider = provider { complete ->
            complete(null, UnknownHostException("private host information"))
        }

        val result = loadForDisplay(provider, displayed)

        assertEquals(displayed, result.config)
        assertEquals(MarketErrorKind.OFFLINE, result.error?.kind)
        assertTrue(result.error!!.retryable)
        assertFalse(result.error.message.contains("private host information"))
    }

    @Test
    fun successfulRemoteConfigReplacesDisplayedConfigAndClearsError() {
        val displayed = sampleConfig()
        val remote = sampleConfig().copy(
            announcement = MarketAnnouncement("Fresh remote config", true, null)
        )
        val provider = provider { complete -> complete(remote, null) }

        val result = loadForDisplay(provider, displayed)

        assertEquals(remote, result.config)
        assertNull(result.error)
        assertEquals(remote, cacheStore.read()!!.config)
    }

    @Test
    fun missingOrInvalidRemoteConfigKeepsDisplayedConfigAndDoesNotOfferRetry() {
        val displayed = sampleConfig()
        val provider = provider { complete -> complete(null, null) }

        val result = loadForDisplay(provider, displayed)

        assertEquals(displayed, result.config)
        assertEquals(MarketErrorKind.INVALID_CONFIG, result.error?.kind)
        assertFalse(result.error!!.retryable)
    }

    @Test
    fun firstLoadOfflineReturnsRetryableErrorWithoutInventingConfig() {
        val provider = provider { complete ->
            complete(null, UnknownHostException("offline"))
        }

        val result = loadForDisplay(provider, null)

        assertNull(result.config)
        assertEquals(MarketErrorKind.OFFLINE, result.error?.kind)
        assertTrue(result.error!!.retryable)
    }

    private fun provider(
        remoteReader: ((MarketConfig?, Exception?) -> Unit) -> Unit
    ) = MarketConfigProvider(remoteReader, cacheStore) { nowMillis }

    private fun loadForDisplay(
        provider: MarketConfigProvider,
        currentlyDisplayed: MarketConfig?
    ): DisplayOutcome {
        var callbackCount = 0
        var config: MarketConfig? = null
        var error: MarketMappedError? = null
        provider.loadForDisplay(currentlyDisplayed) { loaded, mappedError ->
            callbackCount += 1
            config = loaded
            error = mappedError
        }
        assertEquals("Display callback should run exactly once for a synchronous test reader.", 1, callbackCount)
        return DisplayOutcome(config, error)
    }

    private data class DisplayOutcome(
        val config: MarketConfig?,
        val error: MarketMappedError?
    )

    private fun sampleConfig(): MarketConfig = MarketConfig(
        announcement = MarketAnnouncement("Market notice", true, Timestamp(1_800_000_000L, 123_000_000)),
        blockedWords = listOf("spam", "scam"),
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
        private val values = linkedMapOf<String, String>()
        override fun readString(key: String): String? = values[key]
        override fun writeString(key: String, value: String): Boolean {
            values[key] = value
            return true
        }
        override fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
