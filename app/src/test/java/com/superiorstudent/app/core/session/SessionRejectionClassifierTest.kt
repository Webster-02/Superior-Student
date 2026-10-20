package com.superiorstudent.app.core.session

import com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRejectionClassifierTest {
    @Test
    fun rejectsOnlyClearAuthenticationStatusCodes() {
        assertEquals(Decision.REJECTED, SessionRejectionClassifier.classify(statusCode = 401))
        assertEquals(Decision.REJECTED, SessionRejectionClassifier.classify(statusCode = 403))
    }

    @Test
    fun serverAndTimeoutFailuresAreTransientEvenIfTheUrlLooksLikeLogin() {
        assertEquals(
            Decision.TRANSIENT,
            SessionRejectionClassifier.classify(statusCode = 503, finalUrl = "https://erp.superior.edu.pk/web/login")
        )
        assertEquals(Decision.TRANSIENT, SessionRejectionClassifier.classify(statusCode = 408))
        assertEquals(Decision.TRANSIENT, SessionRejectionClassifier.classify(statusCode = 429))
    }

    @Test
    fun explicitLoginRedirectOrInvalidSessionResponseIsRejected() {
        assertEquals(
            Decision.REJECTED,
            SessionRejectionClassifier.classify(finalUrl = "https://erp.superior.edu.pk/web/login?redirect=%2Fstudent%2Fdashboard")
        )
        assertEquals(
            Decision.REJECTED,
            SessionRejectionClassifier.classify(statusCode = 200, responseBody = "Your session expired. Please log in again.")
        )
    }

    @Test
    fun ordinaryOrMissingResponseIsNotTreatedAsRejection() {
        assertEquals(Decision.UNKNOWN, SessionRejectionClassifier.classify(statusCode = 200, finalUrl = "https://erp.superior.edu.pk/student/dashboard"))
        assertEquals(Decision.UNKNOWN, SessionRejectionClassifier.classify())
        assertEquals(Decision.UNKNOWN, SessionRejectionClassifier.classify(statusCode = 404))
    }
}
