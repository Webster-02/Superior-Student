package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentChatReactionEmoji
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentChatReactionEmojiTest {
    @Test
    fun everySupportedReactionIsAllowed() {
        StudentChatReactionEmoji.supported.forEach {
            assertTrue("Expected supported reaction: $it", StudentChatReactionEmoji.isValid(it))
        }
    }

    @Test
    fun unsupportedOrArbitraryValuesAreRejected() {
        assertFalse(StudentChatReactionEmoji.isValid(""))
        assertFalse(StudentChatReactionEmoji.isValid("hello"))
        assertFalse(StudentChatReactionEmoji.isValid("🔥"))
        assertFalse(StudentChatReactionEmoji.isValid("👍👍"))
    }
}
