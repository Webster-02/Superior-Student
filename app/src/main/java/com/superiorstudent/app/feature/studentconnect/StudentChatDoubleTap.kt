package com.superiorstudent.app.feature.studentconnect

/** Small, deterministic double-tap window check for the chat message interaction. */
internal object StudentChatDoubleTap {
    fun occurred(
        previousTapAtMillis: Long,
        currentTapAtMillis: Long,
        timeoutMillis: Long
    ): Boolean = previousTapAtMillis > 0L &&
        currentTapAtMillis > previousTapAtMillis &&
        timeoutMillis > 0L &&
        currentTapAtMillis - previousTapAtMillis <= timeoutMillis
}
