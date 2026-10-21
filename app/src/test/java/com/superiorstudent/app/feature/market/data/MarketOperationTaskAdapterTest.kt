package com.superiorstudent.app.feature.market.data

import com.google.android.gms.tasks.Tasks
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketOperationTaskAdapterTest {
    @Test
    fun successfulOperationReturnsValueWithoutError() {
        val mappedTask = MarketOperationTaskAdapter.run {
            Tasks.forResult("created-task-123")
        }

        assertTrue(mappedTask.isSuccessful)
        assertTrue(mappedTask.result.isSuccess)
        assertEquals("created-task-123", mappedTask.result.value)
        assertNull(mappedTask.result.error)
    }

    @Test
    fun asynchronousPermissionFailureReturnsSafeNonRetryableError() {
        val mappedTask = MarketOperationTaskAdapter.run<String> {
            Tasks.forException(SecurityException("private permission diagnostics"))
        }

        assertTrue(mappedTask.isSuccessful)
        val result = mappedTask.result
        assertNull(result.value)
        assertEquals(MarketErrorKind.PERMISSION_DENIED, result.error?.kind)
        assertFalse(result.error!!.retryable)
        assertFalse(result.error.message.contains("private permission diagnostics"))
    }

    @Test
    fun asynchronousOfflineFailureReturnsRetryableError() {
        val mappedTask = MarketOperationTaskAdapter.run<String> {
            Tasks.forException(UnknownHostException("private-host"))
        }

        val error = mappedTask.result.error
        assertEquals(MarketErrorKind.OFFLINE, error?.kind)
        assertTrue(error!!.retryable)
        assertFalse(error.message.contains("private-host"))
    }

    @Test
    fun synchronousInputFailureBecomesMappedResult() {
        val mappedTask = MarketOperationTaskAdapter.run<String> {
            throw IllegalArgumentException("raw input details")
        }

        val result = mappedTask.result
        assertNull(result.value)
        assertEquals(MarketErrorKind.INVALID_INPUT, result.error?.kind)
        assertFalse(result.error!!.retryable)
        assertFalse(result.error.message.contains("raw input details"))
    }

    @Test
    fun synchronousStateFailureDoesNotSuggestBlindRetry() {
        val mappedTask = MarketOperationTaskAdapter.run<String> {
            throw IllegalStateException("sensitive account/profile details")
        }

        val error = mappedTask.result.error
        assertEquals(MarketErrorKind.PRECONDITION_FAILED, error?.kind)
        assertFalse(error!!.retryable)
        assertFalse(error.message.contains("sensitive account/profile details"))
    }
}
