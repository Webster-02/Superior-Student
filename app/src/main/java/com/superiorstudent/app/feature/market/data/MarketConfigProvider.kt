package com.superiorstudent.app.feature.market.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
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
    private val remoteReader: ((MarketConfig?, Exception?) -> Unit) -> Unit,
    private val cacheStore: MarketConfigCacheStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    constructor(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ) : this(
        remoteReader = { onComplete ->
            MarketConfigReader(firestore).read().addOnCompleteListener(DIRECT_EXECUTOR) { completed ->
                if (completed.isSuccessful) {
                    onComplete(completed.result, null)
                } else {
                    onComplete(
                        null,
                        completed.exception
                            ?: IllegalStateException("Market config read was cancelled or failed without an exception.")
                    )
                }
            }
        },
        cacheStore = MarketConfigCacheStore(context.applicationContext),
        nowMillis = System::currentTimeMillis
    )

    /**
     * UI-facing config load path. The caller supplies the config currently rendered
     * on screen; failures keep that value in place and provide a safe mapped error.
     * The UI should show Retry only when [MarketMappedError.retryable] is true.
     */
    internal fun loadForDisplay(
        currentlyDisplayedConfig: MarketConfig? = null,
        onComplete: (MarketConfig?, MarketMappedError?) -> Unit
    ) {
        load { loadedConfig, failure ->
            when {
                failure != null -> onComplete(currentlyDisplayedConfig, MarketErrorMapper.map(failure))
                loadedConfig != null -> onComplete(loadedConfig, null)
                else -> onComplete(currentlyDisplayedConfig, MarketErrorMapper.invalidRemoteConfig())
            }
        }
    }

    /** Production entry point. The callback-based core keeps policy tests JVM-only. */
    fun load(): Task<MarketConfig?> {
        val source = TaskCompletionSource<MarketConfig?>()
        load { config, failure ->
            if (failure == null) {
                source.trySetResult(config)
            } else {
                source.trySetException(failure)
            }
        }
        return source.task
    }

    /** Internal decision path for deterministic tests; does not depend on Google Task internals. */
    internal fun load(onComplete: (MarketConfig?, Exception?) -> Unit) {
        val cached = cacheStore.read()
        val now = try {
            nowMillis()
        } catch (failure: Exception) {
            onComplete(null, failure)
            return
        }

        if (cached != null && MarketConfigCachePolicy.isFresh(cached.savedAtMillis, now)) {
            onComplete(cached.config, null)
            return
        }

        val finishRemote: (MarketConfig?, Exception?) -> Unit = { remoteConfig, failure ->
            if (failure == null) {
                // A null config indicates a missing or invalid remote document.
                // Treat that as data failure, not a connectivity failure.
                if (remoteConfig != null) cacheStore.write(remoteConfig)
                onComplete(remoteConfig, null)
            } else {
                cachedFallbackOrFailure(cached, failure, onComplete)
            }
        }

        try {
            remoteReader(finishRemote)
        } catch (failure: Exception) {
            cachedFallbackOrFailure(cached, failure, onComplete)
        }
    }

    private fun cachedFallbackOrFailure(
        cached: MarketConfigCacheEntry?,
        failure: Exception,
        onComplete: (MarketConfig?, Exception?) -> Unit
    ) {
        val connectivityFailure = MarketConfigFailurePolicy.isConnectivityFailure(failure)
        if (MarketConfigCachePolicy.shouldFallbackToCachedConfig(
                hasCachedConfig = cached != null,
                connectivityFailure = connectivityFailure
            )
        ) {
            onComplete(cached!!.config, null)
        } else {
            onComplete(null, failure)
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
            if (current is FirebaseFirestoreException &&
                isConnectivityFailureCodeName(current.code.name)
            ) return true

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

    /** Kept separate so the exact Firestore code mapping is testable on a plain JVM. */
    internal fun isConnectivityFailureCodeName(codeName: String?): Boolean =
        codeName == "UNAVAILABLE" || codeName == "DEADLINE_EXCEEDED"
}
