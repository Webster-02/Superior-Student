package com.superiorstudent.app.feature.studentconnect.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StudentChatReadReceiptStatusTest {
    @Test
    fun messageIsNotReadWhenRecipientHasNoReadMarker() {
        assertFalse(StudentChatReadReceiptStatus.isRead(Timestamp(Date(10_000L)), null))
    }

    @Test
    fun messageIsNotReadWhenItIsNewerThanRecipientReadMarker() {
        assertFalse(
            StudentChatReadReceiptStatus.isRead(
                messageCreatedAt = Timestamp(Date(20_000L)),
                lastReadAt = Timestamp(Date(19_000L))
            )
        )
    }

    @Test
    fun messageIsReadWhenRecipientReadMarkerIsAtOrAfterMessageTime() {
        assertTrue(
            StudentChatReadReceiptStatus.isRead(
                messageCreatedAt = Timestamp(Date(20_000L)),
                lastReadAt = Timestamp(Date(21_000L))
            )
        )
        assertTrue(
            StudentChatReadReceiptStatus.isRead(
                messageCreatedAt = Timestamp(Date(20_000L)),
                lastReadAt = Timestamp(Date(20_000L))
            )
        )
    }

    @Test
    fun pendingMessageWithoutServerTimestampIsNotRead() {
        assertFalse(StudentChatReadReceiptStatus.isRead(null, Timestamp(Date(20_000L))))
    }
}
