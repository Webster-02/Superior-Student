package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketMenuItemsTest {
    @Test
    fun menuRowsMatchReferenceOrderAndLabels() {
        assertEquals(
            listOf("My tasks", "My offers", "Notices", "Help and safety", "Market settings"),
            MarketMenuItems.all.map { it.label }
        )
    }

    @Test
    fun noticesShowsReferenceUnreadCountOfTwo() {
        val notices = MarketMenuItems.all.single { it.label == "Notices" }
        assertEquals(2, notices.badge)
        assertEquals("Activity and messages from U2U team", notices.subtitle)
    }

    @Test
    fun eachReferenceMenuRowHasSupportingDescriptionAndActionMessage() {
        assertTrue(MarketMenuItems.all.all { it.subtitle.isNotBlank() && it.feedbackMessage.isNotBlank() })
        assertEquals("Review your posted tasks and applications.",
            MarketMenuItems.all.first { it.label == "My tasks" }.feedbackMessage)
    }
}
