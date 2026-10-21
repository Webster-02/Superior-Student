package com.superiorstudent.app.feature.market.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
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
            Tasks.forResult<MarketConfig?>(null)
        }

        assertEquals(config, await(provider.load()))
        assertEquals(0, remoteCalls)
    }

    @Test
    fun missingCacheReadsRemoteAndPersistsValidResult() {
        val remote = sampleConfig()
        val provider = provider { Tasks.forResult<MarketConfig?>(remote) }

        assertEquals(remote, await(provider.load()))
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
        val provider = provider { Tasks.forResult<MarketConfig?>(refreshed) }

        assertEquals(refreshed, await(provider.load()))
        assertEquals(refreshed, cacheStore.read()!!.config)
        assertEquals(nowMillis, cacheStore.read()!!.savedAtMillis)
    }

    @Test
    fun staleCacheIsUsedOnlyForRecognizedFirestoreConnectivityFailure() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val unavailable = firestoreFailure(FirebaseFirestoreException.Code.UNAVAILABLE)
        val provider = provider { Tasks.forException<MarketConfig?>(unavailable) }

        assertEquals(cached, await(provider.load()))
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun deadlineExceededCanUseStaleCacheButPermissionDeniedCannot() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L

        val timeoutProvider = provider {
            Tasks.forException<MarketConfig?>(
                firestoreFailure(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
            )
        }
        assertEquals(cached, await(timeoutProvider.load()))

        val denied = firestoreFailure(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val deniedProvider = provider { Tasks.forException<MarketConfig?>(denied) }
        val thrown = expectFailure(deniedProvider.load())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (thrown as FirebaseFirestoreException).code)
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun successfulButInvalidRemoteConfigDoesNotFallBackToStaleCache() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val provider = provider { Tasks.forResult<MarketConfig?>(null) }

        assertNull(await(provider.load()))
        assertEquals(cached, cacheStore.read()!!.config)
    }

    @Test
    fun unknownFailuresAndNetworkFailuresWithoutCachePropagate() {
        val cached = sampleConfig()
        assertTrue(cacheStore.write(cached))
        nowMillis += MarketConfigCachePolicy.CACHE_TTL_MILLIS + 1L
        val programmingError = IllegalStateException("Unexpected data processing failure.")
        val thrown = expectFailure(provider {
            Tasks.forException<MarketConfig?>(programmingError)
        }.load())
        assertEquals(programmingError, thrown)

        assertTrue(cacheStore.clear())
        val networkFailure = provider {
            Tasks.forException<MarketConfig?>(UnknownHostException("offline"))
        }
        assertTrue(expectFailure(networkFailure.load()) is UnknownHostException)
    }

    @Test
    fun connectivityClassifierDoesNotTreatPermissionOrGenericErrorsAsNetworkFailures() {
        assertTrue(MarketConfigFailurePolicy.isConnectivityFailure(
            firestoreFailure(FirebaseFirestoreException.Code.UNAVAILABLE)
        ))
        assertTrue(MarketConfigFailurePolicy.isConnectivityFailure(UnknownHostException("offline")))
        assertFalse(MarketConfigFailurePolicy.isConnectivityFailure(
            firestoreFailure(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        ))
        assertFalse(MarketConfigFailurePolicy.isConnectivityFailure(IllegalArgumentException("bad data")))
    }

    private fun provider(remoteReader: () -> Task<MarketConfig?>): MarketConfigProvider =
        MarketConfigProvider(remoteReader, cacheStore) { nowMillis }

    private fun await(task: Task<MarketConfig?>): MarketConfig? =
        Tasks.await(task, 5, TimeUnit.SECONDS)

    private fun expectFailure(task: Task<MarketConfig?>): Exception {
        try {
            await(task)
            throw AssertionError("Expected task to fail.")
        } catch (failure: ExecutionException) {
            return failure.cause as? Exception
                ?: throw AssertionError("Expected an Exception cause.", failure)
        }
    }

    private fun firestoreFailure(code: FirebaseFirestoreException.Code): FirebaseFirestoreException =
        FirebaseFirestoreException("Test Firestore failure", code, null)

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
