package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketMyTasksUiPolicyTest {
    @Test
    fun defaultReferenceTabIsAppliedAndLabelsMatchScreenshot() {
        assertEquals("Posted 2", MarketMyTasksUiPolicy.POSTED_TAB_LABEL)
        assertEquals("Applied 3", MarketMyTasksUiPolicy.APPLIED_TAB_LABEL)
        assertEquals(2, MarketMyTasksUiPolicy.items(MarketMyTasksTab.POSTED).size)
        assertEquals(4, MarketMyTasksUiPolicy.items(MarketMyTasksTab.APPLIED).size)
        assertEquals(MarketMyTasksUiPolicy.APPLIED_TAB_LABEL,
            MarketMyTasksUiPolicy.tabLabel(MarketMyTasksTab.APPLIED))
    }

    @Test
    fun appliedCardsMatchVisibleReferenceCopyAndOrder() {
        val items = MarketMyTasksUiPolicy.appliedTasks
        assertEquals(
            listOf(
                "Poster for Optometry Society health camp",
                "Help fixing a Python lab program",
                "Handwritten notes for Ocular Anatomy",
                "Typing a 10 page report"
            ),
            items.map { it.title }
        )
        assertEquals(listOf("Accepted", "Shortlisted", "Sent", "Declined"), items.map { it.status })
        assertEquals("Your price Rs. 1,200 · Chat is open", items.first().detailLine)
        assertEquals("Your price Free · Waiting for poster", items[1].detailLine)
        assertEquals("Your price Rs. 300 · 2 days left", items[2].detailLine)
        assertEquals("Your price Rs. 400 · Task closed", items[3].detailLine)
    }

    @Test
    fun acceptedAppliedItemOpensOnlyTheChatPreviewAndKeepsContext() {
        val item = MarketMyTasksUiPolicy.appliedTasks.first()
        assertEquals(MarketMyTaskAction.OPEN_CHAT_PREVIEW, item.action)
        assertEquals("Zain Malik", item.helperName)
        assertEquals(1200, item.pricePkr)
        assertTrue(item.detailLine.contains("Chat is open"))
    }

    @Test
    fun postedHealthCampItemUsesExistingOwnerDetailAndPostedListIsIndependent() {
        val item = MarketMyTasksUiPolicy.postedTasks.first()
        assertEquals(MarketMyTaskAction.OPEN_POSTED_DETAIL, item.action)
        assertEquals("Poster for Optometry Society health camp", item.title)
        assertEquals("Your budget Rs. 1,500 · 4 proposals", item.detailLine)
        assertFalse(MarketMyTasksUiPolicy.items(MarketMyTasksTab.POSTED) ===
            MarketMyTasksUiPolicy.items(MarketMyTasksTab.APPLIED))
    }
}
