package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConfigProviderTest {
    private var nowMillis = 1_800_000_000_000L
    private val preferences = FakePreferences()
    private val cacheStore = MarketConfigCacheStore(preferences) { nowMillis }

    @Test
    fun freshCacheIsReturnedWithoutCallingRemoteReader() {
        val config = sampleConfig()
        assertTrue(cacheStore.write(config))
        var remoteCalls = 0
        val provider = provider {
            remoteCalls += 1
            it(null, null)
        }

        val result = load(provider)
        assertEquals(config, result.config)
        assertNull(result.failure)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun missingCacheReadsRemoteAndPersistsValidResult() {
        val remote = sampleConfig()
        val provider = provider { complete -> complete(remote, null) }

        val result = load(provider)
        assertEquals(remote, result.config)
        assertNull(result.failure)
        assertEquals(remote, cacheStore.read()!!.config)
        assertEquals(nowMillis, cacheStore.read()!!.savedAtMillis)
    }

    @Test
    fun staleCacheTriggersRefreshAndUsesSuccessfulRemoteConfig() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val refreshed = sampleConfig().copy(
            announcement = MarketAnnouncement("Updated remotely", true, null)
        )
        val provider = provider { complete -> complete(refreshed, null) }

        val result = load(provider)
        assertEquals(refreshed, result.config)
        assertNull(result.failure)
        assertEquals(refreshed, cacheStore.read()!!.config)
        assertEquals(nowMillis, cacheStore.read()!!.savedAtMillis)
    }

    @Test
    fun staleCacheIsUsedOnlyForRecognizedConnectivityFailure() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val provider = provider { complete ->
            complete(null, UnknownHostException("offline"))
        }

        val result = load(provider)
        assertEquals(cached, result.config)
        assertNull(result.failure)
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun timeoutCanUseStaleCacheButAuthorizationFailureCannot() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L

        val timeoutProvider = provider { complete ->
            complete(null, TimeoutException("request timed out"))
        }
        assertEquals(cached, load(timeoutProvider).config)

        val authorizationFailure = IllegalStateException("permission denied")
        val deniedProvider = provider { complete ->
            complete(null, authorizationFailure)
        }
        val denied = load(deniedProvider)
        assertNull(denied.config)
        assertEquals(authorizationFailure, denied.failure)
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun successfulButInvalidRemoteConfigDoesNotFallBackToStaleCache() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val provider = provider { complete -> complete(null, null) }

        val result = load(provider)
        assertNull(result.config)
        assertNull(result.failure)
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun unknownFailuresAndNetworkFailuresWithoutCachePropagate() {
        val programmingError = IllegalStateException("Unexpected data processing failure.")
        val provider = provider { complete -> complete(null, programmingError) }

        val result = load(provider)
        assertNull(result.config)
        assertEquals(programmingError, result.failure)

        assertTrue(cacheStore.clear())
        val networkFailure = provider { complete ->
            complete(null, UnknownHostException("offline"))
        }
        val networkResult = load(networkFailure)
        assertNull(networkResult.config)
        assertTrue(networkResult.failure is UnknownHostException)
    }

    @Test
    fun connectivityClassifierOnlyAcceptsKnownFirestoreCodesAndNetworkExceptions() {
        assertTrue(MarketConfigFailurePolicy.isConnectivityFailureCodeName("UNAVAILABLE"))
        assertTrue(MarketConfigFailurePolicy.isConnectivityFailureCodeName("DEADLINE_EXCEEDED"))
        assertFalse(MarketConfigFailurePolicy.isConnectivityFailureCodeName("PERMISSION_DENIED"))
        assertFalse(MarketConfigFailurePolicy.isConnectivityFailureCodeName("INVALID_ARGUMENT"))
        assertTrue(MarketConfigFailurePolicy.isConnectivityFailure(UnknownHostException("offline")))
        assertFalse(MarketConfigFailurePolicy.isConnectivityFailure(IllegalArgumentException("bad data")))
    }

    private fun provider(
        remoteReader: ((MarketConfig?, Exception?) -> Unit) -> Unit
    ): MarketConfigProvider = MarketConfigProvider(remoteReader, cacheStore) { nowMillis }

    private fun load(provider: MarketConfigProvider): Outcome {
        var callbackCount = 0
        var config: MarketConfig? = null
        var failure: Exception? = null
        provider.load { value, error ->
            callbackCount += 1
            config = value
            failure = error
        }
        assertEquals("Provider callback should run exactly once for synchronous test reader.", 1, callbackCount)
        return Outcome(config, failure)
    }

    private data class Outcome(val config: MarketConfig?, val failure: Exception?)

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
