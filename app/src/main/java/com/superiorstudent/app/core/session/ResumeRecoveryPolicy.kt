package com.superiorstudent.app.core.session

/** Deterministic timing policy for lifecycle recovery. */
object ResumeRecoveryPolicy {
    const val UI_WATCHDOG_DELAY_MS = 3_000L
    const val SESSION_OPERATION_TIMEOUT_MS = 15_000L
    const val LONG_SESSION_REVALIDATION_MS = 30 * 60 * 1_000L

    fun timeInBackground(nowMs: Long, lastPausedAtMs: Long): Long {
        if (nowMs <= 0L || lastPausedAtMs <= 0L || nowMs < lastPausedAtMs) return 0L
        return nowMs - lastPausedAtMs
    }

    fun shouldRevalidateSession(timeInBackgroundMs: Long): Boolean =
        timeInBackgroundMs >= LONG_SESSION_REVALIDATION_MS
}
