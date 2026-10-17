package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentChatReplyMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentChatReplyMetadataTest {
    @Test
    fun plainMessageWithoutReplyMetadataRemainsValid() {
        assertTrue(StudentChatReplyMetadata.isValid(null, null, null))
    }

    @Test
    fun completeReplyMetadataIsValid() {
        assertTrue(StudentChatReplyMetadata.isValid("message-123", "Ali Khan", "See you in class"))
    }

    @Test
    fun partialReplyMetadataIsRejected() {
        assertFalse(StudentChatReplyMetadata.isValid("message-123", null, "See you in class"))
        assertFalse(StudentChatReplyMetadata.isValid(null, "Ali Khan", "See you in class"))
        assertFalse(StudentChatReplyMetadata.isValid("message-123", "Ali Khan", null))
    }

    @Test
    fun invalidReplyMetadataIsRejected() {
        assertFalse(StudentChatReplyMetadata.isValid("", "Ali Khan", "Text"))
        assertFalse(StudentChatReplyMetadata.isValid("message-123", "A", "Text"))
        assertFalse(StudentChatReplyMetadata.isValid("message-123", "Ali Khan", "   "))
    }
}
