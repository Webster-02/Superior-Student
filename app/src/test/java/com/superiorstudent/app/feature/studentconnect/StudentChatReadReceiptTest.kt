package com.superiorstudent.app.feature.studentconnect

import com.google.firebase.Timestamp
import com.superiorstudent.app.feature.studentconnect.data.StudentChatReadReceipt
import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentChatReadReceiptTest {
    @Test
    fun messageIsNotReadWithoutMessageTimestampOrRecipientReadMarker() {
        val messageAt = Timestamp(Date(2_000L))
        val readAt = Timestamp(Date(3_000L))
        assertFalse(StudentChatReadReceipt.isRead(null, readAt))
        assertFalse(StudentChatReadReceipt.isRead(messageAt, null))
        assertFalse(StudentChatReadReceipt.isRead(null, null))
    }

    @Test
    fun olderRecipientReadMarkerLeavesMessageSent() {
        assertFalse(StudentChatReadReceipt.isRead(Timestamp(Date(3_000L)), Timestamp(Date(2_000L))))
    }

    @Test
    fun messageIsReadWhenRecipientMarkerReachesOrPassesMessageTimestamp() {
        val messageAt = Timestamp(Date(3_000L))
        assertTrue(StudentChatReadReceipt.isRead(messageAt, Timestamp(Date(3_000L))))
        assertTrue(StudentChatReadReceipt.isRead(messageAt, Timestamp(Date(4_000L))))
    }
}
