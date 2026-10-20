package com.superiorstudent.app.core.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientSessionFailureTrackerTest {
    @Test
    fun recentErpFailureSuppressesOnlyTheImmediateLoginRedirect() {
        val tracker = TransientSessionFailureTracker()
        tracker.record("https://erp.superior.edu.pk/student/dashboard", 1_000L)

        assertTrue(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/web/login?redirect=%2Fstudent%2Fdashboard",
                1_500L
            )
        )
        assertFalse(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/web/login",
                1_600L
            )
        )
    }

    @Test
    fun expiredFailureMarkerDoesNotSuppressARealSessionRejection() {
        val tracker = TransientSessionFailureTracker()
        tracker.record("https://erp.superior.edu.pk/student/dashboard", 1_000L)

        assertFalse(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/web/login",
                16_001L
            )
        )
    }

    @Test
    fun unrelatedHostFailureCannotSuppressErpLoginRedirect() {
        val tracker = TransientSessionFailureTracker()
        tracker.record("https://example.com/web/login", 1_000L)

        assertFalse(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/web/login",
                1_200L
            )
        )
    }

    @Test
    fun nonLoginPageDoesNotConsumeTheGuard() {
        val tracker = TransientSessionFailureTracker()
        tracker.record("https://erp.superior.edu.pk/student/dashboard", 1_000L)

        assertFalse(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/student/dashboard",
                1_200L
            )
        )
        assertTrue(
            tracker.shouldDeferLoginRedirect(
                "https://erp.superior.edu.pk/tsg/login",
                1_300L
            )
        )
    }
}
