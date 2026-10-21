package com.superiorstudent.app.feature.market.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException

/**
 * Loads Market config through the six-hour cache policy.
 *
 * A fresh entry avoids a network read. Missing/stale entries trigger a remote
 * read; successful, valid remote data refreshes the cache. Stale cache is used
 * only if the remote failure is specifically recognized as connectivity-related.
 * A successful read that returns invalid/missing config does not use fallback.
 */
class MarketConfigProvider internal constructor(
    private val remoteReader: () -> Task<MarketConfig?>,
    private val cacheStore: MarketConfigCacheStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    constructor(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ) : this(
        MarketConfigReader(firestore)::read,
        MarketConfigCacheStore(context.applicationContext),
        System::currentTimeMillis
    )

    fun load(): Task<MarketConfig?> {
        val cached = cacheStore.read()
        val now = try {
            nowMillis()
        } catch (failure: Exception) {
            return Tasks.forException(failure)
        }

        if (cached != null && MarketConfigCachePolicy.isFresh(cached.savedAtMillis, now)) {
            return Tasks.forResult(cached.config)
        }

        val remoteTask = try {
            remoteReader()
        } catch (failure: Exception) {
            return cachedFallbackOrFailure(cached, failure)
        }

        return remoteTask.continueWithTask(DIRECT_EXECUTOR) { completed ->
            if (completed.isSuccessful) {
                // A null config indicates a missing or invalid remote document.
                // Treat that as data failure, not a connectivity failure.
                val remoteConfig = completed.result
                if (remoteConfig != null) cacheStore.write(remoteConfig)
                Tasks.forResult(remoteConfig)
            } else {
                val failure = completed.exception
                    ?: IllegalStateException("Market config read was cancelled or failed without an exception.")
                cachedFallbackOrFailure(cached, failure)
            }
        }
    }

    private fun cachedFallbackOrFailure(
        cached: MarketConfigCacheEntry?,
        failure: Exception
    ): Task<MarketConfig?> {
        val connectivityFailure = MarketConfigFailurePolicy.isConnectivityFailure(failure)
        return if (MarketConfigCachePolicy.shouldFallbackToCachedConfig(
                hasCachedConfig = cached != null,
                connectivityFailure = connectivityFailure
            )
        ) {
            Tasks.forResult(cached!!.config)
        } else {
            Tasks.forException(failure)
        }
    }
    private companion object {
        val DIRECT_EXECUTOR: Executor = Executor { command -> command.run() }
    }
}

/** Conservative classification: only recognized network and Firestore timeout codes qualify. */
internal object MarketConfigFailurePolicy {
    fun isConnectivityFailure(failure: Throwable): Boolean {
        val visited = HashSet<Throwable>()
        var current: Throwable? = failure

        while (current != null && visited.add(current)) {
            if (current is FirebaseFirestoreException) {
                when (current.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> return true
                    else -> Unit
                }
            }

            if (current is UnknownHostException ||
                current is ConnectException ||
                current is NoRouteToHostException ||
                current is SocketException ||
                current is SocketTimeoutException ||
                current is TimeoutException
            ) return true

            current = current.cause
        }
        return false
    }
}
