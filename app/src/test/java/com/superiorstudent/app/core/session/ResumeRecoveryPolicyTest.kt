package com.superiorstudent.app.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRecoveryPolicyTest {
    @Test
    fun timeInBackgroundIsZeroWithoutReliableTimestamps() {
        assertEquals(0L, ResumeRecoveryPolicy.timeInBackground(10_000L, 0L))
        assertEquals(0L, ResumeRecoveryPolicy.timeInBackground(0L, 1_000L))
        assertEquals(0L, ResumeRecoveryPolicy.timeInBackground(500L, 1_000L))
    }

    @Test
    fun timeInBackgroundUsesElapsedWallClockTime() {
        assertEquals(600_000L, ResumeRecoveryPolicy.timeInBackground(700_000L, 100_000L))
    }

    @Test
    fun longSessionRevalidationThresholdIsThirtyMinutes() {
        assertFalse(ResumeRecoveryPolicy.shouldRevalidateSession(10 * 60 * 1_000L))
        assertFalse(ResumeRecoveryPolicy.shouldRevalidateSession(ResumeRecoveryPolicy.LONG_SESSION_REVALIDATION_MS - 1))
        assertTrue(ResumeRecoveryPolicy.shouldRevalidateSession(ResumeRecoveryPolicy.LONG_SESSION_REVALIDATION_MS))
        assertTrue(ResumeRecoveryPolicy.shouldRevalidateSession(60 * 60 * 1_000L))
    }
}
