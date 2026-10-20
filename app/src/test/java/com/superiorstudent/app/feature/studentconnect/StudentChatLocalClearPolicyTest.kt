package com.superiorstudent.app.feature.studentconnect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentChatLocalClearPolicyTest {

    @Test
    fun noClearBoundaryPreservesExistingMessagesIncludingUnknownTimestamp() {
        assertTrue(StudentChatLocalClearPolicy.isMessageVisible(1_000L, null))
        assertTrue(StudentChatLocalClearPolicy.isMessageVisible(null, null))
    }

    @Test
    fun clearBoundaryHidesOlderAndBoundaryMessagesButKeepsNewerMessages() {
        assertFalse(StudentChatLocalClearPolicy.isMessageVisible(999L, 1_000L))
        assertFalse(StudentChatLocalClearPolicy.isMessageVisible(1_000L, 1_000L))
        assertTrue(StudentChatLocalClearPolicy.isMessageVisible(1_001L, 1_000L))
    }

    @Test
    fun missingTimestampDoesNotResurfaceAnOldMessageAfterClear() {
        assertFalse(StudentChatLocalClearPolicy.isMessageVisible(null, 1_000L))
    }

    @Test
    fun recentChatPreviewIsHiddenUntilANewerMessageArrives() {
        assertFalse(StudentChatLocalClearPolicy.isSummaryHidden(1_000L, null))
        assertTrue(StudentChatLocalClearPolicy.isSummaryHidden(999L, 1_000L))
        assertTrue(StudentChatLocalClearPolicy.isSummaryHidden(1_000L, 1_000L))
        assertFalse(StudentChatLocalClearPolicy.isSummaryHidden(1_001L, 1_000L))
        assertTrue(StudentChatLocalClearPolicy.isSummaryHidden(null, 1_000L))
    }
}
