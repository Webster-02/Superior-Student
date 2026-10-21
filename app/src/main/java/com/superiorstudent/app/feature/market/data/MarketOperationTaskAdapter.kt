package com.superiorstudent.app.feature.market.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import java.util.concurrent.Executor

/**
 * Converts one opted-in Market operation into a result value with safe mapped
 * failures. It never retries or changes Firestore/cache state.
 */
internal object MarketOperationTaskAdapter {
    private val DIRECT_EXECUTOR: Executor = Executor { command -> command.run() }

    fun <T : Any> run(operation: () -> Task<T>): Task<MarketOperationResult<T>> {
        val destination = TaskCompletionSource<MarketOperationResult<T>>()
        val operationTask = try {
            operation()
        } catch (failure: Exception) {
            destination.trySetResult(
                MarketOperationResult.failure(mapSynchronousFailure(failure))
            )
            return destination.task
        }

        operationTask.addOnCompleteListener(DIRECT_EXECUTOR) { completed ->
            if (completed.isSuccessful) {
                val value = completed.result
                if (value != null) {
                    destination.trySetResult(MarketOperationResult.success(value))
                } else {
                    destination.trySetResult(
                        MarketOperationResult.failure(
                            MarketMappedError(
                                kind = MarketErrorKind.UNKNOWN,
                                message = "Market could not confirm that the task was created. Refresh before trying again.",
                                retryable = true
                            )
                        )
                    )
                }
            } else {
                val failure = completed.exception
                    ?: IllegalStateException("Market task creation failed without an exception.")
                destination.trySetResult(
                    MarketOperationResult.failure(MarketErrorMapper.map(failure))
                )
            }
        }
        return destination.task
    }

    /**
     * Synchronous IllegalStateException failures from createTask represent an
     * account/profile/state precondition, not a transport failure. Avoid
     * exposing the exception text or offering a blind network retry.
     */
    private fun mapSynchronousFailure(failure: Exception): MarketMappedError =
        if (failure is IllegalStateException) {
            MarketMappedError(
                kind = MarketErrorKind.PRECONDITION_FAILED,
                message = "Market cannot create this task in the current account or profile state. Check sign-in, email verification, and your profile.",
                retryable = false,
                requiresSignIn = false
            )
        } else {
            MarketErrorMapper.map(failure)
        }
}

/** A successful operation has a value; a failed operation has a mapped error. */
internal data class MarketOperationResult<T : Any> private constructor(
    val value: T?,
    val error: MarketMappedError?
) {
    val isSuccess: Boolean
        get() = value != null && error == null

    companion object {
        fun <T : Any> success(value: T): MarketOperationResult<T> =
            MarketOperationResult(value = value, error = null)

        fun <T : Any> failure(error: MarketMappedError): MarketOperationResult<T> =
            MarketOperationResult(value = null, error = error)
    }
}
