package com.superiorstudent.app.feature.studentconnect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentChatDoubleTapTest {
    @Test
    fun recognizesTwoTapsWithinTheDoubleTapWindow() {
        assertTrue(StudentChatDoubleTap.occurred(1_000L, 1_250L, 300L))
        assertTrue(StudentChatDoubleTap.occurred(1_000L, 1_300L, 300L))
    }

    @Test
    fun rejectsSingleExpiredOrOutOfOrderTaps() {
        assertFalse(StudentChatDoubleTap.occurred(0L, 1_100L, 300L))
        assertFalse(StudentChatDoubleTap.occurred(1_000L, 1_301L, 300L))
        assertFalse(StudentChatDoubleTap.occurred(1_200L, 1_100L, 300L))
        assertFalse(StudentChatDoubleTap.occurred(1_000L, 1_100L, 0L))
    }
}
