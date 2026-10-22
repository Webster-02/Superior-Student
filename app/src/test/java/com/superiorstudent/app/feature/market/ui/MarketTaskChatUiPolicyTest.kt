package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTaskChatUiPolicyTest {
    @Test
    fun referenceChatMatchesTheScreenFixture() {
        assertEquals("Zain Malik", MarketTaskChatUiPolicy.HELPER_NAME)
        assertEquals("Poster for health camp", MarketTaskChatUiPolicy.TASK_TITLE)
        assertEquals("Rs. 1,200 · Due 24 Oct", MarketTaskChatUiPolicy.taskBudgetLine(1200))
        assertEquals("Assigned", MarketTaskChatUiPolicy.STATUS_LABEL)
        assertEquals("Proposal accepted · Today", MarketTaskChatUiPolicy.EVENT_LABEL)
        assertEquals(3, MarketTaskChatUiPolicy.referenceMessages.size)
        assertEquals("4:10 PM", MarketTaskChatUiPolicy.referenceMessages[0].timeLabel)
        assertFalse(MarketTaskChatUiPolicy.referenceMessages[0].fromMe)
        assertTrue(MarketTaskChatUiPolicy.referenceMessages[1].fromMe)
    }

    @Test
    fun initialsUseAtMostTwoNameParts() {
        assertEquals("ZM", MarketTaskChatUiPolicy.initials("Zain Malik"))
        assertEquals("AK", MarketTaskChatUiPolicy.initials("Ayesha Khan"))
        assertEquals("S", MarketTaskChatUiPolicy.initials("Sara"))
        assertEquals("ST", MarketTaskChatUiPolicy.initials("   "))
    }

    @Test
    fun messageInputIsTrimmedAndBounded() {
        assertEquals("Hello there", MarketTaskChatUiPolicy.normaliseMessage("  Hello there  "))
        assertEquals(null, MarketTaskChatUiPolicy.normaliseMessage("   "))
        assertEquals(
            null,
            MarketTaskChatUiPolicy.normaliseMessage("x".repeat(MarketTaskChatUiPolicy.MAX_MESSAGE_LENGTH + 1))
        )
        assertEquals(
            MarketTaskChatUiPolicy.MAX_MESSAGE_LENGTH,
            MarketTaskChatUiPolicy.normaliseMessage("x".repeat(MarketTaskChatUiPolicy.MAX_MESSAGE_LENGTH))?.length
        )
    }

    @Test
    fun previewMessageIsAppendedWithoutMutatingTheStarterList() {
        val original = MarketTaskChatUiPolicy.referenceMessages
        val updated = MarketTaskChatUiPolicy.appendPreviewMessage(original, "  Please see the file  ", "Now")
        assertEquals(4, updated.size)
        assertEquals("Please see the file", updated.last().body)
        assertEquals("Now", updated.last().timeLabel)
        assertTrue(updated.last().fromMe)
        assertSame(original, MarketTaskChatUiPolicy.appendPreviewMessage(original, "   "))
        assertEquals(3, original.size)
    }

    @Test
    fun offerPreviewTranscriptIsSeparateFromAcceptedPosterConversation() {
        val messages = MarketTaskChatUiPolicy.offerPreviewMessages
        assertEquals(3, messages.size)
        assertEquals("Hi! Thanks for checking out my offer.", messages[0].body)
        assertEquals("Could I see a sample page first?", messages[1].body)
        assertEquals("Of course. I can share a sample before you decide.", messages[2].body)
        assertFalse(messages[0].fromMe)
        assertTrue(messages[1].fromMe)
        assertFalse(messages[2].fromMe)
        assertEquals("offer", MarketTaskChatUiPolicy.CHAT_CONTEXT_OFFER)
    }

    @Test
    fun amountFormattingNeverShowsNegativeMoney() {
        assertEquals("Rs. 0", MarketTaskChatUiPolicy.priceLabel(-1200))
        assertEquals("Rs. 2,500", MarketTaskChatUiPolicy.priceLabel(2500))
    }
}
