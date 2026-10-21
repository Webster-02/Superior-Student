package com.superiorstudent.app.feature.market.data

import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/**
 * Maps technical Market/Firestore failures to safe, user-facing guidance.
 *
 * This class is deliberately side-effect-free: it never retries, logs sensitive
 * exception text, changes cache contents, or bypasses Firestore security rules.
 * Callers should keep any already-rendered cached data visible while presenting
 * the message and exposing Retry only when [MarketMappedError.retryable] is true.
 */
internal object MarketErrorMapper {
    fun map(failure: Throwable): MarketMappedError {
        val visited = HashSet<Throwable>()
        var current: Throwable? = failure

        while (current != null && visited.add(current)) {
            when (current) {
                is FirebaseFirestoreException -> {
                    val mapped = mapFirestoreCode(current.code.name)
                    if (mapped.kind != MarketErrorKind.UNKNOWN) return mapped
                }
                is UnknownHostException,
                is ConnectException,
                is NoRouteToHostException,
                is SocketException,
                is SocketTimeoutException,
                is TimeoutException -> return offlineError()
                is CancellationException,
                is InterruptedException -> return MarketMappedError(
                    kind = MarketErrorKind.CANCELLED,
                    message = "The request was cancelled. You can try again.",
                    retryable = true
                )
                is SecurityException -> return MarketMappedError(
                    kind = MarketErrorKind.PERMISSION_DENIED,
                    message = "Market could not allow that action with the current account permissions.",
                    retryable = false
                )
                is IllegalArgumentException -> return invalidInputError()
            }
            current = current.cause
        }

        return MarketMappedError(
            kind = MarketErrorKind.UNKNOWN,
            message = "Something went wrong in Market. Your saved data is still available; try again.",
            retryable = true
        )
    }

    /** A successful read with absent or invalid config is a data error, not an offline fallback. */
    internal fun invalidRemoteConfig(): MarketMappedError = MarketMappedError(
        kind = MarketErrorKind.INVALID_CONFIG,
        message = "Market settings could not be loaded because their configuration is missing or invalid. Existing content remains visible.",
        retryable = false
    )

    /** Separate string-based mapper so every Firestore code can be tested without Android services. */
    internal fun mapFirestoreCode(codeName: String?): MarketMappedError = when (codeName) {
        "UNAUTHENTICATED" -> MarketMappedError(
            kind = MarketErrorKind.SIGN_IN_REQUIRED,
            message = "Please sign in again before using Market.",
            retryable = false,
            requiresSignIn = true
        )
        "PERMISSION_DENIED" -> MarketMappedError(
            kind = MarketErrorKind.PERMISSION_DENIED,
            message = "Market could not allow that action with the current account permissions.",
            retryable = false
        )
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> offlineError()
        "CANCELLED" -> MarketMappedError(
            kind = MarketErrorKind.CANCELLED,
            message = "The request was cancelled. You can try again.",
            retryable = true
        )
        "ABORTED" -> MarketMappedError(
            kind = MarketErrorKind.CONFLICT,
            message = "Market data changed while you were acting. Refresh and try again.",
            retryable = true
        )
        "RESOURCE_EXHAUSTED" -> MarketMappedError(
            kind = MarketErrorKind.TEMPORARY_LIMIT,
            message = "Market is temporarily limiting requests. Wait a moment before trying again.",
            retryable = true
        )
        "FAILED_PRECONDITION" -> MarketMappedError(
            kind = MarketErrorKind.PRECONDITION_FAILED,
            message = "This action is not available in the item's current state. Refresh Market and check its status.",
            retryable = false
        )
        "NOT_FOUND" -> MarketMappedError(
            kind = MarketErrorKind.ITEM_NOT_FOUND,
            message = "This item is no longer available. Refresh Market.",
            retryable = false
        )
        "ALREADY_EXISTS" -> MarketMappedError(
            kind = MarketErrorKind.DUPLICATE_ACTION,
            message = "This action already exists or has already been completed.",
            retryable = false
        )
        "INVALID_ARGUMENT", "OUT_OF_RANGE" -> invalidInputError()
        "UNIMPLEMENTED" -> MarketMappedError(
            kind = MarketErrorKind.UNSUPPORTED,
            message = "This Market action is not available yet.",
            retryable = false
        )
        "INTERNAL" -> MarketMappedError(
            kind = MarketErrorKind.TEMPORARY_FAILURE,
            message = "Market had a temporary problem. Your saved data is still available; try again.",
            retryable = true
        )
        "DATA_LOSS" -> MarketMappedError(
            kind = MarketErrorKind.TEMPORARY_FAILURE,
            message = "Market could not safely complete that request. Refresh and try again.",
            retryable = true
        )
        else -> MarketMappedError(
            kind = MarketErrorKind.UNKNOWN,
            message = "Something went wrong in Market. Your saved data is still available; try again.",
            retryable = true
        )
    }

    private fun offlineError() = MarketMappedError(
        kind = MarketErrorKind.OFFLINE,
        message = "Market could not connect. Check your connection and try again; saved data remains available.",
        retryable = true
    )

    private fun invalidInputError() = MarketMappedError(
        kind = MarketErrorKind.INVALID_INPUT,
        message = "Please review the information and try again.",
        retryable = false
    )
}

internal enum class MarketErrorKind {
    OFFLINE,
    INVALID_CONFIG,
    SIGN_IN_REQUIRED,
    PERMISSION_DENIED,
    ITEM_NOT_FOUND,
    DUPLICATE_ACTION,
    INVALID_INPUT,
    PRECONDITION_FAILED,
    CONFLICT,
    TEMPORARY_LIMIT,
    TEMPORARY_FAILURE,
    UNSUPPORTED,
    CANCELLED,
    UNKNOWN
}

internal data class MarketMappedError(
    val kind: MarketErrorKind,
    val message: String,
    val retryable: Boolean,
    val requiresSignIn: Boolean = false
)
