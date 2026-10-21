package com.superiorstudent.app.feature.market.data

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketErrorMapperTest {
    @Test
    fun mapsAuthenticationAndAuthorizationSeparately() {
        val signIn = MarketErrorMapper.mapFirestoreCode("UNAUTHENTICATED")
        assertEquals(MarketErrorKind.SIGN_IN_REQUIRED, signIn.kind)
        assertTrue(signIn.requiresSignIn)
        assertFalse(signIn.retryable)

        val denied = MarketErrorMapper.mapFirestoreCode("PERMISSION_DENIED")
        assertEquals(MarketErrorKind.PERMISSION_DENIED, denied.kind)
        assertFalse(denied.requiresSignIn)
        assertFalse(denied.retryable)
    }

    @Test
    fun mapsConnectivityAndTimeoutCodesToRetryableOfflineState() {
        listOf("UNAVAILABLE", "DEADLINE_EXCEEDED").forEach { code ->
            val mapped = MarketErrorMapper.mapFirestoreCode(code)
            assertEquals(code, MarketErrorKind.OFFLINE, mapped.kind)
            assertTrue("$code should permit retry", mapped.retryable)
        }
    }

    @Test
    fun mapsItemAndDuplicateActionErrorsWithoutSuggestingBlindRetry() {
        val missing = MarketErrorMapper.mapFirestoreCode("NOT_FOUND")
        assertEquals(MarketErrorKind.ITEM_NOT_FOUND, missing.kind)
        assertFalse(missing.retryable)

        val duplicate = MarketErrorMapper.mapFirestoreCode("ALREADY_EXISTS")
        assertEquals(MarketErrorKind.DUPLICATE_ACTION, duplicate.kind)
        assertFalse(duplicate.retryable)
    }

    @Test
    fun mapsValidationAndPreconditionErrorsToSpecificGuidance() {
        listOf("INVALID_ARGUMENT", "OUT_OF_RANGE").forEach { code ->
            val mapped = MarketErrorMapper.mapFirestoreCode(code)
            assertEquals(code, MarketErrorKind.INVALID_INPUT, mapped.kind)
            assertFalse(mapped.retryable)
        }

        val precondition = MarketErrorMapper.mapFirestoreCode("FAILED_PRECONDITION")
        assertEquals(MarketErrorKind.PRECONDITION_FAILED, precondition.kind)
        assertFalse(precondition.retryable)
    }

    @Test
    fun mapsContentionLimitsAndServiceFailuresToRetryableGuidance() {
        mapOf(
            "ABORTED" to MarketErrorKind.CONFLICT,
            "RESOURCE_EXHAUSTED" to MarketErrorKind.TEMPORARY_LIMIT,
            "INTERNAL" to MarketErrorKind.TEMPORARY_FAILURE,
            "DATA_LOSS" to MarketErrorKind.TEMPORARY_FAILURE
        ).forEach { (code, expectedKind) ->
            val mapped = MarketErrorMapper.mapFirestoreCode(code)
            assertEquals(code, expectedKind, mapped.kind)
            assertTrue("$code should allow a user-requested retry", mapped.retryable)
        }
    }

    @Test
    fun mapsUnsupportedCancelledAndUnknownCodesSafely() {
        val unsupported = MarketErrorMapper.mapFirestoreCode("UNIMPLEMENTED")
        assertEquals(MarketErrorKind.UNSUPPORTED, unsupported.kind)
        assertFalse(unsupported.retryable)

        val cancelled = MarketErrorMapper.mapFirestoreCode("CANCELLED")
        assertEquals(MarketErrorKind.CANCELLED, cancelled.kind)
        assertTrue(cancelled.retryable)

        val unknown = MarketErrorMapper.mapFirestoreCode("A_FUTURE_CODE")
        assertEquals(MarketErrorKind.UNKNOWN, unknown.kind)
        assertTrue(unknown.retryable)
    }

    @Test
    fun detectsConnectivityWhenWrappedAndDoesNotExposeTechnicalExceptionText() {
        val failure = IllegalStateException(
            "internal endpoint host secret.example",
            SocketTimeoutException("socket read timed out")
        )

        val mapped = MarketErrorMapper.map(failure)
        assertEquals(MarketErrorKind.OFFLINE, mapped.kind)
        assertTrue(mapped.retryable)
        assertFalse(mapped.message.contains("secret.example"))
        assertFalse(mapped.message.contains("socket"))
    }

    @Test
    fun mapsDirectNetworkCancellationSecurityAndInputExceptions() {
        assertEquals(
            MarketErrorKind.OFFLINE,
            MarketErrorMapper.map(UnknownHostException("private-host")).kind
        )
        assertEquals(
            MarketErrorKind.CANCELLED,
            MarketErrorMapper.map(CancellationException("cancelled")).kind
        )
        assertEquals(
            MarketErrorKind.PERMISSION_DENIED,
            MarketErrorMapper.map(SecurityException("details")).kind
        )
        assertEquals(
            MarketErrorKind.INVALID_INPUT,
            MarketErrorMapper.map(IllegalArgumentException("details")).kind
        )
    }

    @Test
    fun genericFailuresUseSafeRetryGuidanceAndNeverEchoRawMessages() {
        val mapped = MarketErrorMapper.map(IllegalStateException("database-password-or-path"))
        assertEquals(MarketErrorKind.UNKNOWN, mapped.kind)
        assertTrue(mapped.retryable)
        assertFalse(mapped.message.contains("database-password-or-path"))
        assertTrue(mapped.message.contains("saved data"))
    }
}
